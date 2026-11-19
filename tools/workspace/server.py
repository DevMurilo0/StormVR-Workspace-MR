#!/usr/bin/env python3
"""StormVR LAN preview bridge. Wayland ScreenCast consent -> PipeWire -> JPEG.
No input injection; PC mouse/keyboard remain under compositor control.
SPDX-License-Identifier: GPL-3.0-or-later
"""
import argparse
import hmac
import ipaddress
import json
import os
from pathlib import Path
import secrets
import ssl
import subprocess
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import urlsplit


class Frame:
    def __init__(self, node, properties):
        self.node = int(node)
        self.properties = {str(k): str(v) for k, v in properties.items()}
        self.jpeg = b''
        self.sequence = 0
        self.updated = 0.0
        self.lock = threading.Condition()
        self.pipeline = None
        self.fd = None
        self.error = ''

    def publish(self, data):
        with self.lock:
            self.jpeg = data
            self.sequence += 1
            self.updated = time.monotonic()
            self.lock.notify_all()

    def info(self):
        with self.lock:
            return {'id': str(self.node), 'properties': self.properties,
                    'transport': 'https-jpeg', 'fps_limit': getattr(self, 'fps', 10),
                    'connected': bool(self.jpeg) and time.monotonic() - self.updated < 3,
                    'sequence': self.sequence, 'error': self.error}


def discover_monitors():
    """Read compositor metadata without granting capture or guessing virtual outputs."""
    try:
        result = subprocess.run(['kscreen-doctor', '-j'], capture_output=True, text=True, timeout=3, check=True)
        outputs = json.loads(result.stdout).get('outputs', [])
        monitors = []
        for output in outputs:
            mode = next((m for m in output.get('modes', []) if m['id'] == output.get('currentModeId')), {})
            name = output.get('name', '')
            monitors.append({'id': output.get('id'), 'name': name,
                'connected': output.get('connected', False), 'enabled': output.get('enabled', False),
                'resolution': mode.get('size'), 'refresh_rate': mode.get('refreshRate'),
                'kind': 'virtual' if name.lower().startswith(('virtual', 'headless')) else 'physical-likely' if name.startswith(('LVDS','eDP','HDMI','DP-','DVI','VGA')) else 'unknown',
                'classification': 'connector name heuristic; portal source_type=4 identifies a newly requested virtual output, not all existing virtual outputs'})
        return monitors
    except (OSError, subprocess.SubprocessError, ValueError, KeyError):
        return []


class PortalCapture:
    def __init__(self, fps=10):
        import dbus
        import dbus.mainloop.glib
        import gi
        gi.require_version('Gst', '1.0')
        from gi.repository import GLib, Gst
        dbus.mainloop.glib.DBusGMainLoop(set_as_default=True)
        self.dbus, self.GLib, self.Gst = dbus, GLib, Gst
        Gst.init(None)
        self.bus = dbus.SessionBus()
        self.portal = self.bus.get_object('org.freedesktop.portal.Desktop', '/org/freedesktop/portal/desktop')
        self.interface = dbus.Interface(self.portal, 'org.freedesktop.portal.ScreenCast')
        self.frames = {}
        self.session = None
        self.fps = fps
        self.receivers = []

    def request(self, method, args, done):
        token = 'stormvr_' + secrets.token_hex(8)
        sender = self.bus.get_unique_name()[1:].replace('.', '_')
        path = '/org/freedesktop/portal/desktop/request/' + sender + '/' + token
        def response(code, result):
            if int(code) != 0:
                print('Portal recusou/cancelou a captura:', code, flush=True)
                return
            done(result)
        self.receivers.append(self.bus.add_signal_receiver(response, signal_name='Response',
            dbus_interface='org.freedesktop.portal.Request', path=path))
        options = self.dbus.Dictionary({'handle_token': token}, signature='sv')
        options.update(args[-1])
        method(*args[:-1], options, dbus_interface='org.freedesktop.portal.ScreenCast')

    def start(self):
        self.request(self.interface.CreateSession, ({'session_handle_token': 'stormvr_' + secrets.token_hex(8)},), self.created)

    def created(self, result):
        self.session = result['session_handle']
        props = self.dbus.Interface(self.portal, 'org.freedesktop.DBus.Properties')
        available = int(props.Get('org.freedesktop.portal.ScreenCast', 'AvailableSourceTypes'))
        # Ask explicitly: virtual outputs only if the compositor advertises support.
        types = 1 | (available & 4)
        self.request(self.interface.SelectSources, (self.session, {'types': self.dbus.UInt32(types),
            'multiple': self.dbus.Boolean(True), 'cursor_mode': self.dbus.UInt32(2)}), self.selected)

    def selected(self, _):
        self.request(self.interface.Start, (self.session, '', {}), self.started)

    def started(self, result):
        for node, properties in result['streams']:
            frame = Frame(node, properties)
            frame.fps = self.fps
            # Open a remote for each pipeline; never assume global PipeWire node access.
            frame.fd = self.interface.OpenPipeWireRemote(self.session, {}).take()
            self.frames[str(node)] = frame
            pipeline = self.Gst.parse_launch(
                f'pipewiresrc fd={frame.fd} target-object={int(properties.get("pipewire-serial", node))} do-timestamp=true ! '
                'queue max-size-buffers=1 leaky=downstream ! videoconvert ! videoscale ! videorate ! '
                f'video/x-raw,width=1280,height=720,framerate={self.fps}/1 ! '
                'jpegenc quality=65 ! appsink name=out emit-signals=true max-buffers=1 drop=true sync=false')
            frame.pipeline = pipeline
            def sample(sink, target=frame):
                sample0 = sink.emit('pull-sample')
                if sample0 is None:
                    return self.Gst.FlowReturn.ERROR
                buffer = sample0.get_buffer()
                ok, mapping = buffer.map(self.Gst.MapFlags.READ)
                if ok:
                    target.publish(bytes(mapping.data))
                    buffer.unmap(mapping)
                return self.Gst.FlowReturn.OK
            pipeline.get_by_name('out').connect('new-sample', sample)
            bus = pipeline.get_bus(); bus.add_signal_watch()
            def message(_, msg, target=frame):
                if msg.type == self.Gst.MessageType.ERROR:
                    err, _ = msg.parse_error(); target.error = str(err)
            bus.connect('message', message)
            pipeline.set_state(self.Gst.State.PLAYING)
        print(f'{len(self.frames)} fonte(s) autorizada(s). Use /sources com o token.', flush=True)

    def close(self):
        for frame in self.frames.values():
            if frame.pipeline:
                frame.pipeline.set_state(self.Gst.State.NULL)
            if frame.fd is not None:
                os.close(frame.fd)
        if self.session:
            try:
                self.bus.get_object('org.freedesktop.portal.Desktop', self.session).Close(dbus_interface='org.freedesktop.portal.Session')
            except Exception:
                pass


PAGE = b'''<!doctype html><meta name="viewport" content="width=device-width"><style>
body{margin:0;background:#0b161e;color:#e7f5fa;font:18px sans-serif}img{width:100%;height:94vh;object-fit:contain}#s{height:6vh}</style>
<div id=s>Conectando ao monitor autorizado...</div><img id=v><script>
const token=decodeURIComponent(location.hash.slice(1)),id=location.pathname.split('/').pop();
let last=null; async function frame(){if(window.stormvrPaused){setTimeout(frame,1000);return;}try{const r=await fetch('/frame/'+id,{headers:{Authorization:'Bearer '+token},cache:'no-store'});
if(!r.ok)throw Error('HTTP '+r.status);const blob=await r.blob(),url=URL.createObjectURL(blob);
v.src=url;if(last)URL.revokeObjectURL(last);last=url;s.textContent='Monitor '+id+' | fonte Linux independente';}
catch(e){s.textContent='Sem imagem: '+e.message+' | tentando reconectar';}setTimeout(frame,100);}frame();</script>'''


SOURCES_PAGE = b'''<!doctype html><meta name="viewport" content="width=device-width"><style>
body{background:#0b161e;color:#e7f5fa;font:24px sans-serif;padding:24px}a{display:block;color:#a0e9f5;padding:20px}</style>
<h1>StormVR Workspace MR</h1><div id=s>Buscando fontes autorizadas...</div><script>
const t=location.hash.slice(1);fetch('/sources',{headers:{Authorization:'Bearer '+decodeURIComponent(t)}})
.then(r=>{if(!r.ok)throw Error('HTTP '+r.status);return r.json()}).then(d=>{
s.textContent=d.sources.length?'Escolha um monitor independente:':'Autorize as fontes no KDE e recarregue.';
for(const src of d.sources){const a=document.createElement('a');a.textContent='Monitor '+src.id+' | '+JSON.stringify(src.properties);
a.href='/view/'+src.id+'#'+t;document.body.appendChild(a);}}).catch(e=>s.textContent=e.message);</script>'''


def handler_for(capture, token):
    class Handler(BaseHTTPRequestHandler):
        def log_message(self, *_):
            pass  # Never log credentials or source URLs.

        def reply(self, code, body, content_type='application/json'):
            self.send_response(code)
            self.send_header('Content-Type', content_type)
            self.send_header('Content-Length', str(len(body)))
            self.send_header('Cache-Control', 'no-store')
            self.send_header('X-Content-Type-Options', 'nosniff')
            self.send_header('Referrer-Policy', 'no-referrer')
            self.send_header('Content-Security-Policy', "default-src 'none'; script-src 'unsafe-inline'; style-src 'unsafe-inline'; connect-src 'self'; img-src blob:")
            self.end_headers()
            self.wfile.write(body)

        def do_GET(self):
            path = urlsplit(self.path).path
            if path == '/':
                self.reply(200, SOURCES_PAGE, 'text/html; charset=utf-8')
                return
            if path.startswith('/view/'):
                self.reply(200, PAGE, 'text/html; charset=utf-8')
                return
            expected = 'Bearer ' + token
            if not hmac.compare_digest(self.headers.get('Authorization', ''), expected):
                self.reply(401, b'{"error":"unauthorized"}')
                return
            if path == '/monitors':
                self.reply(200, json.dumps({'monitors': discover_monitors()}).encode())
            elif path == '/sources':
                self.reply(200, json.dumps({'sources': [f.info() for f in list(capture.frames.values())],
                    'input': 'PC keyboard/mouse only; no remote injection', 'capture': 'xdg-desktop-portal consent'}).encode())
            elif path.startswith('/frame/'):
                frame = capture.frames.get(path.split('/')[-1])
                if frame is None:
                    self.reply(404, b'{"error":"source not selected"}')
                    return
                with frame.lock:
                    data = frame.jpeg
                    fresh = time.monotonic() - frame.updated < 3
                if data and fresh:
                    self.reply(200, data, 'image/jpeg')
                else:
                    self.reply(503, b'{"error":"capture unavailable"}')
            else:
                self.reply(404, b'{}')
    return Handler


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--bind', required=True, help='Explicit private LAN IPv4 address; never 0.0.0.0')
    parser.add_argument('--port', type=int, default=8443)
    parser.add_argument('--cert', required=True)
    parser.add_argument('--key', required=True)
    parser.add_argument('--token-file', required=True)
    parser.add_argument('--fps', type=int, choices=range(1, 16), default=10)
    args = parser.parse_args()
    address = ipaddress.ip_address(args.bind)
    if address.version != 4 or not (address.is_private or address.is_loopback) or address.is_unspecified:
        parser.error('Use an explicit private/loopback IPv4 address')
    token_path = Path(args.token_file)
    if token_path.exists():
        if token_path.stat().st_mode & 0o077:
            parser.error('Token file must have mode 0600')
        token = token_path.read_text().strip()
    else:
        token = secrets.token_urlsafe(32)
        fd = os.open(token_path, os.O_CREAT | os.O_EXCL | os.O_WRONLY, 0o600)
        with os.fdopen(fd, 'w') as stream:
            stream.write(token)
    if len(token) < 32:
        parser.error('Token must contain at least 32 characters')
    capture = PortalCapture(args.fps)
    server = ThreadingHTTPServer((args.bind, args.port), handler_for(capture, token))
    server.daemon_threads = True
    tls = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
    tls.minimum_version = ssl.TLSVersion.TLSv1_2
    tls.load_cert_chain(args.cert, args.key)
    server.socket = tls.wrap_socket(server.socket, server_side=True)
    thread = threading.Thread(target=server.serve_forever, daemon=True)
    thread.start()
    loop = capture.GLib.MainLoop()
    try:
        capture.start()
        print(f'StormVR HTTPS em {args.bind}:{args.port}. Autorize as fontes na janela do KDE.', flush=True)
        loop.run()
    except KeyboardInterrupt:
        pass
    finally:
        server.shutdown(); server.server_close(); capture.close()


if __name__ == '__main__':
    main()
