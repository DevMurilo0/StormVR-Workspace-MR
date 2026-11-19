#!/usr/bin/env bash
set -euo pipefail
address=${1:?Uso: setup-tls.sh IP_PRIVADO_DO_PC}
python - "$address" <<'PY'
import ipaddress,sys
ip=ipaddress.ip_address(sys.argv[1]);assert ip.version==4 and ip.is_private and not ip.is_unspecified
PY
config_dir="${XDG_CONFIG_HOME:-$HOME/.config}/stormvr-workspace"
mkdir -p "$config_dir"
chmod 700 "$config_dir"
if [[ -e "$config_dir/key.pem" || -e "$config_dir/cert.pem" ]]; then
    printf 'Certificado existente preservado em %s\n' "$config_dir"
    exit 0
fi
umask 077
openssl req -x509 -newkey rsa:3072 -nodes -days 365 -sha256 \
    -subj '/CN=StormVR Workspace LAN' -addext "subjectAltName=IP:$address" \
    -addext 'basicConstraints=critical,CA:TRUE' -addext 'keyUsage=critical,digitalSignature,keyEncipherment,keyCertSign' \
    -keyout "$config_dir/key.pem" -out "$config_dir/cert.pem"
openssl x509 -in "$config_dir/cert.pem" -outform DER -out "$config_dir/stormvr-lan.crt"
openssl x509 -in "$config_dir/cert.pem" -noout -fingerprint -sha256
printf 'Instale somente stormvr-lan.crt como CA do usuário no Android. Nunca copie key.pem.\n'
