#!/usr/bin/env bash
# Captures the App Store raws of one language on the remote Mac's simulator (ios-mac skill rules apply: one Mac
# session at a time, scripts/mac-remote.sh takes the shared lock; this script ends with `mac-remote.sh cleanup`).
#   scripts/store-shots/ios-capture.sh --device iphone|ipad --lang <app code, e.g. en, fi, zh-rCN> [--keep]
# iphone = iPhone 16 Pro Max (6.9", 1320x2868; the 6.5" set is framed from the same raws), ipad = iPad Pro 13-inch (M4).
# Steps: demo backup + modules (prepare-lang.sh) -> tmp/store-shots/ (synced to the Mac) -> one batch under one lock:
# build+install the app, seed it (ios-seed.sh), run StoreScreenshotsUITests for the light shots, reseed in dark
# appearance and run it for the dark shots -> raws from the attachments -> `storeshots record`.
# --keep: do not run `mac-remote.sh cleanup` (when another Mac step follows at once).
set -euo pipefail
device="" lang="" keep=0
while [ $# -gt 0 ]; do case "$1" in
    --device) device="$2"; shift 2 ;;
    --lang) lang="$2"; shift 2 ;;
    --keep) keep=1; shift ;;
    *) echo "unknown arg $1" >&2; exit 2 ;;
esac; done
case "$device" in
    iphone) target=ios-iphone-69; also=(--also ios-iphone-65); export MAC_SIM_IPHONE="${MAC_SIM_IPHONE:-iPhone 16 Pro Max}" ;;
    ipad) target=ios-ipad-13; also=(); export MAC_SIM_IPAD="${MAC_SIM_IPAD:-iPad Pro 13-inch (M4)}" ;;
    *) echo "--device iphone|ipad" >&2; exit 2 ;;
esac
[ -n "$lang" ] || { echo "--lang required" >&2; exit 2; }
here="$(cd "$(dirname "$0")" && pwd)"
repo="$(git -C "$here" rev-parse --show-toplevel)"
work="${STORE_SHOTS_WORK:-$repo/.local/store-shots}"
zip=$("$here/prepare-lang.sh" "$lang" | tail -1)

stage="$repo/tmp/store-shots"
rm -rf "$stage"; mkdir -p "$stage/db" "$stage/modules"
unzip -q -o -j "$zip" 'db/*.sqlite3' -d "$stage/db"
python3 -I - "$repo/store-screenshots/demo-content/modules.yaml" "$lang" "$work/modules" "$stage/modules" <<'PY'
import shutil, sys, re
path, lang, cache, out = sys.argv[1:]
# minimal YAML read of "<lang>:\n  - {initials: X, repo: Y}" (the full parser lives in storeshots; no deps here)
cur = None
for line in open(path):
    m = re.match(r'^([A-Za-z0-9+_-]+):\s*$', line)
    if m: cur = m.group(1); continue
    m = re.search(r'initials:\s*([^,}\s]+)', line)
    if m and cur == lang: shutil.copy(f"{cache}/{m.group(1)}.zip", out)
PY
"$here/run.sh" plan --lang "$lang" --target "$target" --mode light --out "$stage/plan-light.json"
"$here/run.sh" plan --lang "$lang" --target "$target" --mode dark --out "$stage/plan-dark.json"
test_id="-only-testing:iosAppUITests/StoreScreenshotsUITests"
steps=("xcode-build $device" "run bash scripts/store-shots/ios-seed.sh light" "xcode-test $device $test_id")
if grep -q '"id"' "$stage/plan-dark.json"; then
    steps+=("run bash scripts/store-shots/ios-seed.sh dark" "xcode-test $device $test_id")
fi
log="$work/$lang/ios-$device.log"
mkdir -p "$work/$lang"
set +e
MAC_KEEP_SIM=0 "$repo/scripts/mac-remote.sh" batch --keep-going "${steps[@]}" 2>&1 | tee "$log"
rc=${PIPESTATUS[0]}
set -e
run_id=$(sed -n 's/^mac-remote: run \([^ ]*\) .*/\1/p' "$log" | head -1)
[ "$keep" = 1 ] || "$repo/scripts/mac-remote.sh" cleanup >/dev/null 2>&1 || true
rm -rf "$stage"
[ -n "$run_id" ] || { echo "ios-capture: no mac-remote run id in $log" >&2; exit 1; }
container=$(python3 -c 'import json;print(json.load(open("/etc/jailbee/container.json"))["name"])' 2>/dev/null || hostname)
evidence="$repo/.local/mac/$container/runs/$run_id"
raws="$work/$lang/ios-$device-attachments"
rm -rf "$raws"; mkdir -p "$raws"
find "$evidence" -name 'store-*.png' ! -name 'store-FAILED-*' | while read -r f; do
    b=$(basename "$f" .png); cp "$f" "$raws/${b#store-}.png"
done
find "$evidence" -name 'store-FAILED-*' -print | sed 's/^/ios-capture: FAILED shot evidence: /' >&2
"$here/run.sh" record --lang "$lang" --target "$target" --dir "$raws" "${also[@]}"
exit "$rc"
