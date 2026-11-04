---
name: verifying-on-a-runtime
description: >
  Use when choosing where to verify an AndBible change, deciding whether green JVM,
  Robolectric or Roborazzi tests cover a platform behavior, planning cross-platform
  smoke tests, or deciding which checks need the maintainer's physical hardware.
  Triggers: "missä testataan", "millä ympäristöllä", "testaa oikeasti",
  "which environment checks this", "runtime verification", "manual checks".
---

# Choose the runtime that can observe the behavior

Route by the behavior, not by the machine nearest the maintainer. **Ordinary Windows
checks are container work**, including MSI, font/DPI and WebView2. Green automated gates
and successful compilation do not replace a live check of native glue.

## Routing table

| Subject | Environment / required skill | Evidence boundary |
|---------|-----------------------------|-------------------|
| Android lifecycle, WebView, input, API-specific behavior | Container emulator: **android-emulator** | Match the API; not OEM/e-ink/phone-call hardware |
| iOS UIKit/WebKit, Native tests, framework/XCUITest, simulator screenshots | Remote Mac: **ios-mac** | Linux iOS compile is type checking, not UIKit runtime; missing mount/relay is setup, not automatic handoff |
| Windows installer, taskbar, font/DPI, WebView2, keyboard | QEMU Windows 11: **windows-vm** | Obtain orchestrator's host-wide VM token; not physical Direct3D/GPU |
| Linux CEF/WebKitGTK launch, native click/key focus, exit | Xvfb + openbox: **desktop-linux-live** | Test each engine; not physical GPU/Wayland/IME without explicit tests |
| Shared logic/data and host-independent regressions | Module `jvmTest` (desktop modules use `test`) | Does not load UIKit or prove native browser/OS behavior |
| Android host behavior / composed image regression | Robolectric / Roborazzi | Diff-selected gates below; one bundled Skia is not per-Android rendering |
| Physical iOS/Android devices, real iOS-on-Mac app launch, physical GPU/Wayland | Named maintainer checklist, issue **#3946** | Keep exact unverified action; don't relabel container-capable work as hardware-only |

## Select, run, report

1. Use `CLAUDE.md` and `docs/superpowers/CLAUDE.md` **Gate selection** for the current diff;
   record paths and chosen gates. Roborazzi verify already includes the app unit suite:
   do not run it twice. Load the selected environment skill before runtime commands.
2. Include a fresh-install/empty-profile pass for onboarding or a verification round;
   test migration/persistence with a separate appropriate old/existing profile. Inspect
   screenshots and the actual native action, not just a ready log.
3. Report: **revision; behavior; environment/API/engine/profile; observed action/result;
   evidence path; gates and counts; unverified hardware; cleanup**. A blocked setup is
   BLOCKED, not PASS. Ask the orchestrator about missing prerequisites.

## Common mistakes

- API 35 branch with default `TEST_SDK = 33`: use explicit `@Config(sdk = [35])` and a
  pre-fix failing test; still use the matching runtime for OS behavior.
- Simulator success claimed as real iOS-on-Mac/device success, or Xvfb as physical GPU.
- Handing Windows tests to the maintainer because they own Windows: use the VM yourself.
- Waiting for pushed CI instead of the remote Mac, or leaving 6 GiB guests running.

Details, examples and gate pointers: [verification boundaries](references/boundaries.md).
