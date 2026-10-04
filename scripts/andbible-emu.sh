#!/usr/bin/env bash
# Small, boring helpers for the AndBible emulator verification pass
# (docs/emulator-and-webview-debugging.md).
# Every subcommand talks to the CONTAINER-local adb-server on port 5038 — never the
# shared host server on 5037 (that one is the maintainer's phone).
# Usage:
#
#   scripts/andbible-emu.sh boot <avd>          boot detached, wait for sys.boot_completed
#   scripts/andbible-emu.sh kill                stop the running emulator, verify qemu is gone
#   scripts/andbible-emu.sh install <apk>       adb install -r -d (never gradle install*)
#   scripts/andbible-emu.sh wipe [pkg]          pm clear (data + granted state) -> true first run
#   scripts/andbible-emu.sh push-module <zip> [pkg]
#                                               push a Sword module zip (mods.d/ + modules/) into
#                                               the app's module dir (external files dir for standard;
#                                               internal files/modules via run-as for discrete), force-stop
#   scripts/andbible-emu.sh start [pkg]         force-stop + launch the launcher activity
#   scripts/andbible-emu.sh top                 the resumed activity + task summary
#   scripts/andbible-emu.sh dump [file]         uiautomator XML (stdout, or to file)
#   scripts/andbible-emu.sh texts               one line per node: text | content-desc | bounds
#   scripts/andbible-emu.sh tap-text <regex>    tap the centre of the FIRST node whose text or
#                                               content-desc matches (grep -E); exit 1 if none
#   scripts/andbible-emu.sh shot <name>         screenshot -> .local/emu-verify/shots/<name>.png
#   scripts/andbible-emu.sh crashes [pkg]       FATAL/ANR lines from logcat since last `logmark`
#   scripts/andbible-emu.sh logmark             clear logcat (start of a box)
#
# Package defaults to the standard debug build, net.bible.android.activity.debug.
# The discrete debug build is com.app.calculator.debug.
set -euo pipefail

export ANDROID_ADB_SERVER_PORT=5038
unset ADB_SERVER_SOCKET
repo="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
kit="$repo/.local/emu-verify"
PKG_DEFAULT=net.bible.android.activity.debug

die() { echo "andbible-emu: $*" >&2; exit 1; }

booted() { [ "$(timeout 10 adb shell getprop sys.boot_completed 2>/dev/null </dev/null | tr -d '\r')" = 1 ]; }

cmd="${1:-}"; shift || true
# Fail fast instead of letting adb sit on "- waiting for device -" forever. The emulator can
# vanish mid-session with a clean "Destroyed VkInstance" as its last log line and no OOM —
# known, not your doing: boot it again and redo the current box.
case "$cmd" in
    boot|kill|""|-h|--help) ;;
    *) adb devices | awk 'NR>1 && $2=="device"' | grep -q . \
        || die "no emulator attached (it may have vanished — see /tmp/emulator-*.log); run '$0 boot <avd>'" ;;
esac
case "$cmd" in
boot)
    avd="${1:?avd name}"
    [ -d "$HOME/.android/avd/$avd.avd" ] || die "no AVD $avd (ls ~/.android/avd)"
    if adb devices | awk 'NR>1 && $2=="device"' | grep -q .; then
        die "an emulator is already running — run '$0 kill' first (one at a time)"
    fi
    sdk="${ANDROID_HOME:-$HOME/Android/Sdk}"
    [ -x "$sdk/emulator/emulator" ] || die "$sdk/emulator/emulator missing — install the system image on the host SDK (scripts/emulator-avds.sh prints the command)"
    # setsid, NOT the Bash tool's run_in_background: a background task's process tree is
    # reaped later and takes the emulator with it (looks like an emulator crash).
    # The wrapper appends "EMULATOR-EXIT=<code>" to the log: 139 = the emulator itself SEGV'd
    # (seen 2026-09-28, ~1-3 min after an app launch, with -gpu swiftshader_indirect AND guest);
    # the log otherwise just ends at "Destroyed VkInstance".
    ANDROID_HOME="$sdk" ANDROID_SDK_ROOT="$sdk" setsid nohup bash -c '
        "$1/emulator/emulator" -avd "$2" -no-window -no-snapshot-save -no-boot-anim -no-audio \
            -partition-size 2048 -gpu swiftshader_indirect
        echo "EMULATOR-EXIT=$? at $(date +%T)"' _ "$sdk" "$avd" \
        >"/tmp/emulator-$avd.log" 2>&1 </dev/null & disown
    for _ in $(seq 72); do booted && break; sleep 5; done
    booted || die "did not boot in 6 min; tail /tmp/emulator-$avd.log"
    adb shell settings put global window_animation_scale 0
    adb shell settings put global transition_animation_scale 0
    adb shell settings put global animator_duration_scale 0
    echo "booted $avd: API $(adb shell getprop ro.build.version.sdk | tr -d '\r'), $(adb shell wm size | tr -d '\r')"
    ;;
kill)
    adb emu kill >/dev/null 2>&1 || true
    for _ in $(seq 30); do pgrep -x qemu-system-x86 >/dev/null || break; sleep 2; done
    if pgrep -x qemu-system-x86 >/dev/null; then pkill -x qemu-system-x86; sleep 2; fi
    pgrep -x qemu-system-x86 >/dev/null && die "qemu still running" || echo "no emulator running"
    ;;
install)
    apk="${1:?apk path}"; adb install -r -d "$apk"
    ;;
wipe)
    adb shell pm clear "${1:-$PKG_DEFAULT}"
    ;;
push-module)
    zip="${1:?module zip}"; pkg="${2:-$PKG_DEFAULT}"
    tmp="$(mktemp -d)"; trap 'rm -r "$tmp"' EXIT
    # python3, not unzip: the container has no unzip.
    python3 -c 'import sys, zipfile; zipfile.ZipFile(sys.argv[1]).extractall(sys.argv[2])' "$zip" "$tmp"
    [ -d "$tmp/mods.d" ] || die "$zip has no mods.d/"
    case "$pkg" in
    com.app.calculator*)
        # Discrete keeps modules in INTERNAL storage (SharedConstants.modulesDir, #2521):
        # stage under /data/local/tmp, then copy in as the app via run-as.
        stage="/data/local/tmp/andbible-stage-$$"
        adb shell rm -rf "$stage" </dev/null
        (cd "$tmp" && find mods.d modules -type d) | while read -r d; do
            adb shell mkdir -p "'$stage/$d'" </dev/null
        done
        (cd "$tmp" && find mods.d modules -type f) | while read -r f; do
            adb push "$tmp/$f" "$stage/$f" >/dev/null </dev/null
        done
        adb shell chmod -R a+rX "$stage" </dev/null
        adb shell run-as "$pkg" sh -c "'mkdir -p files/modules && cp -R $stage/mods.d $stage/modules files/modules/'" </dev/null \
            || die "run-as copy failed (debug build? staging readable?)"
        adb shell rm -rf "$stage" </dev/null
        base="(internal) files/modules"
        ;;
    *)
        base="/sdcard/Android/data/$pkg/files"
        # Pre-create every dir, then push FILE BY FILE: `adb push dir existing/` flattens.
        (cd "$tmp" && find mods.d modules -type d) | while read -r d; do
            adb shell mkdir -p "'$base/$d'" </dev/null
        done
        (cd "$tmp" && find mods.d modules -type f) | while read -r f; do
            adb push "$tmp/$f" "$base/$f" >/dev/null </dev/null
        done
        # Dirs made by `adb shell mkdir` are SHELL-owned 0770 and the app cannot enter them.
        # Dirs the app created itself are app-owned and refuse the chmod — that is fine, the
        # app can already enter those, so the failure is ignored.
        (cd "$tmp" && find mods.d modules -type d) | while read -r d; do
            adb shell chmod 777 "'$base/$d'" 2>/dev/null </dev/null || true
        done
        adb shell chmod 777 "$base" 2>/dev/null || true
        ;;
    esac
    # JSword scans mods.d at startup only.
    adb shell am force-stop "$pkg"
    echo "pushed $(basename "$zip") -> $pkg:$base (app force-stopped)"
    ;;
start)
    pkg="${1:-$PKG_DEFAULT}"
    adb shell am force-stop "$pkg"
    adb shell monkey -p "$pkg" -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1
    sleep 3
    ;;
top)
    adb shell dumpsys activity activities | tr -d '\r' | grep -E 'topResumedActivity|ResumedActivity:|\* Task\{|Hist #' | head -30
    ;;
dump)
    adb shell uiautomator dump /sdcard/ui.xml >/dev/null
    if [ -n "${1:-}" ]; then adb exec-out cat /sdcard/ui.xml >"$1"; else adb exec-out cat /sdcard/ui.xml; echo; fi
    ;;
texts)
    "$0" dump | python3 -c '
import sys, xml.etree.ElementTree as ET
for n in ET.fromstring(sys.stdin.read()).iter("node"):
    t, d = n.get("text",""), n.get("content-desc","")
    if t or d:
        flags = "".join(c for c, k in (("C","clickable"),("F","focused"),("S","selected"),("K","checked")) if n.get(k)=="true")
        b = n.get("bounds")
        print(f"{t!r:40} | {d!r:30} | {b} {flags}")'
    ;;
tap-text)
    pat="${1:?regex}"
    xy="$("$0" dump | python3 -c '
import re, sys, xml.etree.ElementTree as ET
pat = re.compile(sys.argv[1])
for n in ET.fromstring(sys.stdin.read()).iter("node"):
    if pat.search(n.get("text","")) or pat.search(n.get("content-desc","")):
        x1, y1, x2, y2 = map(int, re.findall(r"\d+", n.get("bounds")))
        print((x1 + x2) // 2, (y1 + y2) // 2); break' "$pat")"
    [ -n "$xy" ] || die "no node matches /$pat/"
    adb shell input tap $xy
    echo "tapped /$pat/ at $xy"
    ;;
shot)
    name="${1:?name}"; mkdir -p "$kit/shots"
    adb exec-out screencap -p >"$kit/shots/$name.png"
    echo "$kit/shots/$name.png"
    ;;
logmark)
    adb logcat -c
    ;;
crashes)
    # The crash buffer holds only real crashes; ANRs and DataBaseNotReady come from main.
    { timeout 20 adb logcat -d -b crash; timeout 20 adb logcat -d -b main,system | grep -E 'ANR in|DataBaseNotReady'; } \
        | tr -d '\r' | grep -v '^---------' | head -60 || true
    ;;
*)
    sed -n '2,25p' "$0"; exit 2
    ;;
esac
