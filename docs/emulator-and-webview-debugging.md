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

## Emulator version and GPU mode

The in-container emulator used to die with exit code 139 (SIGSEGV) every few minutes of testing,
and its host process grew by ~250 MB per app restart. Both come from gfxstream, the emulator's
host-side graphics renderer, in emulator 37.2.12 (the stable build in October 2026):

- **Leak.** Each guest process that dies with a GL context (an app force-stop, `pm clear`, a
  WebView renderer exit) leaves ~16 host threads (SwiftShader worker pools, named `RenderThread`)
  and ~250 MB behind. After ~70 restarts qemu used 11 GB for a 4 GB AVD.
- **Crash.** A guest process dying while another one starts (force-stop + relaunch) races
  `FrameBuffer::cleanupProcGLObjects` against `ColorBufferGl::create` on another render thread,
  which then runs GL with no current context and crashes in `TextureResize::TextureResize`
  (`strlen(NULL)`). Captured with gdb on 2026-10-16; host load widens the race window.
- `-gpu guest` does not help: on 37.x it still renders on the host (swangle), with the same leak.

Emulator **37.3.4** has neither problem in the same tests (25 force-stop cycles that crashed 37.2.12
at cycle 6; a 30-minute soak with `pm clear` every fifth cycle under extra CPU load). The jailbee
golden image therefore installs the SDK itself, with that emulator build pinned
(`.jailbee/install.d/94-android-sdk.sh`, `/opt/android-sdk`), instead of mounting the host SDK.
Do not `sdkmanager --update` there: it would replace the pinned emulator with the stable one.
`scripts/andbible-emu.sh boot` warns when it finds an older emulator.

GPU mode (`ANDBIBLE_EMU_GPU`, default `auto`):

| Mode | When | Notes |
|------|------|-------|
| `host` | a GPU render node (`/dev/dri/renderD*`) plus `weston` and `Xwayland` exist | Mesa on the host GPU. `andbible-emu.sh` starts a headless weston with Xwayland, because the emulator's `-gpu host` on Linux needs a GLX display. Boots in ~17 s; no SwiftShader thread pools at all. |
| `swiftshader_indirect` | fallback, also automatic when `host` fails to start | CPU rendering, roughly 2 cores busy while the app draws. Stable on 37.3.4 (56 mixed cycles under load); qemu RSS still grows ~10 MB per app restart, so reboot the emulator after a few hundred. |

Two more boot failures that `boot` now prevents: a killed emulator leaves its AVD lock behind
("Another emulator instance is running"), and a `netsimd` (the emulators' shared network daemon)
left half shut down by an earlier emulator makes the next one hang until its watchdog exits 139
("detected a hanging thread 'QEMU2 main loop'"). When no qemu is running, `boot` removes both.

Check a running emulator with `scripts/andbible-emu.sh health` (version, GPU, qemu threads and
RSS, exit code). A thread count or RSS that grows with every app restart means the leak is back.

## Creating the AVD: `scripts/emulator-avds.sh`

Creates an AVD, default `screenshot_phone` (Pixel 6 Pro, API 36, `google_apis` x86_64, 6 GB
userdata), in `$ANDROID_AVD_HOME`. Idempotent; `andbible-emu.sh boot` calls it when the AVD is
missing. It needs `$ANDROID_HOME` with cmdline-tools and the system image
`system-images;android-36;google_apis;x86_64`, which the golden image has.

In jailbee `ANDROID_AVD_HOME` is container-local (`~/.local/share/android-avd`), so every container
starts from a fresh AVD. AVDs used to live in the shared `~/.android`, where containers collided
("Another emulator instance is running") and old AVDs piled up on the host disk.

## Driving the emulator: `scripts/andbible-emu.sh`

Small helpers, all against the 5038 server:

| Subcommand | Purpose |
|------------|---------|
| `boot [avd]` / `kill` | boot detached (GPU mode and proxy as above) and wait for `sys.boot_completed` / stop it and verify qemu is gone |
| `health` | emulator version, GPU mode, qemu threads and RSS, last exit code |
| `install <apk>` | `adb install -r -d` |
| `wipe [pkg]` | `pm clear`, for a true first run |
| `push-module <zip> [pkg]` | push a Sword module zip (`mods.d/` + `modules/`) into the app's module dir |
| `start [pkg]`, `top` | force-stop and launch the launcher activity; show the resumed activity |
| `dump`, `texts`, `tap-text <regex>` | uiautomator XML; one line per node; tap the first matching node |
| `shot <name>` | screenshot to `.local/emu-verify/shots/<name>.png` |
| `crashes [pkg]`, `logmark` | FATAL/ANR lines since the last mark; clear logcat |

The default package is the debug build, `net.bible.android.activity.debug`. If the emulator
vanishes mid-session, `health` shows the exit code; boot it again and redo the current step, and
re-check pushed files (`push-module` runs `adb shell sync`, but a crash loses unflushed writes).
Note that `start` force-stops the app, so any in-app position is lost.

**Network.** In strict egress the guest reaches the network only through the container's proxy.
`boot` sets the guest's global proxy (`settings put global http_proxy`) to `$HTTPS_PROXY`
(`ANDBIBLE_EMU_PROXY` overrides it; empty disables) and checks that a tunnel works; `health` repeats
the check. Apps and the WebView then send `CONNECT host:443`, which the hostname allowlist accepts.
The emulator's own `-http-proxy` is not used: it tunnels every guest connection as
`CONNECT <ip>:443`, which a hostname allowlist rejects.

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

The AVD is container-local, so a new container is seeded again (about a minute). The shared
`~/.android` keeps the debug signing key, so `adb install -r` of a new build still works.

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
