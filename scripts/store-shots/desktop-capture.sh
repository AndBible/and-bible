#!/usr/bin/env bash
# Captures the website's desktop raws (target web-desktop, 1600x1000 window client area) from the real Linux desktop
# app (CEF build) on an isolated Xvfb + openbox display, following the desktop-linux-live runbook: own process groups,
# scratch profile, bounded lifetime, cleanup at the end.
#   scripts/store-shots/desktop-capture.sh --lang en [--app <bin/andbible>] [--shots id,id]
# Build the app first: ./gradlew :desktopApp:createDistributable (add -Pandbible.kcefArchive=<local archive> offline).
# The demo databases and modules are placed in the scratch profile ($XDG_DATA_HOME/andbible/{databases,sword}), the
# same files the app's restore and module install write. Workspaces are switched with genuine clicks on the
# toolbar's Workspaces button and the sheet row; the row position is the workspace's index in content.yaml (the sheet
# lists workspaces in that order), measured for a 1600x1000 client area. Windows uses the same flow through the VM
# (windows-vm skill) and needs the orchestrator's VM token.
set -euo pipefail
lang="" app="" only=""
while [ $# -gt 0 ]; do case "$1" in
    --lang) lang="$2"; shift 2 ;;
    --app) app="$2"; shift 2 ;;
    --shots) only="$2"; shift 2 ;;
    *) echo "unknown arg $1" >&2; exit 2 ;;
esac; done
[ -n "$lang" ] || { echo "--lang required" >&2; exit 2; }
here="$(cd "$(dirname "$0")" && pwd)"
repo="$(git -C "$here" rev-parse --show-toplevel)"
work="${STORE_SHOTS_WORK:-$repo/.local/store-shots}"
app="${app:-$repo/desktopApp/build/compose/binaries/main/app/andbible/bin/andbible}"
[ -x "$app" ] || { echo "desktop app not built: $app" >&2; exit 1; }
zip=$("$here/prepare-lang.sh" "$lang" | tail -1)
target=web-desktop
plan="$work/$lang/plan-$target.json"
"$here/run.sh" plan --lang "$lang" --target "$target" --out "$plan" >/dev/null

run=$(mktemp -d /tmp/andbible-store-shots.XXXXXXXX)
procs() { python3 -I "$repo/scripts/desktop-live-processes.py" "$@"; }
cleanup() {
    for n in app openbox xvfb; do procs stop "$run" "$n" >/dev/null 2>&1 || true; done
    rm -rf "$run"
}
trap cleanup EXIT
mkdir -p "$run"/{home,data,config,cache} "$run/data/andbible/databases" "$run/data/andbible/sword"
unzip -q -o -j "$zip" 'db/*.sqlite3' -d "$run/data/andbible/databases"
mods=$("$here/run.sh" modules --lang "$lang" --out "$work/modules")
[ -n "$mods" ] || { echo "desktop-capture: no modules for $lang" >&2; exit 1; }
while read -r z; do unzip -q -o "$z" -d "$run/data/andbible/sword"; done <<<"$mods"

# Display wider/taller than the client area: openbox puts a 1 px border and a title bar around it.
procs start "$run" xvfb -- bash -c 'exec Xvfb -displayfd 3 -screen 0 1640x1060x24 -nolisten tcp -noreset 3>"$1"' \
    runtime "$run/display" >/dev/null
for _ in $(seq 150); do [ -s "$run/display" ] && xdpyinfo -display ":$(<"$run/display")" >/dev/null 2>&1 && break; sleep 0.1; done
export DISPLAY=":$(<"$run/display")"
procs start "$run" openbox -- env DISPLAY="$DISPLAY" openbox >/dev/null
for _ in $(seq 150); do xprop -root _NET_SUPPORTING_WM_CHECK 2>/dev/null | grep -q 'window id' && break; sleep 0.1; done
procs start "$run" app -- timeout --kill-after=10s 1800s env -u JDK_JAVA_OPTIONS -u _JAVA_OPTIONS \
    DISPLAY="$DISPLAY" HOME="$run/home" XDG_DATA_HOME="$run/data" XDG_CONFIG_HOME="$run/config" \
    XDG_CACHE_HOME="$run/cache" JAVA_TOOL_OPTIONS="-Duser.home=$run/home -Dskiko.renderApi=SOFTWARE" "$app" >/dev/null
w=""
for _ in $(seq 120); do w=$(xdotool search --onlyvisible --name AndBible 2>/dev/null | head -1 || true); [ -n "$w" ] && break; sleep 1; done
[ -n "$w" ] || { echo "desktop-capture: app window did not appear" >&2; exit 1; }
sleep 10
xdotool windowmove "$w" 0 0 windowsize "$w" 1600 1000
sleep 3
read -r cx cy < <(xwininfo -id "$w" | awk '/Absolute upper-left X/{x=$4} /Absolute upper-left Y/{y=$4} END{print x, y}')
size=$(xwininfo -id "$w" | awk '/Width:/{w=$2} /Height:/{h=$2} END{print w "x" h}')
[ "$size" = 1600x1000 ] || { echo "desktop-capture: client area is $size, expected 1600x1000" >&2; exit 1; }
click() { xdotool mousemove --sync $((cx + $1)) $((cy + $2)) click 1; }
raws="$work/$lang/desktop-raw"
rm -rf "$raws"; mkdir -p "$raws"
python3 -I - "$plan" "$repo/store-screenshots/demo-content/$lang/content.yaml" "$only" <<'PY' > "$run/jobs"
import json, re, sys
plan = json.load(open(sys.argv[1]))
names = [m.group(1) for m in re.finditer(r'^  - name: "?(.*?)"?\s*$', open(sys.argv[2]).read(), re.M)]
only = set(filter(None, sys.argv[3].split(',')))
for s in plan["shots"]:
    if only and s["id"] not in only: continue
    if s["action"] != "none" or s["night"]:
        print(f"SKIP {s['id']} (action {s['action']}, night {s['night']}: not scripted on desktop)", file=sys.stderr); continue
    print(s["id"], names.index(s["workspace"]))
PY
switch_and_capture() {  # shot index
    xdotool windowactivate --sync "$w"
    click 1527 27                      # toolbar: Workspaces
    sleep 2
    click 600 $((571 + 48 * $2))       # sheet row of that workspace
    sleep 7                            # WebView layout, fonts, images
    xdotool mousemove $((cx + 1620)) $((cy + 1040))  # pointer outside the window (no hover state)
    ffmpeg -nostdin -v error -f x11grab -video_size 1600x1000 -i "$DISPLAY+$cx,$cy" -frames:v 1 -update 1 -y "$raws/$1.png"
}
# A switch that did not happen leaves the previous workspace on screen: a capture byte-identical to the previous
# shot's (of a different workspace) is retried once, then fails the run naming the shot.
prev_index="" prev_hash=""
while read -r shot index; do
    switch_and_capture "$shot" "$index"
    hash=$(sha256sum < "$raws/$shot.png")
    if [ -n "$prev_hash" ] && [ "$index" != "$prev_index" ] && [ "$hash" = "$prev_hash" ]; then
        echo "desktop-capture: $shot identical to the previous shot, retrying the workspace switch" >&2
        switch_and_capture "$shot" "$index"
        hash=$(sha256sum < "$raws/$shot.png")
        if [ "$hash" = "$prev_hash" ]; then
            echo "desktop-capture: workspace switch for $shot failed (capture unchanged after retry)" >&2
            exit 1
        fi
    fi
    prev_index="$index" prev_hash="$hash"
    echo "desktop-capture: $shot"
done < "$run/jobs"
"$here/run.sh" record --lang "$lang" --target "$target" --dir "$raws"
