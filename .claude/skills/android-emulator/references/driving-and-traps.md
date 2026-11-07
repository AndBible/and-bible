# Driving the app on the emulator: recipes and traps

Commands are `scripts/andbible-emu.sh <cmd>` (`E` below). Package defaults to the standard debug
build `net.bible.android.activity.debug`; the discrete build is `com.app.calculator.debug`.

## Input and reading the screen

- `adb shell input text` drops a character in about 1 of 4 uses (Gboard), and a wrong query then
  looks like a product bug ("0 results", "URL invalid"). Compose text fields are not in `texts`:
  tap the field to focus it, then read it back with
  `E dump /dev/stdout | grep -o 'text="..." resource-id="" class="android.widget.EditText'`.
  Retry via the clear-text button if it differs; press Enter/Apply only after it matches.
- Real key events: `E key KEY_A` / `E key KEY_LEFTALT KEY_O` (sendevent on the guest's AT keyboard;
  needs `adb root` and leaves adbd root, `adb unroot` undoes it). `adb emu event send EV_KEY`
  and `event text` never reach the guest on emulator 37.3.4 (`getevent` stays silent), so a
  keyboard FAIL recorded from console events is an environment artifact. Prove delivery by typing a
  letter into a focused field first.
- `adb shell input keyevent KEYCODE_BACK` closes a note editor WITH saving it.
- Never `pkill -f ...`: the pattern matches your own shell and kills it (exit 144). Use `pgrep -x`
  or an anchored path.
- `uiautomator` (so `dump`, `texts`, `tap-text`) is blind to the list content of the
  search-results sheet and the History sheet: only the header, drag handle and close button
  appear although a `shot` shows the content. Tap inside them by pixel coordinates from a
  screenshot (scale by original width / displayed width).
- `shot` (screencap) is all black on FLAG_SECURE screens. `emushot <name>` (emulator side)
  bypasses FLAG_SECURE, so its images are not evidence that the screen does not leak.

## Start versus restart versus process death

- `E start` is `force-stop` + launch: harder than anything the OS does to a backgrounded app. It
  skips `onStop`/`onSaveInstanceState`, so reading position resets and a persistence check
  wrongly looks failed. Use it only for "give me a clean launch" when persisted state does not matter.
- Process-death recipe (does state survive the app being killed and reopened?): press Home
  (`adb shell input keyevent KEYCODE_HOME`), `adb shell am kill <pkg>` (kills only a
  backgrounded app's process), open Recents (`adb shell input keyevent KEYCODE_APP_SWITCH`),
  tap the app's card (`tap-text` or screenshot coordinates). `E top` shows the relaunched activity.
- After `wipe` + `push-module` the emulator can vanish and the reboot then leaves the pushed
  `.conf` files 0 bytes (unflushed `/sdcard` writes), so Welcome says "no Bibles". `adb shell sync`
  after every push and check `mods.d` file sizes before `start`.
- The crash sheet needs two crashes within an hour (`checkCrash`), not one.

## Retained workspace traps (windows, chips, pads)

On a retained multi-window workspace (Bible / Bible / linked StudyPad) these taps silently reshape
it, and sync carries the change to the other user:

- Tapping the bottom chip of the ACTIVE window minimizes it (dashed chip); tap again to restore.
  Tapping another chip activates that window. To edit a window, tap inside its pane, not its chip.
- Toolbar Bible button on a pad window converts it into a Bible window and opens a document sheet
  (one row visible, no scrolling).
- Menu > Choose Document is absent while a pad window is active; the nav menu may start scrolled
  down, swipe it down first.
- Menu > Study Pads > pad opens the pad in the ACTIVE window, replacing what it showed.
- A document change in B's window reaches A's workspace on the next sync.

Screenshot first to see which window is highlighted. Restore order: active window 2 -> Bible sheet
or Choose Document -> KJV; un-minimize window 3 by its chip; then Menu > Study Pads > pad. Prefer a
verification slice that never touches window documents.

## Discrete build (`com.app.calculator.debug`)

- Type `1234` then `=` on the calculator to enter (`calculator_pin` default).
- FLAG_SECURE makes `shot` black: use `emushot`, or drive it with `texts` / `tap-text`.
- Modules live in internal storage (`files/modules/{mods.d,modules,mybible,mysword}`), so
  `run-as com.app.calculator.debug` + `chmod` really change permissions (the standard build's
  external storage is FUSE and ignores them): use it for read-only-directory tests.
- The VIEW-intent install dialog does not open for it (the calculator comes to front): copy the
  module in with `run-as` (`push-module` does this). `read.andbible.org` deep links do not resolve
  for the discrete package.

## Two-device sync test on one emulator

Both debug builds on the same Android user share one `deviceIdentifier` (ANDROID_ID is per signing
key + user), so they write the same device folder and skip each other's patches as "own". Put
"device B" on a second Android user:

1. `adb shell pm create-user userB` (id 10), `pm install-existing --user 10 <pkg>`; if A is the
   other build, `pm uninstall --user 0 <pkg>`.
2. `am start-user 10`, `am switch-user 10`, launch with `am start --user 10 -n <pkg>/<launcher>`
   (discrete launcher: `net.bible.android.view.activity.Calculator`).
3. Modules: discrete, stage under `/data/local/tmp` then `run-as <pkg> --user 10 sh -c 'cp -R ... files/modules/'`;
   standard, copy as root into `/data/media/10/Android/data/<pkg>/files` then `chown -R 2000:1078`.
   `run-as <pkg> --user 10 cat databases/x` reads its databases.
4. A local Nextcloud at 10.0.2.2 needs the guest proxy off: `adb shell settings put global http_proxy :0`
   (restore it afterwards). Type the server URL as `http://10.0.2.2:8080` without trailing slash
   (the slash fails sign-in with an `ArrayIndexOutOfBoundsException` in owncloud `WebdavEntry`).
5. On user 10 the app often does not sync on HOME or `am start` (user 0 does). With B in the
   foreground run `svc wifi disable; svc data disable`, wait, enable both: the network callback's
   `onAvailable` starts the sync. Bring a task back with `monkey -p <pkg> -c android.intent.category.LAUNCHER 1`;
   `am start -n .../StartupActivity` routes to the Bible view and loses the open screen.
6. Log tag `DeviceSync`; patches resolve last-writer-wins. Clean up with `pm remove-user 10`.

## WebView / JS debugging

`scripts/webview-cdp.sh <pkg> list | scripts | eval '<js>' | tail --exceptions | break <file>:<line> --eval '<js>' | screenshot <png>`
(for the emulator prefix `ANDROID_ADB_SERVER_PORT=5038`). Full flags: `docs/emulator-and-webview-debugging.md`.
The app must be running with a live WebView (`scripts/seed-emulator-webview-debug.sh` gets one
with no network). Why it beats logcat: it replays the whole console and exception backlog with real
object previews. Traps:

- The debug bundle is minified: a paused frame's locals have minified names (`e`, `t`), so `--eval`
  must use them; the original name gives a bare `ReferenceError` that looks like a wrong breakpoint.
  Breakpoint paths are original sources (`eventbus.ts:61`).
- A pause auto-resumes unless `--hold`, which really freezes the WebView.
- Logcat is the fallback (also for crash-on-load): console messages appear as `bibleview-js:` only
  while `show_errorbox` is off, and log pre-stringified strings (objects print `[object Object]`).
  Do not enable the in-page errorBox for tap-driven bugs: its overlay swallows the tap.
- Watch the pid: the app restarts, so `adb logcat --pid=` goes deaf.
- Settings are DB-backed: `adb exec-out run-as <pkg> cat /data/data/<pkg>/databases/settings.sqlite3`.
