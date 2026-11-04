---
name: desktop-linux-live
description: >
  Use when launching or testing AndBible Linux desktop live: CEF/KCEF or WebKitGTK,
  real click/key input, screenshots, focus, blank panes, shutdown hangs or exit 134;
  also when Xvfb is supposedly running but no display works.
  Triggers: "testaa Linux-työpöydällä", "aja desktop", "desktopin näppäimet",
  "Linux desktop smoke", "Xvfb", "openbox".
---

# Linux desktop live checks

Use **Xvfb + openbox** for click and keyboard checks. Weston/Xwayland can render while
having no keyboard focus, and XTest clicks can crash it. JVM tests or an app window
alone do not prove embedded-browser input.

**Read [the runbook](references/runbook.md) first:** build both real app images, start an
isolated X11 display/profile, drive the app, screenshot and clean up with deadlines.
Background and packaging: `docs/desktop-linux-live.md`, `docs/desktop-release.md`.

## Quick reference

| Goal | Tool / command |
|------|----------------|
| Build CEF app image | `./gradlew :desktopApp:createDistributable` |
| Build WebKitGTK app image | `./gradlew :desktopWebkitGtk:createDistributable` |
| Start real app | packaged `bin/andbible` / `bin/andbible-webkitgtk` (runbook) |
| Safe display / process ownership | `Xvfb -displayfd` + `openbox` via `python3 -I scripts/desktop-live-processes.py` |
| Activate / type / click | `xdotool windowactivate --sync`, `type`, `key`, `mousemove ... click` |
| Capture evidence | `ffmpeg -f x11grab`; inspect PNG with Read |
| Window gone, JVM alive | `sudo gdb -p <app-pid> -batch -ex 'thread apply all bt 15'` |

## Rules and common mistakes

- Use separate scratch profiles for fresh-install and persisted-state checks. Same-profile
  simultaneous CEF/WebKitGTK launches hit the shared single-instance lock; that is not a
  browser failure. Record engine, revision, display, profile and rendering mode.
- Start openbox as well as Xvfb: PointerRoot focus without a window manager hides real
  focus defects. Use screen coordinates from a current screenshot; click the target field
  or pane before typing. Injected DOM/CDP text is not native-keyboard verification.
- Filter process **names** (`pgrep -x Xvfb`) or use your saved PIDs. `pgrep -f Xvfb` can
  match the agent prompt; `pkill -f` can kill the session. Never stop unrelated Java/display
  processes or Gradle daemons.
- Use `-Dskiko.renderApi=SOFTWARE` for deterministic Xvfb checks; this does **not** test a
  physical GPU (#3946). A hang in libGLX exit destructors needs gdb, not repeated jstack.
- WebKitGTK needs GTK4/libwebkitgtk-6.0 and `GDK_BACKEND=x11`; never substitute the PoC
  launcher for the real app. An exit 134 after green assertions is still a failed exit
  (GTK-thread exit handlers; see runbook).
- Blank modules: inspect host JSword home and registration order. Boolean settings still
  defaulting: check `AppEnvironment.isInitialized`, not just `DatabaseContainer.ready`.
- End every run by closing the app, boundedly checking exit, and killing only your remaining
  process groups. Preserve inspected evidence in `docs/superpowers/status/`; delete scratch.
