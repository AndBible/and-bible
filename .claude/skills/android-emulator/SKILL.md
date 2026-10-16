---
name: android-emulator
description: >
  Use when booting, provisioning or driving the in-container Android emulator for AndBible
  (emulator verification, device smoke tests, screenshots, WebView/CDP debugging), or when the
  emulator vanishes, exits 139, hangs at boot, refuses with "Another emulator instance is
  running", or the app on it cannot download. Triggers: "testaa emulaattorilla", "emulaattori
  kaatui", "smoke test on the emulator", "boot the emulator".
---

# Android emulator in the jailbee container

All emulator work goes through `scripts/andbible-emu.sh` (port 5038, container-local adb server).
Background and measurements: `docs/emulator-and-webview-debugging.md`.

## Quick reference

| Goal | Command |
|------|---------|
| Boot (creates the AVD if missing) | `scripts/andbible-emu.sh boot` |
| Empty container to debuggable BibleView | `scripts/seed-emulator-webview-debug.sh [--build]` |
| Install a build | `scripts/andbible-emu.sh install <apk>` |
| Add a Sword module | `scripts/andbible-emu.sh push-module <zip>` |
| Launch, look, tap | `start`, `shot <name>`, `texts`, `tap-text <regex>` |
| Is it healthy? | `scripts/andbible-emu.sh health` |
| Stop | `scripts/andbible-emu.sh kill` |

Smoke test from an empty container, in order (modules are read at app start only):
`boot` → `install <apk>` → `push-module <zip>` → `start` → `shot <name>`.

`boot` picks the GPU mode (`host` on the GPU render node via a headless weston + Xwayland,
else `swiftshader_indirect`; override with `ANDBIBLE_EMU_GPU`), sets the guest proxy, clears a
stale AVD lock and a wedged `netsimd`, and warns if the emulator is older than 37.3.4.

## Rules

- Run `boot` in an ordinary foreground Bash call. It detaches the emulator with `setsid`
  itself; a `run_in_background` launch is reaped with its task and looks like a crash.
- Use `adb` only on 5038 (the script exports it). Never `adb kill-server`/`start-server` on
  5037, and never `./gradlew install*`: both take down the shared host adb server.
- The AVD lives in `$ANDROID_AVD_HOME` (container-local). Do not create AVDs in, or wipe,
  `~/.android/avd`: that directory is shared with the host and other containers.
- After pushing files, `adb shell sync` (`push-module` does it). A crash loses unflushed writes.

## When something goes wrong

| Symptom | Cause | Do |
|---------|-------|----|
| `health` says emulator < 37.3.4, or it exits 139 after app restarts | gfxstream leak/crash in 37.2.x | Use the golden image's `/opt/android-sdk` (the script's default); report if the image lacks it |
| `EMULATOR-EXIT=139` after "detected a hanging thread" (boot or later) | wedged `netsimd` | `kill`, then `boot` again (boot clears it) |
| "Another emulator instance is running" | lock left by a killed emulator | `kill`, then `boot` |
| `health` RenderThread count or RSS climbs with every app restart (37.2.12 leaked ~16 threads and ~250 MB per restart; healthy plateaus after a few) | the leak is back (old emulator or new bug) | Record `health` output and the emulator version; reboot the emulator |
| App downloads fail | guest proxy unreachable (`health` says NOT reachable) | Check `/proc/$(pgrep -x netsimd)/environ` has no proxy vars (boot unsets them; netsimd would CONNECT bare IPs), then `HTTPS_PROXY` and `egress_allow` |
| "Failed to get EGL display" / no renderer | no GLX display for `-gpu host` | `ANDBIBLE_EMU_GPU=swiftshader_indirect scripts/andbible-emu.sh boot`; see `/tmp/andbible-emu-weston.log` |

A crash you cannot explain from this table: get a backtrace with `sudo gdb -p <qemu pid>`
(`handle SIGSEGV stop`, then `bt` in the crashing thread) before changing anything.
