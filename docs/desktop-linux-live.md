# Linux desktop runtime verification

Use the project skill `desktop-linux-live` (`.claude/skills/desktop-linux-live/SKILL.md`) for
real AndBible CEF and WebKitGTK launch, X11 input, screenshots and cleanup. Its
[runbook](../.claude/skills/desktop-linux-live/references/runbook.md) contains runnable commands
for building app images and driving a task-owned **Xvfb + openbox** session with isolated profiles.

Use `verifying-on-a-runtime` when choosing between desktop, Windows VM, Android emulator,
iOS simulator and automated gates. Packaging/release details remain in [desktop-release.md](desktop-release.md).

## Evidence boundaries

- Use the real launchers, not the WebKitGTK PoC. Test each engine explicitly.
- A visible welcome screen proves launch, not module rendering, keyboard focus, IME or persistence.
- Click/type with native X11 events; browser DOM injection is not an input test.
- Xvfb software rendering does not verify a physical GPU (issue #3946) or a real Wayland seat.
- Record the engine, commit, display, profile and actual action/exit outcome with inspected screenshots.
- Preserve meaningful evidence under `docs/superpowers/status/`; stop only owned processes and remove scratch.

The runbook also covers known traps: Weston/Xwayland XTest crashes or missing keyboard focus,
process searches matching the agent prompt, libGLX JVM-exit deadlocks, WebKitGTK GTK-thread
exit requirements, JSword home/path initialization and early boolean preference reads.
