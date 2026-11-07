#!/usr/bin/env bash
# Captures the Google Play raws of one language and one target on the container emulator.
#   scripts/store-shots/android-capture.sh --target play-phone|play-seven|play-ten --lang <app code> [--shots id,id]
#       [--apk <apk>] [--no-boot]
# AVDs: play-phone screenshot_phone (1440x3120), play-seven screenshot_tablet7 (1920x1200 landscape),
# play-ten tablet_api36 (2560x1600 landscape). One emulator at a time (the script kills a running one of another AVD);
# never while the Windows VM runs. Build the APK first: ./gradlew assembleStandardGithubDebug.
# Steps: demo backup + modules (prepare-lang.sh) -> boot -> install APK + modules -> per shot: reseed the databases,
# launch, workspace, action, screencap (storeshots/android_driver.py) -> `storeshots record`.
# Leaves the emulator running (several targets/languages in a row); `scripts/andbible-emu.sh kill` when done.
set -euo pipefail
target="" lang="" only="" apk="" boot=1
while [ $# -gt 0 ]; do case "$1" in
    --target) target="$2"; shift 2 ;;
    --lang) lang="$2"; shift 2 ;;
    --shots) only="$2"; shift 2 ;;
    --apk) apk="$2"; shift 2 ;;
    --no-boot) boot=0; shift ;;
    *) echo "unknown arg $1" >&2; exit 2 ;;
esac; done
case "$target" in
    play-phone) avd=screenshot_phone; landscape=() ;;
    play-seven) avd=screenshot_tablet7; landscape=(--landscape) ;;
    play-ten) avd=tablet_api36; landscape=(--landscape) ;;
    *) echo "--target play-phone|play-seven|play-ten" >&2; exit 2 ;;
esac
[ -n "$lang" ] || { echo "--lang required" >&2; exit 2; }
here="$(cd "$(dirname "$0")" && pwd)"
repo="$(git -C "$here" rev-parse --show-toplevel)"
work="${STORE_SHOTS_WORK:-$repo/.local/store-shots}"
apk="${apk:-$repo/app/build/outputs/apk/standardGithub/debug/app-standard-github-debug.apk}"
emu="$repo/scripts/andbible-emu.sh"
zip=$("$here/prepare-lang.sh" "$lang" | tail -1)

if [ "$boot" = 1 ]; then
    current=$("$emu" health 2>/dev/null | sed -n 's/.*AVD[: ]*\([A-Za-z0-9_]*\).*/\1/p' | head -1 || true)
    if [ "$current" != "$avd" ]; then
        "$emu" kill >/dev/null 2>&1 || true
        "$repo/scripts/emulator-avds.sh" "$avd" >/dev/null
        "$emu" boot "$avd"
    fi
fi
"$emu" install "$apk" >/dev/null
python3 -I - "$repo/store-screenshots/demo-content/modules.yaml" "$lang" <<'PY' | while read -r m; do "$emu" push-module "$work/modules/$m.zip" >/dev/null </dev/null; done
import re, sys
cur = None
for line in open(sys.argv[1]):
    m = re.match(r'^([A-Za-z0-9+_-]+):\s*$', line)
    if m: cur = m.group(1); continue
    m = re.search(r'initials:\s*([^,}\s]+)', line)
    if m and cur == sys.argv[2]: print(m.group(1))
PY
plan="$work/$lang/plan-$target.json"
"$here/run.sh" plan --lang "$lang" --target "$target" --out "$plan" >/dev/null
if [ -n "$only" ]; then
    python3 -I -c 'import json,sys; p=json.load(open(sys.argv[1])); keep=set(sys.argv[2].split(",")); p["shots"]=[s for s in p["shots"] if s["id"] in keep]; json.dump(p, open(sys.argv[1],"w"))' "$plan" "$only"
fi
raws="$work/$lang/android-$target-raw"
rm -rf "$raws"; mkdir -p "$raws"
set +e
(cd "$here" && uv run --no-project --with pyyaml python -m storeshots.android_driver --plan "$plan" \
    --content "$repo/store-screenshots/demo-content/$lang/content.yaml" --zip "$zip" --out "$raws" "${landscape[@]}")
rc=$?
set -e
"$here/run.sh" record --lang "$lang" --target "$target" --dir "$raws"
exit "$rc"
