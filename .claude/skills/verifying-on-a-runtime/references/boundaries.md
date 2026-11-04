# Verification boundaries

## Project entry points

Load the skill named in the router, then its runbook; do not reconstruct commands from
old status notes. Current source documents:

| Skill / gate | Source |
|--------------|--------|
| android-emulator | `docs/emulator-and-webview-debugging.md`, `scripts/andbible-emu.sh` |
| ios-mac | `docs/ios-remote-mac.md`, `scripts/mac-remote.sh` |
| windows-vm | `docs/windows-vm.md`, `scripts/windows-vm.sh` |
| desktop-linux-live | `docs/desktop-linux-live.md`, `scripts/desktop-live-processes.py` |
| Diff-selected automated gates | `CLAUDE.md`, private `docs/superpowers/CLAUDE.md` "Gate selection" |
| Outstanding physical checks | GitHub issue #3946 (read-only lookup: `gh issue view 3946`) |

The existing VM base, the container's emulator and the remote Mac are real project
capabilities. Missing credentials, a relay or a package may block setup; report the exact
prerequisite and ask through the appropriate channel, rather than assigning ordinary tests
to the maintainer. A child asks the orchestrator with `jailbee inbox send message --kind question`.
Remote Mac optional mount is `mac-ssh`: follow ios-mac's exact mount/relay checks, never look
for keys elsewhere. No new test environment is required merely because CI is remote.

## Representative routing example

Change: shared UI plus a Windows font/DPI bug, iOS UIKit picker and Linux key focus.

- Shared module: relevant `jvmTest` and iOS compile tasks according to the diff-based
  gate selection. Native/host glue may require extra gates; do not infer scope from the
  filename alone if app tests exercise the shared code through Koin/rendering.
- Android images affected: Roborazzi verify plus applicable assembly per gate rules.
  Verify already runs the full app unit suite; inspect JUnit counts from the unfiltered run.
- Windows: obtain the host-wide VM token, then use windows-vm, MSI install or ZIP launch,
  125%/150% logoff/logon, input and screenshots. Do not hand this to the maintainer.
- UIKit picker: use ios-mac for simulator/XCUITest. Linux compilation cannot catch UIKit
  threading, delegate lifetime or native interop at runtime. No Mac mount? Ask for setup.
- Linux: use desktop-linux-live under Xvfb + openbox with both relevant engines; test real
  keys after clicking a field/pane, then natural app exit. DOM-injected text is insufficient.
- Physical Direct3D stutter or iOS app **Designed for iPad on macOS**: leave the specific
  physical-GPU / real-Mac app launch check with the maintainer (#3946). Simulator boot on
  the Mac does not check the Mac app launch. Never claim these from software rendering.

Do runtime steps sequentially: no Android emulator alongside the 6 GiB Windows VM, no
heavy container gate alongside either, one Windows VM across containers (orchestrator
hands out the token). Mac compute can overlap the container according to ios-mac's lock
rules, but the controller owns long waits. Each environment skill defines its cleanup.

## What automated green means

- Module `jvmTest` tests JVM behavior. iOS `compileKotlinIosArm64` /
  `compileKotlinIosSimulatorArm64` checks Apple API types, not framework linkage or runtime.
  Use the module's actual tasks and the project gate selection; JSword also has parityTest.
- Robolectric's default TEST_SDK is 33. API-35-only logic needs `@Config(sdk = [35])` and
  a test that fails against the real pre-fix branch; launching a fixture with zero fake
  insets cannot prove inset handling. Construct the relevant state/input explicitly.
- Roborazzi compares composition against inspected goldens on one bundled native Skia.
  Changing sdk does not select Android 11's vs Android 15's rendering engine. It cannot
  certify physical icons/GPU/system bars just because a golden is unchanged.
- A filtered test matching no classes, UP-TO-DATE task or skipped environment probe is not
  a fresh executed pass. Read counts and outputs, distinguish PASS / FAIL / BLOCKED / SKIP.
- A clean welcome screen is launch evidence, not reading, cross-window sync, keyboard,
  IME or persistence evidence. A reused provisioned profile cannot test first run.
- Test the UI variants relevant to the change: dark/light, grayscale monochrome and
  no animations. Do not claim all from one default screenshot.

## Maintainer boundary (#3946)

Read the issue for the live checklist rather than copying it into a new permanent list.
Examples: signed-device Keychain/iCloud/BGTaskScheduler/data protection, actual phone-call
speech interruption/widgets/e-ink/predictive back, actual iOS-on-Mac activation/IME/sheets,
physical Windows Direct3D and CJK IME/Finnish keyboard, physical Linux Wayland/GPU.

Some portions are still automatable (VM DPI, test keyboard layouts, simulator panes).
Verify the container-capable part first and state exactly which real-hardware portion
remains. A physical-device limitation does not excuse skipping the VM or simulator.

## Report contract

A short factual record is sufficient:

```text
Revision: <commit>
Subject: <behavior and native action>
Runtime: <OS/API/engine, display/rendering, fresh or persisted profile>
Observed: <actual input, visible result, natural exit or failure>
Evidence: docs/superpowers/status/<date>-<topic>/<inspected screenshot or log>
Automated gates: <chosen by diff; counts, failures/skips; not just BUILD SUCCESSFUL>
Unverified: <specific #3946 hardware item, or none>
Cleanup: <own guests/processes/profiles stopped/removed; shared inputs retained>
```

Use meaningful paths and separate counts, revisions and units. Preserve evidence in the
private submodule and immediately commit its parent gitlink. Keep application user docs in
`website/content/en/docs/`; these runtime runbooks are developer docs, not website content.
