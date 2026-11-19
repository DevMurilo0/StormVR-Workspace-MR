#!/usr/bin/env bash
set -eu
printf 'Espaço disponível\n'
df -h / /home
printf '\nMonitores KDE\n'
kscreen-doctor -j
printf '\nVA-API no host (não representa o sandbox Flatpak)\n'
vainfo
printf '\nPipeline disponível\n'
python - <<'PY'
import gi,dbus
gi.require_version('Gst','1.0')
from gi.repository import Gst
Gst.init(None)
for name in ('pipewiresrc','videoconvert','videoscale','videorate','jpegenc','appsink'):
 print(name,bool(Gst.ElementFactory.find(name)))
b=dbus.SessionBus();p=dbus.Interface(b.get_object('org.freedesktop.portal.Desktop','/org/freedesktop/portal/desktop'),'org.freedesktop.DBus.Properties')
print('Tipos de fonte do portal:',p.Get('org.freedesktop.portal.ScreenCast','AvailableSourceTypes'))
PY
