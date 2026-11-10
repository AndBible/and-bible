#!/usr/bin/env bash
# Maintainer-only, interactive: pack the release secrets into ONE GPG bundle (format 1), so a release run needs a single
# YubiKey touch. Spec docs/superpowers/specs/2026-11-10-release-internal-one-touch-design.md §5.1 and §9.4.
#
# Usage: scripts/release/secrets-pack.sh [--out <path>] [--recipient <keyid>]
#   --out        default .local/release/andbible-release-secrets.tar.gpg (refuses to overwrite)
#   --recipient  default: the key keystore.properties.gpg is encrypted to (read with gpg --list-only, no decrypt)
# Inputs: keystore.properties.gpg (repo root); from the release-secrets mount: asc-api.env, AuthKey_<ASC_KEY_ID>.p8.gpg,
# exactly one *.json.gpg (Play service account) and release_keys.p12.gpg. Four decrypts, each announced and run alone
# (back-to-back decrypts miss the touch window), each retried once. Only the `andbible` alias is exported into the
# bundle's andbible.p12 (key password = store password); the certificate must match the Play app signing certificate.
# Plaintext only inside the lib-secrets RAM dir; passwords reach keytool/jarsigner through :file options only.
# Env: REPO_ROOT, RELEASE_SECRETS_SRC (~/.release-secrets), PACK_PAUSE (seconds between decrypts, 4),
#      PACK_EXPECT_CERT_PREFIX / PACK_EXPECT_CERT_SUFFIX (Play app signing cert SHA-256 ends; tests override),
#      RELEASE_RAMDIR_MOUNT=none (tests only).
set -uo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO="${REPO_ROOT:-$(cd "$HERE/../.." && pwd)}"
SRC="${RELEASE_SECRETS_SRC:-$HOME/.release-secrets}"
PAUSE="${PACK_PAUSE:-4}"
EXPECT_PREFIX="${PACK_EXPECT_CERT_PREFIX-8B:F5:16:8A}"
EXPECT_SUFFIX="${PACK_EXPECT_CERT_SUFFIX-1C:48:CF:30}"
OUT=""
RECIP=""
while [ $# -gt 0 ]; do
    case "$1" in
    --out) OUT="${2:?}"; shift 2 ;;
    --recipient) RECIP="${2:?}"; shift 2 ;;
    *) echo "usage: secrets-pack.sh [--out <path>] [--recipient <keyid>]" >&2; exit 2 ;;
    esac
done
OUT="${OUT:-$REPO/.local/release/andbible-release-secrets.tar.gpg}"
die() { echo "pack: $*" >&2; exit 1; }

# shellcheck source=lib-secrets.sh
. "$HERE/lib-secrets.sh"

# Inputs (no decrypt yet).
[ ! -e "$OUT" ] || die "$OUT already exists"
PROPS_GPG="$REPO/keystore.properties.gpg"
[ -f "$PROPS_GPG" ] || die "missing $PROPS_GPG"
[ -f "$SRC/asc-api.env" ] || die "missing $SRC/asc-api.env (is the release-secrets mount attached?)"
ASC_KEY_ID=$(sed -n 's/^ASC_KEY_ID=//p' "$SRC/asc-api.env" | head -n 1)
[ -n "$ASC_KEY_ID" ] || die "asc-api.env lacks ASC_KEY_ID"
P8_GPG="$SRC/AuthKey_${ASC_KEY_ID}.p8.gpg"
[ -f "$P8_GPG" ] || die "missing $P8_GPG"
P12_GPG="$SRC/release_keys.p12.gpg"
[ -f "$P12_GPG" ] || die "missing $P12_GPG"
shopt -s nullglob
json=("$SRC"/*.json.gpg)
shopt -u nullglob
[ ${#json[@]} -eq 1 ] || die "need exactly one *.json.gpg (Play service account) in $SRC, found ${#json[@]}"

# keyid_of <file.gpg>: the public-key-encrypted packet's key id, without decrypting (no touch).
keyid_of() {
    local pk
    pk=$(gpg --batch --list-only --list-packets "$1" 2>&1)
    printf '%s\n' "$pk" | sed -n 's/.*pubkey enc packet:.* keyid \([0-9A-Fa-f]\{16\}\).*/\1/p' | head -n 1
}
if [ -z "$RECIP" ]; then
    RECIP=$(keyid_of "$PROPS_GPG")
    [ -n "$RECIP" ] || die "cannot read the recipient of keystore.properties.gpg; pass --recipient"
fi
RECIP=${RECIP%!}
echo "pack: recipient $RECIP" >&2

# Single-instance with release runs: the stale-dir sweep of a release run must never meet this script's RAM dir.
exec 9> "${RELEASE_LOCK_FILE:-/tmp/andbible-release-$(id -u).lock}"
flock -n 9 || die "a release run is active in this container"

trap 'secrets_close' EXIT
trap 'exit 130' INT
trap 'exit 143' TERM HUP
secrets_open || die "no RAM dir"
D=$SECRETS_DIR
umask 077

N=0
dec() {   # dec <src.gpg> <name>: announced, alone, one retry after a missed touch
    local i
    N=$((N + 1))
    [ "$N" = 1 ] || sleep "$PAUSE"
    for i in 1 2; do
        echo "pack: touch the YubiKey now for $(basename "$1") ($N/4, attempt $i)" >&2
        if secrets_decrypt "$1" "$2" 2>/dev/null && [ -s "$D/$2" ]; then return 0; fi
        echo "pack: decrypt of $(basename "$1") failed" >&2
        [ "$i" = 2 ] || sleep $((PAUSE * 2))
    done
    die "could not decrypt $(basename "$1")"
}
dec "$PROPS_GPG" orig.properties
dec "$P12_GPG" src.p12
dec "${json[0]}" play-service-account.json
dec "$P8_GPG" AuthKey.p8

# Password files from the original properties (simple key=value; escapes would be misread, so they are refused).
python3 -I - "$D" <<'EOF' || exit 1
import os, sys
d = sys.argv[1]
props = {}
for line in open(os.path.join(d, "orig.properties"), encoding="utf-8"):
    line = line.rstrip("\r\n")
    if not line.strip() or line.lstrip().startswith(("#", "!")) or "=" not in line:
        continue
    k, v = line.split("=", 1)
    props[k.strip()] = v.strip()
for k in ("storePassword", "keyPassword", "keyAlias"):
    if not props.get(k):
        sys.exit("pack: keystore.properties lacks " + k)
    if "\\" in props[k]:
        sys.exit("pack: backslash in " + k + "; escape handling not implemented")
for name, k in (("sp", "storePassword"), ("kp", "keyPassword"), ("alias", "keyAlias")):
    fd = os.open(os.path.join(d, name), os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    os.write(fd, props[k].encode())
    os.close(fd)
EOF
ALIAS=$(cat "$D/alias")
[ "$ALIAS" = andbible ] || echo "pack: note: the source alias is '$ALIAS'; in the bundle it is 'andbible'" >&2

keytool -importkeystore -noprompt \
    -srckeystore "$D/src.p12" -srcstoretype PKCS12 -srcstorepass:file "$D/sp" -srcalias "$ALIAS" -srckeypass:file "$D/kp" \
    -destkeystore "$D/andbible.p12" -deststoretype PKCS12 -deststorepass:file "$D/sp" -destkeypass:file "$D/sp" \
    -destalias andbible >/dev/null 2>"$D/kt.err" || { grep -v -i pass "$D/kt.err" >&2; die "keytool import failed"; }
shred -u "$D/src.p12" "$D/orig.properties" "$D/kp" "$D/alias" "$D/kt.err"

list=$(keytool -list -keystore "$D/andbible.p12" -storetype PKCS12 -storepass:file "$D/sp" 2>/dev/null)
n=$(printf '%s\n' "$list" | grep -c PrivateKeyEntry)
[ "$n" = 1 ] || die "expected exactly 1 private key entry, got $n"
fp=$(keytool -list -v -keystore "$D/andbible.p12" -storetype PKCS12 -storepass:file "$D/sp" -alias andbible 2>/dev/null \
    | awk '/SHA256:/{print $2; exit}')
case "$fp" in
"$EXPECT_PREFIX"*"$EXPECT_SUFFIX") echo "pack: certificate SHA-256 matches Play app signing (${fp:0:11}...${fp: -11})" >&2 ;;
*) die "certificate SHA-256 ${fp:0:11}...${fp: -11} does not match the Play app signing certificate" ;;
esac

# Test signature of a throwaway jar inside the RAM dir.
mkdir "$D/j" && echo x > "$D/j/a.txt" && (cd "$D/j" && jar cf ../t.jar a.txt) || die "jar"
jarsigner -keystore "$D/andbible.p12" -storetype PKCS12 -storepass:file "$D/sp" -keypass:file "$D/sp" "$D/t.jar" andbible \
    >/dev/null 2>&1 || die "test signing failed"
ver=$(jarsigner -verify "$D/t.jar" 2>/dev/null)
case "$ver" in *"jar verified"*) ;; *) die "the test signature does not verify" ;; esac
rm -rf "$D/j" "$D/t.jar"
echo "pack: test signature OK" >&2

python3 -I - "$D" <<'EOF' || exit 1
import os, sys
d = sys.argv[1]
sp = open(os.path.join(d, "sp"), encoding="utf-8").read()
fd = os.open(os.path.join(d, "keystore.properties"), os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
os.write(fd, ("storeFile=andbible.p12\nstorePassword=%s\nkeyAlias=andbible\nkeyPassword=%s\n" % (sp, sp)).encode())
os.close(fd)
EOF
shred -u "$D/sp"

install -m 600 "$SRC/asc-api.env" "$D/asc-api.env"
grep -q '^ASC_TEAM_ID=' "$D/asc-api.env" || echo 'ASC_TEAM_ID=X6A6CDK8T7' >> "$D/asc-api.env"
for k in ASC_KEY_ID ASC_ISSUER_ID ASC_TEAM_ID; do grep -q "^$k=." "$D/asc-api.env" || die "asc-api.env lacks $k"; done
head -c 40 "$D/AuthKey.p8" | grep -q 'BEGIN PRIVATE KEY' || die "AuthKey.p8 is not a PEM private key"
python3 -I -c 'import json,sys; j=json.load(open(sys.argv[1])); sys.exit(0 if j.get("type")=="service_account" else 1)' \
    "$D/play-service-account.json" 2>/dev/null || die "the Play JSON is not a service account key"

{ echo format=1; for m in "${SECRETS_BUNDLE_MEMBERS[@]}"; do echo "member=$m"; done; } > "$D/MANIFEST"
have=$(ls -A "$D" | grep -vx .owner | sort | tr '\n' ' ')
want=$(printf '%s\n' MANIFEST "${SECRETS_BUNDLE_MEMBERS[@]}" | sort | tr '\n' ' ')
[ "$have" = "$want" ] || die "unexpected files in the RAM dir: $have"

mkdir -p "$(dirname "$OUT")"
tar -C "$D" --owner=0 --group=0 --numeric-owner -cf - MANIFEST "${SECRETS_BUNDLE_MEMBERS[@]}" \
    | gpg --batch --yes --trust-model always --encrypt --recipient "$RECIP!" -o "$OUT" \
    || { rm -f "$OUT"; die "encrypt failed"; }
chmod 600 "$OUT"
got=$(keyid_of "$OUT")
[ "${got^^}" = "${RECIP^^}" ] || { rm -f "$OUT"; die "recipient check failed (bundle encrypted to '$got')"; }
echo "pack: wrote $OUT ($(stat -c %s "$OUT") bytes), encrypted to $RECIP"
rel=${OUT#"$REPO"/}
cat <<EOM
pack: on the host, from the repository checkout:
    install -m 600 $rel ~/.config/andbible-release/andbible-release-secrets.tar.gpg
    rm $rel
pack: release runs read only the bundle now; the separate .gpg files in ~/.config/andbible-release/ are no longer used
      by the pipeline (keep release_keys.p12.gpg for the other apps if you like).
EOM
