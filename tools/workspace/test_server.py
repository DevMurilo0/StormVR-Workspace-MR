import importlib.util
import json
from pathlib import Path
import threading
import unittest
import urllib.error
import urllib.request
from http.server import ThreadingHTTPServer

spec=importlib.util.spec_from_file_location('workspace_server',Path(__file__).with_name('server.py'))
server=importlib.util.module_from_spec(spec);spec.loader.exec_module(server)

class BridgeTests(unittest.TestCase):
    def setUp(self):
        self.frame=server.Frame(77,{'source_type':4,'size':(1280,720)})
        self.frame.publish(b'\xff\xd8test-frame\xff\xd9')
        second=server.Frame(88,{'source_type':1});second.publish(b'\xff\xd8different-monitor\xff\xd9')
        capture=type('Capture',(),{'frames':{'77':self.frame,'88':second}})()
        self.http=ThreadingHTTPServer(('127.0.0.1',0),server.handler_for(capture,'a'*32))
        self.thread=threading.Thread(target=self.http.serve_forever,daemon=True);self.thread.start()
        self.url='http://127.0.0.1:'+str(self.http.server_port)
    def tearDown(self):
        self.http.shutdown();self.http.server_close();self.thread.join()
    def get(self,path,token='a'*32):
        return urllib.request.urlopen(urllib.request.Request(self.url+path,headers={'Authorization':'Bearer '+token}),timeout=2)
    def test_authentication(self):
        for token in ('','wrong'):
            with self.assertRaises(urllib.error.HTTPError) as error:self.get('/sources',token)
            self.assertEqual(error.exception.code,401);error.exception.close()
    def test_independent_source_and_metadata(self):
        with self.get('/sources') as response:
            data=json.load(response)
            self.assertEqual(data['sources'][0]['id'],'77')
            self.assertTrue(data['sources'][0]['connected'])
            self.assertEqual(data['sources'][0]['properties']['source_type'],'4')
    def test_frame_and_unknown_source(self):
        with self.get('/frame/77') as response:
            self.assertEqual(response.read(),self.frame.jpeg)
            self.assertEqual(response.headers['Cache-Control'],'no-store')
        with self.assertRaises(urllib.error.HTTPError) as error:self.get('/frame/78')
        self.assertEqual(error.exception.code,404);error.exception.close()
    def test_two_monitor_frames_are_distinct(self):
        with self.get("/frame/77") as a,self.get("/frame/88") as b:
            self.assertNotEqual(a.read(),b.read())
    def test_stale_capture_does_not_pretend_live(self):
        self.frame.updated=0
        with self.assertRaises(urllib.error.HTTPError) as error:self.get('/frame/77')
        self.assertEqual(error.exception.code,503);error.exception.close()
    def test_no_credentials_in_page(self):
        with self.get('/view/77','') as response:
            body=response.read()
            self.assertNotIn(b'a'*32,body)
            self.assertEqual(response.headers['Referrer-Policy'],'no-referrer')
    def test_tls_with_verified_certificate(self):
        import tempfile,subprocess,ssl
        with tempfile.TemporaryDirectory() as directory:
            cert=Path(directory)/'cert.pem';key=Path(directory)/'key.pem'
            subprocess.run(['openssl','req','-x509','-newkey','rsa:2048','-nodes','-days','1',
                '-subj','/CN=localhost','-addext','subjectAltName=IP:127.0.0.1',
                '-keyout',str(key),'-out',str(cert)],check=True,stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
            capture=type('Capture',(),{'frames':{'77':self.frame}})()
            secure=ThreadingHTTPServer(('127.0.0.1',0),server.handler_for(capture,'a'*32))
            tls=ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER);tls.load_cert_chain(cert,key)
            secure.socket=tls.wrap_socket(secure.socket,server_side=True)
            thread=threading.Thread(target=secure.serve_forever,daemon=True);thread.start()
            try:
                request=urllib.request.Request('https://127.0.0.1:'+str(secure.server_port)+'/sources',headers={'Authorization':'Bearer '+'a'*32})
                with urllib.request.urlopen(request,context=ssl.create_default_context(cafile=str(cert)),timeout=3) as response:
                    self.assertEqual(response.status,200)
            finally:secure.shutdown();secure.server_close();thread.join()
    def test_gstreamer_real_jpeg_pipeline(self):
        import gi
        gi.require_version('Gst','1.0')
        from gi.repository import Gst
        Gst.init(None)
        p=Gst.parse_launch('videotestsrc num-buffers=1 ! video/x-raw,width=1280,height=720,framerate=10/1 ! videoconvert ! jpegenc quality=65 ! appsink name=out sync=false')
        p.set_state(Gst.State.PLAYING)
        try:
            sample=p.get_by_name('out').emit('try-pull-sample',5*Gst.SECOND)
            self.assertIsNotNone(sample)
            buffer=sample.get_buffer();ok,mapping=buffer.map(Gst.MapFlags.READ)
            self.assertTrue(ok)
            try:self.assertEqual(bytes(mapping.data[:2]),b'\xff\xd8')
            finally:buffer.unmap(mapping)
        finally:p.set_state(Gst.State.NULL)

if __name__=='__main__':unittest.main()
