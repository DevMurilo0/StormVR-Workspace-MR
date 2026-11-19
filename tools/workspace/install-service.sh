#!/usr/bin/env bash
set -euo pipefail
script_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
lib_dir="${XDG_DATA_HOME:-$HOME/.local/share}/stormvr-workspace"
bin_dir="$HOME/.local/bin"
python - <<'PY'
import dbus,gi
gi.require_version('Gst','1.0')
from gi.repository import Gst
Gst.init(None)
missing=[x for x in ('pipewiresrc','videoconvert','videoscale','videorate','jpegenc','appsink') if not Gst.ElementFactory.find(x)]
if missing:raise SystemExit('Plugins GStreamer ausentes: '+', '.join(missing))
PY
mkdir -p "$lib_dir" "$bin_dir"
install -m 755 "$script_dir/server.py" "$lib_dir/server.py"
cat > "$bin_dir/stormvr-workspace" <<'LAUNCHER'
#!/usr/bin/env bash
set -euo pipefail
exec python "${XDG_DATA_HOME:-$HOME/.local/share}/stormvr-workspace/server.py" "$@"
LAUNCHER
chmod 755 "$bin_dir/stormvr-workspace"
printf 'Instalado em %s. O servidor não foi iniciado. Configure TLS e execute com --bind, --cert, --key e --token-file.\n' "$bin_dir/stormvr-workspace"
