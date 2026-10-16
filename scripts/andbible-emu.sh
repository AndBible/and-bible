#!/usr/bin/env bash
# Small, boring helpers for the AndBible emulator verification pass
# (docs/emulator-and-webview-debugging.md).
# Every subcommand talks to the CONTAINER-local adb-server on port 5038 — never the
# shared host server on 5037 (that one is the maintainer's phone).
# Usage:
#
#   scripts/andbible-emu.sh boot [avd]          boot detached (default AVD screenshot_phone, created
#                                               if missing), wait for sys.boot_completed
#   scripts/andbible-emu.sh kill                stop the running emulator, verify qemu is gone
#   scripts/andbible-emu.sh health              emulator version, GPU mode, qemu threads/RSS, exit code
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
#
# boot environment:
#   ANDBIBLE_EMU_GPU   auto (default) | host | swiftshader_indirect | <any -gpu mode>.
#                      auto = host when a GPU render node, weston and Xwayland are available
#                      (hardware GL through a headless weston + Xwayland display), else
#                      swiftshader_indirect.
#   ANDBIBLE_EMU_PROXY the guest's global HTTP(S) proxy (`settings put global http_proxy`);
#                      defaults to $HTTPS_PROXY. Set it empty to disable. In strict egress the
#                      guest reaches the network only through this proxy. Not `emulator
#                      -http-proxy`: that one CONNECTs to IP addresses, which a hostname
#                      allowlist rejects.
# See docs/emulator-and-webview-debugging.md, "Emulator version and GPU mode".
set -euo pipefail

export ANDROID_ADB_SERVER_PORT=5038
unset ADB_SERVER_SOCKET
repo="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
kit="$repo/.local/emu-verify"
PKG_DEFAULT=net.bible.android.activity.debug

die() { echo "andbible-emu: $*" >&2; exit 1; }

booted() { [ "$(timeout 10 adb shell getprop sys.boot_completed 2>/dev/null </dev/null | tr -d '\r')" = 1 ]; }

# The jailbee golden image's SDK (.jailbee/install.d/94-android-sdk.sh) unless ANDROID_HOME says otherwise.
if [ -n "${ANDROID_HOME:-}" ]; then sdk="$ANDROID_HOME"
elif [ -d /opt/android-sdk ]; then sdk=/opt/android-sdk
else sdk="$HOME/Android/Sdk"; fi
avd_home="${ANDROID_AVD_HOME:-$HOME/.android/avd}"
# The emulator and avdmanager silently fall back to ~/.android/avd when ANDROID_AVD_HOME is
# set but missing, so make sure it exists.
mkdir -p "$avd_home"
# qemu's process name, truncated by the kernel to 15 characters (pgrep -x needs it exactly).
QEMU=qemu-system-x86

emulator_version() { sed -n 's/^Pkg.Revision=//p' "$sdk/emulator/source.properties" 2>/dev/null; }

# Emulator 37.2.12 and older leak host render threads and SIGSEGV (exit 139) in gfxstream after
# app force-stops; 37.3.4 does not (measured 2026-10-16).
check_emulator_version() {
    local v; v="$(emulator_version)"
    if [ -n "$v" ] && [ "$(printf '%s\n' "$v" 37.3.4 | sort -V | head -1)" != 37.3.4 ]; then
        echo "andbible-emu: WARNING: emulator $v is older than 37.3.4 and crashes (exit 139) after" >&2
        echo "  app restarts. The jailbee golden image ships 37.3.4 in /opt/android-sdk." >&2
    fi
}

# A headless weston with Xwayland gives the emulator a GLX display backed by the GPU render
# node (Mesa), which is what `-gpu host` needs on Linux. Prints the X display (":N").
ensure_gpu_display() {
    local log=/tmp/andbible-emu-weston.log pidf=/tmp/andbible-emu-weston.pid d
    if [ -f "$pidf" ] && kill -0 "$(cat "$pidf")" 2>/dev/null; then
        d="$(sed -n 's/.*xserver listening on display \(:[0-9]*\).*/\1/p' "$log" | tail -1)"
        [ -n "$d" ] && { echo "$d"; return 0; }
    fi
    export XDG_RUNTIME_DIR="${XDG_RUNTIME_DIR:-/run/user/$(id -u)}"
    mkdir -p "$XDG_RUNTIME_DIR" 2>/dev/null || true
    [ -f "$pidf" ] && kill "$(cat "$pidf")" 2>/dev/null
    env -u DISPLAY -u WAYLAND_DISPLAY setsid nohup weston --backend=headless --renderer=gl \
        --socket=wl-andbible-emu --xwayland --idle-time=0 >"$log" 2>&1 </dev/null &
    echo $! >"$pidf"; disown
    for _ in $(seq 20); do
        d="$(sed -n 's/.*xserver listening on display \(:[0-9]*\).*/\1/p' "$log" | tail -1)"
        [ -n "$d" ] && { echo "$d"; return 0; }
        sleep 0.5
    done
    kill "$(cat "$pidf")" 2>/dev/null; rm -f "$pidf"
    return 1
}

# host:port of the proxy the guest should use ("" for none).
guest_proxy() {
    local p="${ANDBIBLE_EMU_PROXY-${HTTPS_PROXY:-${https_proxy:-}}}"
    p="${p#*://}"; p="${p%%/*}"; p="${p##*@}"   # Android's setting takes neither scheme nor credentials
    # The container's loopback is 10.0.2.2 from inside the guest.
    case "$p" in 127.0.0.1:*|localhost:*) p="10.0.2.2:${p##*:}" ;; esac
    echo "$p"
}

# Ask the proxy for a tunnel from inside the guest; succeeds on "200".
proxy_works() {
    local hp="$1"
    timeout 20 adb shell "printf 'CONNECT crosswire.org:443 HTTP/1.1\r\nHost: crosswire.org:443\r\n\r\n' | nc -w 5 ${hp%:*} ${hp##*:} 2>/dev/null | head -1" 2>/dev/null \
        | grep -q ' 200 '
}

pick_gpu() {
    local gpu="${ANDBIBLE_EMU_GPU:-auto}"
    if [ "$gpu" = auto ]; then
        gpu=swiftshader_indirect
        if ls /dev/dri/renderD* >/dev/null 2>&1 && command -v weston >/dev/null && command -v Xwayland >/dev/null; then
            gpu=host
        fi
    fi
    echo "$gpu"
}

cmd="${1:-}"; shift || true
# Fail fast instead of letting adb sit on "- waiting for device -" forever. If the emulator
# vanished, `health` shows its exit code; boot it again and redo the current box.
case "$cmd" in
    boot|kill|health|""|-h|--help) ;;
    *) adb devices | awk 'NR>1 && $2=="device"' | grep -q . \
        || die "no emulator attached (it may have vanished — see /tmp/emulator-*.log); run '$0 boot'" ;;
esac
case "$cmd" in
boot)
    avd="${1:-screenshot_phone}"
    if adb devices | awk 'NR>1 && $2=="device"' | grep -q .; then
        die "an emulator is already running — run '$0 kill' first (one at a time)"
    fi
    [ -x "$sdk/emulator/emulator" ] || die "$sdk/emulator/emulator missing (ANDROID_HOME=$sdk)"
    check_emulator_version
    if [ ! -d "$avd_home/$avd.avd" ]; then
        ANDROID_HOME="$sdk" "$repo/scripts/emulator-avds.sh" "$avd" || die "could not create AVD $avd"
    fi
    # A killed or crashed emulator leaves its lock behind, and the next boot then refuses with
    # "Another emulator instance is running". Only remove it when no qemu is running at all.
    # Likewise netsimd (the emulators' shared network daemon) can outlive its emulator wedged
    # half shut down; the next emulator then hangs connecting to it, and the emulator's own
    # watchdog kills it with exit 139 ("detected a hanging thread 'QEMU2 main loop'").
    if ! pgrep -x "$QEMU" >/dev/null; then
        rm -rf "$avd_home/$avd.avd/"*.lock
        pkill -x netsimd && sleep 1
        pkill -9 -x netsimd || true
        rm -f "${XDG_RUNTIME_DIR:-/run/user/$(id -u)}"/netsim.ini{,.init.lock}
    fi
    proxy="$(guest_proxy)"
    log="/tmp/emulator-$avd.log"
    # Launch with one GPU mode and wait for boot; on failure kill qemu and return 1.
    launch() {
        local gpu="$1" display=
        if [ "$gpu" = host ]; then
            display="$(ensure_gpu_display)" || { echo "andbible-emu: no headless GPU display (see /tmp/andbible-emu-weston.log)" >&2; return 1; }
        fi
        local args=(-avd "$avd" -no-window -no-snapshot-save -no-boot-anim -no-audio -partition-size 2048 -gpu "$gpu")
        echo "andbible-emu: emulator $(emulator_version), -gpu $gpu${display:+ (DISPLAY $display)}${proxy:+, proxy $proxy}"
        # setsid, NOT the Bash tool's run_in_background: a background task's process tree is
        # reaped later and takes the emulator with it (looks like an emulator crash).
        # The wrapper appends "EMULATOR-EXIT=<code>" to the log: 139 = the emulator SIGSEGV'd.
        # No proxy variables: the emulator starts netsimd (the guest's WiFi, its default
        # network), which takes an ambient http_proxy as its --http-proxy and then CONNECTs every
        # guest TCP connection to a bare IP, which a hostname allowlist refuses ("Connection
        # refused" in the guest, even for 10.0.2.2). The guest's own global proxy covers egress.
        env -u WAYLAND_DISPLAY -u http_proxy -u HTTP_PROXY -u https_proxy -u HTTPS_PROXY \
            -u no_proxy -u NO_PROXY -u all_proxy -u ALL_PROXY ${display:+DISPLAY=$display} ANDROID_HOME="$sdk" ANDROID_SDK_ROOT="$sdk" \
            setsid nohup bash -c '"$0" "$@"; echo "EMULATOR-EXIT=$? at $(date +%T)"' \
            "$sdk/emulator/emulator" "${args[@]}" >"$log" 2>&1 </dev/null & disown
        for _ in $(seq 72); do
            booted && return 0
            # A renderer that failed to start leaves qemu hung rather than exiting.
            if grep -q -e '^EMULATOR-EXIT=' -e 'Could not start renderer' "$log" 2>/dev/null; then
                echo "andbible-emu: emulator failed: $(grep -E 'FATAL|ERROR|EMULATOR-EXIT' "$log" | tail -3)" >&2
                break
            fi
            sleep 5
        done
        pkill -9 -x "$QEMU" || true
        sleep 2
        return 1
    }
    gpu="$(pick_gpu)"
    if ! launch "$gpu"; then
        if [ "$gpu" = host ] && [ "${ANDBIBLE_EMU_GPU:-auto}" = auto ]; then
            echo "andbible-emu: -gpu host failed; retrying with swiftshader_indirect" >&2
            rm -rf "$avd_home/$avd.avd/"*.lock
            pkill -9 -x netsimd || true
            launch swiftshader_indirect || die "did not boot; tail $log"
        else
            die "did not boot; tail $log"
        fi
    fi
    adb shell settings put global window_animation_scale 0
    adb shell settings put global transition_animation_scale 0
    adb shell settings put global animator_duration_scale 0
    if [ -n "$proxy" ]; then
        adb shell settings put global http_proxy "$proxy"
        # The guest network comes up a little after boot_completed.
        for _ in $(seq 12); do proxy_works "$proxy" && break; sleep 5; done
        proxy_works "$proxy" || echo "andbible-emu: WARNING: the guest cannot tunnel through $proxy; downloads in the app will fail" >&2
    else
        adb shell settings delete global http_proxy >/dev/null
    fi
    echo "booted $avd: API $(adb shell getprop ro.build.version.sdk | tr -d '\r'), $(adb shell wm size | tr -d '\r')"
    ;;
health)
    echo "emulator $(emulator_version) in $sdk; AVD home $avd_home"
    log="$(ls -t /tmp/emulator-*.log 2>/dev/null | head -1)"
    if [ -n "$log" ]; then
        echo "$log: $(grep -m1 -o 'gles_mode_selected:[a-z_]*' "$log") $(grep -m1 -o 'Graphics Adapter Android Emulator OpenGL ES Translator.*' "$log" | sed 's/.*Translator //') $(grep '^EMULATOR-EXIT=' "$log" || echo running)"
    fi
    if adb devices | awk 'NR>1 && $2=="device"' | grep -q .; then
        p="$(adb shell settings get global http_proxy | tr -d '\r')"
        case "$p" in
            null|:0|"") echo "guest proxy: none" ;;
            *) proxy_works "$p" && echo "guest proxy $p: works" || echo "guest proxy $p: NOT reachable" ;;
        esac
    fi
    if q="$(pgrep -x "$QEMU" | head -1)"; then
        echo "qemu pid $q: $(ls /proc/$q/task | wc -l) threads ($(cat /proc/$q/task/*/comm 2>/dev/null | grep -c RenderThread) RenderThread), RSS $(( $(awk '/VmRSS/{print $2}' /proc/$q/status) / 1024 )) MB"
    else
        echo "no qemu running"
    fi
    ;;
kill)
    adb emu kill >/dev/null 2>&1 || true
    for _ in $(seq 30); do pgrep -x "$QEMU" >/dev/null || break; sleep 2; done
    if pgrep -x "$QEMU" >/dev/null; then pkill -x "$QEMU" || true; sleep 5; fi
    # A hung renderer ignores SIGTERM.
    if pgrep -x "$QEMU" >/dev/null; then pkill -9 -x "$QEMU" || true; sleep 2; fi
    pkill -x netsimd || true
    pgrep -x "$QEMU" >/dev/null && die "qemu still running" || echo "no emulator running"
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
    # An emulator crash loses unflushed /sdcard writes (0-byte .conf files after a reboot).
    adb shell sync
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
    sed -n '2,41p' "$0"; exit 2
    ;;
esac
