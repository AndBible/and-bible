#!/usr/bin/env bash
# Get a FRESH container from zero to a debuggable AndBible BibleView on the
# in-container emulator, so `scripts/webview-cdp.sh` has something to attach to.
# Idempotent: re-running it only does the steps that are actually missing.
#
#   scripts/seed-emulator-webview-debug.sh [--build] [--avd NAME] [--module FinRK]
#
# --build runs the (slow, ~6 min) gradle assemble if no debug APK is present.
#
# WHAT PERSISTS ACROSS CONTAINERS (so this is often a no-op after the first run):
#   ~/.android            shared host cache -> the AVDs AND their userdata, i.e. the
#                         installed app and any pushed Sword modules survive a container
#                         recreate, as does the debug signing key
#   <repo>/.local         gitignored in the repo clone; copy testmods.zip (and the emu-verify
#                         kit, if used) into it on the host BEFORE creating the container
# WHAT DOES NOT:
#   ~/Android/Sdk is mounted READ-ONLY, so a missing system image CANNOT be installed
#   from in here — sdkmanager will fail. Install it on the HOST, then re-run.
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.."
repo="$PWD"

avd=screenshot_phone
module=FinRK
build=0
while [ $# -gt 0 ]; do
    case "$1" in
        --avd) avd="${2:?}"; shift 2 ;;
        --module) module="${2:?}"; shift 2 ;;
        --build) build=1; shift ;;
        *) echo "unknown arg $1" >&2; exit 2 ;;
    esac
done

pkg=net.bible.android.activity.compose      # the compose-port debug suffix
apk_glob="app/build/outputs/apk/standardGithub/debug/*.apk"
export ANDROID_ADB_SERVER_PORT=5038         # container-local server: 5037 is the host phone
unset ADB_SERVER_SOCKET

say() { printf '==> %s\n' "$*"; }

# 1. AVD ---------------------------------------------------------------------
if [ ! -d "$HOME/.android/avd/$avd.avd" ]; then
    say "AVD $avd missing — creating (needs the system image present in the RO host SDK)"
    "$repo/scripts/emulator-avds.sh" || {
        echo "AVD creation failed. If it complained about a system image, install it ON THE HOST:" >&2
        echo "  sdkmanager 'system-images;android-36;google_apis;x86_64'   # SDK is read-only in here" >&2
        exit 1
    }
else
    say "AVD $avd present (persisted via the ~/.android shared cache)"
fi

# 2. Emulator ----------------------------------------------------------------
if ! command adb devices | awk 'NR>1 && $2=="device"' | grep -q .; then
    say "booting $avd detached"
    # setsid: a run_in_background/nohup launch gets reaped with its task tree and the
    # emulator dies mid-session, looking like a crash.
    setsid nohup "$HOME/Android/Sdk/emulator/emulator" -avd "$avd" \
        -no-window -no-snapshot-save -no-boot-anim -no-audio -partition-size 2048 \
        -gpu swiftshader_indirect >"/tmp/emulator-$avd.log" 2>&1 </dev/null & disown
    for _ in $(seq 72); do
        [ "$(command adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ] && break
        sleep 5
    done
    [ "$(command adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ] \
        || { echo "emulator did not boot; see /tmp/emulator-$avd.log" >&2; exit 1; }
    say "booted"
else
    say "emulator already up"
fi

# 3. App ---------------------------------------------------------------------
if ! command adb shell pm list packages | tr -d '\r' | grep -q "^package:$pkg\$"; then
    apk="$(ls -t $apk_glob 2>/dev/null | head -1 || true)"
    if [ -z "$apk" ] && [ "$build" = 1 ]; then
        say "building debug APK (slow)"
        (./gradlew :app:assembleStandardGithubDebug)
        apk="$(ls -t $apk_glob 2>/dev/null | head -1 || true)"
    fi
    [ -n "$apk" ] || { echo "no debug APK — run with --build, or:
  ./gradlew :app:assembleStandardGithubDebug" >&2; exit 1; }
    say "installing $(basename "$apk")"
    command adb install -r "$apk" >/dev/null
else
    say "$pkg already installed"
fi

# 4. Sword module ------------------------------------------------------------
# Without a Bible document the app stops at its first-run screen and never creates a
# WebView at all — and strict egress means the in-app download cannot fix that.
mods_dir=/sdcard/Android/data/$pkg/files
lower="$(echo "$module" | tr 'A-Z' 'a-z')"
if [ "$(command adb shell ls "$mods_dir/modules/texts/ztext/$lower" 2>/dev/null | tr -d '\r' | grep -c .)" -lt 2 ]; then
    say "seeding Sword module $module from .local/testmods.zip"
    tmp="$(mktemp -d)"
    python3 - "$tmp" "$module" "$lower" <<'PY'
import sys, zipfile, pathlib
out, mod, lower = pathlib.Path(sys.argv[1]), sys.argv[2], sys.argv[3]
z = zipfile.ZipFile('.local/testmods.zip')
want = [n for n in z.namelist()
        if n == f'mods.d/{mod}.conf' or (n.startswith(f'modules/texts/ztext/{lower}/') and not n.endswith('/'))]
if not want:
    sys.exit(f'{mod} not in .local/testmods.zip')
for n in want:
    p = out / n
    p.parent.mkdir(parents=True, exist_ok=True)
    p.write_bytes(z.read(n))
PY
    # Create BOTH dirs first: after a fresh install the app has never run, so its
    # external files dir has no mods.d/modules yet and the push would fail. And push the
    # data files ONE BY ONE into a pre-created leaf dir: `adb push <dir> <existing dir>/`
    # dumps the CONTENTS into the parent, after which JSword reports the book as missing
    # its data files (in logcat only — the UI just says no Bibles are installed).
    command adb shell mkdir -p "$mods_dir/mods.d" "$mods_dir/modules/texts/ztext/$lower"
    command adb push "$tmp/mods.d/$module.conf" "$mods_dir/mods.d/" >/dev/null
    for f in "$tmp/modules/texts/ztext/$lower"/*; do
        command adb push "$f" "$mods_dir/modules/texts/ztext/$lower/" >/dev/null
    done
    rm -rf "$tmp"
    # A dir created by `adb shell mkdir` is owned by SHELL with mode 0770, and the app is
    # not in its group — so the app cannot enter it and JSword sees no module at all,
    # leaving the app on its first-run "no Bibles installed" screen. Verified by
    # isolating the variable: 0770 -> first-run screen, 0777 -> module loads.
    # (`run-as <pkg> cat` is NOT a valid check here — run-as does not join the app's
    # storage mount namespace, so it reports Permission denied either way.)
    command adb shell chmod 777 "$mods_dir/mods.d" "$mods_dir/modules" \
        "$mods_dir/modules/texts" "$mods_dir/modules/texts/ztext" \
        "$mods_dir/modules/texts/ztext/$lower"
    command adb shell am force-stop "$pkg"      # JSword scans mods.d at startup only
else
    say "module $module already on the device"
fi

# 5. Launch + confirm a live WebView ----------------------------------------
# Always restart: JSword scans mods.d at STARTUP only, so a process that came up before
# the module was in place stays on the first-run screen no matter how often you bring it
# to the front — and then this script would report failure for an app that is fine.
say "restarting $pkg"
command adb shell am force-stop "$pkg"
command adb shell monkey -p "$pkg" -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1
for _ in $(seq 20); do
    pid="$(command adb shell pidof "$pkg" | tr -d '\r' | awk '{print $1}')"
    [ -n "$pid" ] && command adb shell cat /proc/net/unix | tr -d '\r' |
        grep -q "@webview_devtools_remote_$pid\$" && break
    sleep 2
done
if [ -n "${pid:-}" ] && command adb shell cat /proc/net/unix | tr -d '\r' | grep -q "@webview_devtools_remote_$pid\$"; then
    say "READY — WebView debuggable (pid $pid). Try:"
    echo "  ANDROID_ADB_SERVER_PORT=5038 scripts/webview-cdp.sh $pkg list"
    echo "  ANDROID_ADB_SERVER_PORT=5038 CDP_CONSOLE=0 scripts/webview-cdp.sh $pkg eval 'document.title'"
else
    echo "no WebView socket yet for $pkg (pid ${pid:-none})." >&2
    echo "A beta-notice dialog on first launch is harmless (CDP still attaches); but if the" >&2
    echo "app is on its first-run 'no Bibles installed' screen, the module did not load —" >&2
    echo "check: adb logcat -d | grep -i sword" >&2
    exit 1
fi
