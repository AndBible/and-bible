# Emulator and WebView debugging

How to verify AndBible on an emulator and debug the BibleView (Vue.js) running inside the
Android WebView, from a dev container or any Linux box where the helper scripts in `scripts/`
apply. All scripts are plain bash (plus one Python and one Node helper) and have usage text in
their headers.

## The adb model: two servers, two ports

| Port | adb-server | Device it sees |
|------|------------|----------------|
| 5037 (adb default) | the shared host adb-server, forwarded into the container | a real phone paired on the host |
| 5038 | a container-local adb-server | an in-container emulator |

A bare `adb` talks to 5037 and sees the phone. An emulator binds its console and adbd ports on
the container's own localhost, which the host server cannot reach, so emulator work needs its own
server. The container-local one cannot use 5037 (the port forward occupies it), hence 5038.

Rules of thumb:

- Do not kill or restart the 5037 server from inside the container; that takes the phone away
  from every container sharing it. An empty `adb devices` on 5037 is a fact to report.
- Install debug builds with `adb install -r -d <apk>`, not with `./gradlew install*`: Gradle
  uses the SDK's own adb, and a version mismatch makes it replace the running server.

### `scripts/with-container-adb.sh`

Runs any command with `ANDROID_ADB_SERVER_PORT=5038` (and `ADB_SERVER_SOCKET` unset), so adb
auto-spawns the container-local server and the emulator registers with it:

```bash
scripts/with-container-adb.sh adb devices
scripts/with-container-adb.sh scripts/andbible-emu.sh boot
scripts/with-container-adb.sh ./gradlew connectedCheck
```

## Creating the AVD: `scripts/emulator-avds.sh`

Creates the `screenshot_phone` AVD (Pixel 6 Pro, API 36, `google_apis` x86_64, 6 GB userdata).
Idempotent. It needs `$ANDROID_HOME` with cmdline-tools and the system image
`system-images;android-36;google_apis;x86_64`. It never installs the image: if the SDK is
read-only (it is in the container) it prints the `sdkmanager` command to run on the host. The SDK
location is typically `~/Android/Sdk`.

## Driving the emulator: `scripts/andbible-emu.sh`

Small helpers, all against the 5038 server:

| Subcommand | Purpose |
|------------|---------|
| `boot <avd>` / `kill` | boot detached and wait for `sys.boot_completed` / stop it and verify qemu is gone |
| `install <apk>` | `adb install -r -d` |
| `wipe [pkg]` | `pm clear`, for a true first run |
| `push-module <zip> [pkg]` | push a Sword module zip (`mods.d/` + `modules/`) into the app's module dir |
| `start [pkg]`, `top` | force-stop and launch the launcher activity; show the resumed activity |
| `dump`, `texts`, `tap-text <regex>` | uiautomator XML; one line per node; tap the first matching node |
| `shot <name>` | screenshot to `.local/emu-verify/shots/<name>.png` |
| `crashes [pkg]`, `logmark` | FATAL/ANR lines since the last mark; clear logcat |

The default package is the debug build, `net.bible.android.activity.debug`. If the emulator
vanishes mid-session with no OOM, boot it again and redo the current step. Note that `start`
force-stops the app, so any in-app position is lost.

## Seeding a debuggable BibleView: `scripts/seed-emulator-webview-debug.sh`

```bash
scripts/seed-emulator-webview-debug.sh [--build] [--avd NAME] [--module FinRK]
```

Takes an empty environment to a debuggable BibleView in one idempotent command: AVD, then boot
detached, then install the debug APK (`--build` runs the slow Gradle assemble if no APK exists),
then seed a Sword module, restart the app and confirm the devtools socket exists.

The test Sword modules are not redistributable, so they are not in the repository. Place your own
copy at `.local/testmods.zip` (a ready `.sword` tree: `mods.d/` + `modules/`). `.local/` is
gitignored. Without a module the app shows no Bible text and there is nothing to debug.

The `~/.android` cache holds the AVDs and their userdata, so the installed app and pushed modules
survive a container recreate when that directory is shared. A read-only SDK cannot be updated from
inside the container; install missing system images on the host.

## WebView DevTools from the shell: `scripts/webview-cdp.sh`

Chrome DevTools Protocol against the app's WebView on a phone or the emulator:

```bash
scripts/webview-cdp.sh <pkg> list                         # devtools targets
scripts/webview-cdp.sh <pkg> scripts [--filter S]         # bundled scripts
scripts/webview-cdp.sh <pkg> eval '<js>'                  # read/write live page state
scripts/webview-cdp.sh <pkg> tail [--seconds N] [--exceptions]
scripts/webview-cdp.sh <pkg> break eventbus.ts:61 [--eval '<js>'] [--seconds N] [--hold]
scripts/webview-cdp.sh <pkg> screenshot out.png           # the WebView only
```

Options: `--device <serial>`, `--socket <name>` (any devtools socket, e.g. Chrome's), `--seconds`.
Every mode exits by itself.

Which device: the script follows `ANDROID_ADB_SERVER_PORT`, so it agrees with the `adb` CLI.
Unset or 5037 is the phone; 5038 is the emulator. For the emulator either export 5038 or run the
script under `scripts/with-container-adb.sh`.

Why not `adb forward`: for a host-connected device the listener would open on the host, out of the
container's reach. `scripts/adb-localabstract-proxy.py` speaks the adb wire protocol itself and
binds the listener inside the container; `scripts/cdp.mjs` carries the session using Node's
built-in `WebSocket` (Node 21+), so no npm dependency is needed.

What it gives you over logcat:

- Attaching replays the page's whole console and exception backlog.
- Evaluation is two-way and the JS context persists between invocations.
- Breakpoints accept an original source path (`eventbus.ts:61`) because the Vite debug bundle
  ships inline sourcemaps.

Caveats: the bundle is minified, so locals in a paused frame have minified names and `--eval`
must use them. A breakpoint pause auto-resumes unless you pass `--hold`, which really freezes the
WebView on the device.
