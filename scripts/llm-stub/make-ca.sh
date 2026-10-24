#!/usr/bin/env bash
# Disposable CA for the private emulator only; never use a real CA or provider key.
set -euo pipefail
umask 077
repo="$(cd "$(dirname "$0")/../.." && pwd)"
certs="${1:-$repo/.local/llm-stub/certs}"
mkdir -p "$certs"
if [ -n "$(ls -A "$certs")" ]; then
    printf 'Refusing to replace existing certificates in %s; choose a new empty directory.\n' "$certs" >&2
    exit 1
fi
openssl req -x509 -newkey rsa:2048 -nodes -sha256 -days 30 \
    -subj '/CN=AndBible disposable emulator CA' \
    -addext 'basicConstraints=critical,CA:TRUE' \
    -addext 'keyUsage=critical,keyCertSign,cRLSign' \
    -keyout "$certs/ca.key" -out "$certs/ca.crt"
openssl req -new -newkey rsa:2048 -nodes -sha256 \
    -subj '/CN=10.0.2.2' -keyout "$certs/server.key" -out "$certs/server.csr"
printf '%s\n' 'basicConstraints=critical,CA:FALSE' 'keyUsage=critical,digitalSignature,keyEncipherment' \
    'extendedKeyUsage=serverAuth' 'subjectAltName=IP:10.0.2.2' > "$certs/server.ext"
openssl x509 -req -sha256 -days 30 -in "$certs/server.csr" \
    -CA "$certs/ca.crt" -CAkey "$certs/ca.key" -CAcreateserial \
    -extfile "$certs/server.ext" -out "$certs/server.crt"
openssl verify -CAfile "$certs/ca.crt" "$certs/server.crt"
printf 'Created disposable certificates in %s\n' "$certs"
