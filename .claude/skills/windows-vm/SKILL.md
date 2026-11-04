---
name: windows-vm
description: >
  Use when testing AndBible Windows desktop behavior in the container: MSI/zip install,
  taskbar icons, WebView2, font metrics, keyboard/mouse input, DPI scaling, or a Windows-only
  crash; also when starting, driving or cleaning the QEMU Windows 11 VM.
  Triggers: "testaa windowsilla", "aja windows-vm:llä", "Windows-bugi", "Windows smoke test".
---

# Windows VM runtime checks

Windows checks belong in this container, not on the maintainer's checklist. Use
`bash scripts/windows-vm.sh` from the repo root. The shared base is
`.local/windows-vm/base.qcow2`; do not rebuild or modify it for a test.

**Read [the runbook](references/runbook.md) before the first command.** It contains the
bounded boot smoke, interactive logon, MSI/zip installation, DPI changes and cleanup.
Full infrastructure details: `docs/windows-vm.md`.

## Quick reference

All commands below are arguments to `bash scripts/windows-vm.sh`.

| Goal | Arguments |
|------|-----------|
| Clean, throwaway boot | `start`, then bounded SSH readiness check (runbook) |
| Inspect current guest | `status`, `ssh '<PowerShell>'` |
| Copy installer / fetch evidence | `scp-to <local> C:/abtest/file`, `scp-from <guest> <local>` |
| Launch visible app (after console logon) | `gui-run '<cmd.exe command>'` |
| Screenshot / input | `shot <unique-name>`, `mouse click <x> <y>`, `key <combo>`, `type '<ASCII>'` |
| Secret keyboard input | `type-stdin < <credential-file>` (never secret text in argv) |
| Build in persistent guest | `dev-start`, `dev-provision`, `push-repo`, `dev-gradle :desktopApp:packageWindowsZip` |
| Release resources | `stop`, then `dev-reset`; remove only your run/output/shot files |

## Rules and common mistakes

- **6 GiB RAM; never alongside the Android emulator or a heavy container gate.** Stop your
  emulator first using `android-emulator`. **Only one container may run a Windows VM at a
  time**; get the orchestrator's VM token before `install`/`start`/`dev-start`.
  One VM per container; inspect ownership before stopping any existing process.
- `start` means QMP ready, not Windows/SSH ready. Check SSH with a deadline; a login-screen
  screenshot proves only VM boot, not an AndBible test.
- An SSH-launched GUI is invisible and may crash Skia. Log on at the console, then use
  `gui-run`. Do not print, commit or screenshot credentials.
- `mouse` takes pixels from the **current guest screenshot**, not Compose dp or host pixels.
  For 125% / 150%, set `LogPixels` / `Win8DpiScaling`, log off and back on, then recapture.
- Plain `start` discards guest changes next time. `dev-start` retains them across `stop`,
  but the dev overlay is still task scratch: delete it before `done`.
- Preserve before/after screenshots and relevant logs in `docs/superpowers/status/`,
  commit there and immediately commit the parent gitlink. Stop and clean even on failure.
- VM software rendering does not establish physical-GPU behavior (issue #3946), TPM
  behavior, or a successful physical-device check. Report only what was observed.
