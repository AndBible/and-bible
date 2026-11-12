# Linux live runbook

From repository root, read `.jailbee/AGENT-GUIDE.md`. Use the controller for builds; no
Android emulator or Windows VM during a heavy gate. No app changes are needed for this recipe.

## 1. Build the real launcher

Prerequisites: JDK 17 toolchain, Node 24/npm 11, Xvfb, openbox, xdotool, xdpyinfo, xprop, ffmpeg.
Check with `command -v`, `java --version`, and `node --version`. Build in the normal HOME, **not**
the isolated runtime profile (otherwise Gradle/npm caches disappear).

```bash
(cd app/bibleview-js && npm ci && npm run build-production)
./gradlew :desktopApp:createDistributable
```

If the trusted pinned Linux CEF archive already exists, the build can use
`-Pandbible.kcefArchive=.local/kcef/jbr_jcef-17.0.14-linux-x64-b1367.22.tar.gz`;
Gradle checks the pinned digest. Never bypass a checksum or fetch an unrelated binary.
Long build: background it in the controller, wait for completion, read `BUILD SUCCESSFUL`
and the exit status. One Gradle invocation at a time. The resulting real app:

- CEF: `desktopApp/build/compose/binaries/main/app/andbible/bin/andbible`

Alternatively extract a matching, known-revision deb to a fresh directory with
`dpkg-deb -x <deb> <new-dir>` and use its `opt/<package>/bin/<package>` launcher. Record its
revision; don't test a cached older build as if it contained the change. A development alternative
for CEF is `:desktopApp:run -Pkcef.args=--remote-debugging-port=9333`,
but packaged launchers make process ownership and shutdown evidence clearer.

## 2. Start an isolated X11 session

Bash variables do not persist between tool calls. In **each new shell**, set
`run=/tmp/andbible-linux-live.<the-printed-suffix>` and
`export DISPLAY=":$(<"$run/display")"`. Choose `app` again when launching another engine;
do not use an inherited host DISPLAY.

```bash
run=$(mktemp -d /tmp/andbible-linux-live.XXXXXXXX)
printf 'Runtime scratch: %s\n' "$run"
mkdir -p "$run"/{home,data,config,cache}
python3 -I scripts/desktop-live-processes.py start "$run" xvfb -- \
  bash -c 'exec Xvfb -displayfd 3 -screen 0 1600x1000x24 -nolisten tcp -noreset 3>"$1"' \
  runtime "$run/display"
# Bounded display readiness (also notices failed Xvfb startup).
python3 -I - "$run" <<'PY'
import pathlib, subprocess, sys, time
p = pathlib.Path(sys.argv[1]); end = time.monotonic() + 15
while time.monotonic() < end:
    display = (p / 'display').read_text().strip() if (p / 'display').exists() else ''
    if display:
        r = subprocess.run(['xdpyinfo', '-display', ':' + display],
                           stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, timeout=2)
        if r.returncode == 0:
            print('Display :' + display)
            break
    time.sleep(0.1)
else:
    raise SystemExit('Xvfb not ready; inspect xvfb.log, then cleanup')
PY
export DISPLAY=":$(<"$run/display")"
python3 -I scripts/desktop-live-processes.py start "$run" openbox -- \
  env DISPLAY="$DISPLAY" openbox
python3 -I - <<'PY'
import subprocess, time
end = time.monotonic() + 15
while time.monotonic() < end:
    r = subprocess.run(['xprop', '-root', '_NET_SUPPORTING_WM_CHECK'],
                       capture_output=True, text=True, timeout=2)
    if 'window id' in r.stdout:
        print(r.stdout.strip())
        break
    time.sleep(0.1)
else:
    raise SystemExit('openbox not ready; inspect openbox.log, then cleanup')
PY
```

Do not use `pgrep -f` to decide whether Xvfb exists: the agent's prompt can match. `-displayfd`
selects an unused display without disturbing another session. Check openbox readiness before
launch: `xdotool getwindowname "$(xdotool getwindowfocus)"` may initially report no active
window; `xprop -root _NET_SUPPORTING_WM_CHECK` must show the WM's window ID. Poll it with a
15-second deadline (as above), then inspect `openbox.log` on failure, rather than running
input against PointerRoot. `-noreset` prevents the X server resetting when the readiness
probe's last client disconnects; openbox then connects to the same ready server.

## 3. Launch the app (five-minute lease)

```bash
app="$PWD/desktopApp/build/compose/binaries/main/app/andbible/bin/andbible"
test -x "$app"
python3 -I scripts/desktop-live-processes.py start "$run" app -- \
  timeout --kill-after=10s 300s env -u JDK_JAVA_OPTIONS -u _JAVA_OPTIONS \
  DISPLAY="$DISPLAY" \
  HOME="$run/home" XDG_DATA_HOME="$run/data" XDG_CONFIG_HOME="$run/config" \
  XDG_CACHE_HOME="$run/cache" \
  JAVA_TOOL_OPTIONS="-Duser.home=$run/home -Dskiko.renderApi=SOFTWARE" \
  bash -c 'file=$1; shift; "$@"; rc=$?; printf "%s\n" "$rc" > "$file"; exit "$rc"' \
  runtime "$run/app.exit" "$app"
# Window readiness, max two minutes; not a blind sleep.
python3 -I - <<'PY'
import subprocess, time
end = time.monotonic() + 120
while time.monotonic() < end:
    r = subprocess.run(['xdotool', 'search', '--onlyvisible', '--name', 'AndBible'],
                       capture_output=True, text=True, timeout=3)
    if r.returncode == 0 and r.stdout.strip():
        print('Visible windows:', r.stdout.strip())
        break
    time.sleep(0.2)
else:
    raise SystemExit('App window not visible; inspect app.log, then cleanup')
PY
```

The timeout owns a process group and caps forgotten live sessions. Save the actual JVM PID
from `ps -eo pid,ppid,comm,args` within that group if diagnosing a hang. Do not export
`JAVA_TOOL_OPTIONS` globally (it would affect later Gradle calls). The command overrides it
and removes `JDK_JAVA_OPTIONS` / `_JAVA_OPTIONS` **only in the child environment**; the shell's
original settings stay untouched. Record any other needed environment change. Window appearance is not reading readiness:
inspect `app.log`, screenshots and the in-app state; initial startup may show a welcome flow.
Do not bypass first run with a pre-seeded profile when testing first run.

For an offline reading fixture use the app's file import with a known Sword module ZIP; pass
its absolute path as a launcher argument after `"$app"` above (the import may need confirmation).
Do not modify shared `~/.sword` or copy an arbitrary stale database. Test reading with installed
modules; an empty profile's welcome screen is a valid launch smoke, not a reading smoke.

## 4. Screenshot and genuine X input

```bash
# List visible windows; choose the MAIN window ID from name and geometry, not the first ID blindly.
xdotool search --onlyvisible --name AndBible
xdotool getwindowgeometry --shell <main-window-id>
ffmpeg -v error -f x11grab -video_size 1600x1000 -i "$DISPLAY" \
  -frames:v 1 -update 1 "$run/before.png"
# Inspect before.png with Read, then use its actual target coordinates:
xdotool windowactivate --sync <main-window-id>
xdotool mousemove --sync <x> <y> click 1
xdotool type --clearmodifiers --delay 40 'grace'
xdotool key --clearmodifiers BackSpace
ffmpeg -v error -f x11grab -video_size 1600x1000 -i "$DISPLAY" \
  -frames:v 1 -update 1 "$run/after.png"
```

Inspect the after image to verify the action/text. Root capture on Xvfb includes separate
Compose popup/sheet windows; window-only capture can miss them. For one chosen window use
`ffmpeg -f x11grab -window_id <id> -i "$DISPLAY" ...` instead. Native keyboard verification
requires clicking a real Compose field or browser editor, then testing letters, Backspace,
Ctrl+B/Ctrl+Z and Escape as relevant.
Use Xvfb + openbox, never headless Weston/Xwayland (no seat keyboard, so XTest keys reach nothing).
Under a window manager AWT keeps X focus on its FocusProxy and CEF's `setFocus(true)` does not move it
for a parented browser, so an embedded KCEF pane gets keys only through `kcef/X11PaneFocus` (page
mousedown -> cefQuery -> XSetInputFocus on CEF's innermost child; needs
`--add-opens java.desktop/sun.awt.X11=ALL-UNNAMED`). A switch to off-screen rendering may drop it but
must keep letters, Backspace, the editor's Ctrl+B/Ctrl+Z, Esc and IME composition. CDP via
`-Dkcef.args=--remote-debugging-port=<port>` helps in-page probes. AWT maps X buttons 8/9 (mouse
back/forward) to AWT 6/7. CDP may inspect DOM but cannot replace these input tests.
XTest ASCII input is not an IME test. For UI changes explicitly exercise relevant light,
dark, monochrome and no-animation settings; do not claim them from one light screenshot.

## 5. Close, diagnose, clean

Close through the window manager so app persistence/shutdown runs: activate the main window
and `xdotool key --clearmodifiers alt+F4`. Check exit and identify owned JVMs with:

```bash
python3 -I - "$run" <<'PY'
import json, pathlib, subprocess, sys, time
p = pathlib.Path(sys.argv[1])
end = time.monotonic() + 20
while time.monotonic() < end and not (p/'app.exit').exists():
    time.sleep(0.2)
if (p/'app.exit').exists():
    print('App exit:', (p/'app.exit').read_text().strip())
else:
    print('No natural app exit recorded; diagnose before cleanup')
# status exit 0 means the recorded leader still matches PID/PGID/starttime.
check = subprocess.run(['python3', '-I', 'scripts/desktop-live-processes.py',
                        'status', str(p), 'app'])
if check.returncode == 0:
    subprocess.run(['python3', '-I', 'scripts/desktop-live-processes.py',
                    'members', str(p), 'app'], check=True)
else:
    print('Leader gone/unverifiable: do not attach gdb or signal a reused PGID')
PY
```

`members` prints verified group PIDs; inspect their `comm`/command with `ps -p <PID>` to
identify the JVM before attaching gdb (recheck identity if there is a delay).

Only natural `app.exit` **0** is a clean exit; any nonzero code is a failure to investigate.
No `app.exit` after the 300-second lease means timeout/forced exit, not success. If shutdown
hangs, collect `app.log` and the actual JVM PID's stack from the owned group.
For a closed window with a lingering JVM, use
`sudo gdb -p <JVM-pid> -batch -ex 'thread apply all bt 15'` (bound it with `timeout 20s`).
Known libGLX `_dl_fini` -> `XESetCloseDisplay` lock deadlock prints nothing useful with
jstack/SIGQUIT; software Skia avoids loading GLX in the common Xvfb case. Do not declare a
successful natural exit after force killing it.

Preserve only inspected evidence and a short status note under `docs/superpowers/status/`;
commit the submodule, then immediately its parent gitlink. Before deleting scratch, inspect
its path and stop **only your identity-checked process groups** (app first, then openbox, Xvfb):

```bash
ls -ld "$run"
for name in app openbox xvfb; do
  python3 -I scripts/desktop-live-processes.py status "$run" "$name" || true
  python3 -I scripts/desktop-live-processes.py stop "$run" "$name"
done
```

The helper records the actual session leader PID, PGID and `/proc` starttime, avoiding
`setsid` wrapper forks and PID reuse. Missing/dead/unverifiable records are safe no-ops;
a verified live group gets TERM, then KILL only if its leader still matches after five
seconds. Inspect `ps -eo pid,pgid,comm,args` for reported descendants; never signal an
unverifiable group or all Java/browser processes. Report leftovers as blocked if their
ownership cannot be established. Remove your `$run` once evidence is committed and no
owned processes remain. Keep shared cached build inputs, not temporary profiles.

## Diagnostic seams

- Blank module path on JVM: `CWProject.setHome`'s second argument alone is treated as a
  **name under user.home**. The host sets `jsword.home` as a system property before init,
  disables `SwordBookPath.scanDefaultLocations`, and sets the download directory before
  first driver registration. Inspect `sharedLogic/src/hostMain/.../HostSwordEnvironment.kt`
  and the logged actual module path; don't create directories at the guessed doubled path.
- Background boolean settings still false: `DatabaseContainer.ready` precedes the host's
  databases-open signal. Gate on `AppEnvironment.isInitialized` / host startup, not merely
  a non-null DB. Rule: `DatabaseAppSettings.getBoolean` answers the default
  until the host's databases-open flag is set (desktop `HostStartup.databasesOpen`, after
  `DatabaseContainer.instance.sync()`); other getters stay readable early. A silent "nothing happened"
  in a background pass: check this first.
- Existing evidence: `docs/superpowers/status/2026-10-30-desktop-d3a-live-checks.md`
  (focus/input), `2026-10-29-desktop-d2b-live-checks.md` (startup/exit).
