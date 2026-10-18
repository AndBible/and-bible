---
name: ios-mac
description: >
  Use when AndBible iOS work needs real macOS: iosSimulatorArm64Test runs (jsword, kdom, sharedCore,
  sharedData, sharedLogic), linking the iOS framework, xcodebuild / XCUITest runs of iosApp,
  simulator screenshots, an iOS app crash, hang or blank WebView, Swift compilation, or any
  "verify on iOS" step that would otherwise wait for the macOS CI (`ios.yml`, `jsword-ios-tests`).
  Triggers: "aja iOS-testit", "testaa simulaattorilla", "aja macilla", "run the iOS tests",
  "check it on the simulator", "iOS runtime verification".
---

# iOS builds and tests on the remote Mac

A Mac reachable over SSH replaces the macOS CI for iOS verification: the working tree is rsynced
there (uncommitted changes included) and Gradle / xcodebuild run on it. One round takes minutes,
needs no push, and the results come back into the container. Do not wait for a CI run to verify
iOS work; CI still runs on pushes to `ios`, as a late extra signal only.

Everything goes through `scripts/mac-remote.sh` (`--help`-style usage at its top). Setup and the
Mac's configuration: `docs/ios-remote-mac.md`.

## Before the first call

`scripts/mac-remote.sh check`. If it says "no ssh config", the key is not mounted: ask the user
(AskUserQuestion) to run `jailbee mount mac-ssh <container>` on the host, and wait. A connection
timeout means the Mac's address is missing from egress: ask for
`jailbee net egress add <mac-ip>:22 --repo`. Never look for the key elsewhere.

## Quick reference

| Goal | Command | Measured |
|------|---------|-----------|
| CI `jsword-ios-tests` job (Native tests on the simulator) | `native-tests` | seconds if nothing changed; 3 min for the four small modules; +4 min when jsword recompiles |
| Only some modules | `native-tests jsword sharedCore` | |
| CI `ios` job (bibleview-js, xcodegen, XCUITests, screenshots) | `xcode-test iphone` | 9.5 min (first run) |
| One UI test | `xcode-test iphone -only-testing:iosAppUITests/PocUITests/testEdgeSwipeBack` | |
| iPad screenshots (as CI) | `xcode-test ipad -only-testing:iosAppUITests/PocUITests/testScreenshotsAllScenarios` | |
| Any Gradle task | `gradle :sharedUi:linkDebugFrameworkIosSimulatorArm64` | |
| Build, install and launch the app by hand | `xcode-build iphone`, then `sim shot <name>` | 20 s warm |
| Any command in the remote tree | `run '<command>'` | |

Results land in `.local/mac/<container>/`: `native-tests/<module>/test-results/…/*.xml`,
`xcode/xcodebuild-<device>.log`, `xcode/app-<device>.log` (the app's unified log during the run),
`xcode/screenshots-<device>/<Test>/<name>.png` (read them with the Read tool), `shots/`, `crashes/`.

## Running

- Steps longer than a few minutes (`native-tests`, `xcode-test`, `xcode-build`, cold Gradle): Bash
  `run_in_background: true` with output redirected to a file, then wait for the completion
  notification. Do not poll, do not sleep. Shorter steps: foreground with `timeout: 600000`.
- **Several containers share the Mac.** One Mac-wide lock (`~/work/.build-lock`) lets only one heavy
  step run at a time (16 GiB RAM, shared with the owner's session). `gradle`, `native-tests`,
  `xcode-test`, `xcode-build`, `run` and the simulator-driving `sim boot|shot|launch|shutdown` take
  it; `sync`, `fetch`, `check` and `sim list|log|sample|crashes` do not. A call that finds the lock
  taken waits, printing the holder every 5 minutes (`MAC_LOCK_WAIT`, default 90 min). Waiting
  behind another container's run is normal: do not `unlock`, do not retry in a loop.
- **No dead-lock waits.** A waiter breaks the lock by itself when the holder process is gone (killed,
  Mac rebooted). A holder whose container side disappears (local job killed, session ended) is
  stopped on the Mac by a watchdog within ~10 s (`check` shows its last lines), so it frees the lock
  and the RAM. Therefore: a waiting Mac call belongs to the controller, in the background; a subagent
  never starts one (it cannot wait that long). `unlock` is only for a case `check` shows as stuck.
- Between two separate calls another container may use the simulator (e.g. between `xcode-build`
  and `sim shot`). If a screenshot shows something unexpected, repeat the pair.
- **Overlap the container and the Mac: they are different machines.** Mac runs do not count against
  the container's one-Gradle-run rule. The working tree is copied to the Mac when the call starts
  (before any lock wait), so the usual pattern is: start the Mac round in the background, then run
  the container gate (verify, jvmTest, assemble) in the foreground at the same time, and read both
  results. Editing files after the call printed `synced` does not affect that Mac run.
- Count results from the fetched XML (classes, tests, failures, skipped), not from
  "BUILD SUCCESSFUL": a filter matching nothing is green too. A test task reported `UP-TO-DATE` did
  not run: its inputs equal an earlier run's, whose XML you are reading. That is a valid result for
  an unchanged module; to force a rerun use `gradle :<module>:iosSimulatorArm64Test --rerun` (with
  `IOS_TEST_SIMULATOR` needed only for sharedCore, so prefer `native-tests` there).
- `xcode-test` exports only the screenshots the tests attach themselves (mostly
  `testScreenshotsAllScenarios`); a single other test yields none. For visual proof of a state use
  `xcode-build iphone` (it waits for the launch to settle) and then `sim shot <name>`.
- The repo lives only in the container. The Mac gets a plain working tree (no `.git`; a one-commit
  stub repo satisfies `:app`'s `git describe`) and a stub Android SDK in `local.properties`. Keep it
  that way: no git operations on the Mac, no commits needed before a run. When a status line must
  name what was tested, commit first and record the container's hash.

## Debugging on the simulator

| Symptom | Do |
|---------|----|
| App crashed | `sim crashes` → `.local/mac/<container>/crashes/*.ips` (crashed thread, Kotlin/Native frames) |
| App hangs or spins | `sim sample` (5 s stack sample of AndBibleDev; main thread first) |
| What did the app log | `sim log [minutes]`; during XCUITest runs `xcode/app-<device>.log` |
| See the screen now | `sim shot <name>` |
| Reproduce by hand | `xcode-build iphone` (installs + launches), `sim launch`, `sim shot`, `sim log 2` |
| Need something else (lldb, defaults, simctl) | `run '<command>'`, e.g. `run 'xcrun simctl get_app_container booted org.andbible.ios.dev data'` |

A Native-only failure in kdom reproduces faster in the container (`:kdom:linuxX64Test`); everything
using Foundation/UIKit needs the Mac.

## Mac facts that differ from CI

- Xcode 26.6 with the iOS 26.5 runtime (CI: Xcode 26.0.1 on macos-15). `iPhone 16` is created on
  first use; iPads use `iPad Air 11-inch (M4)` (Xcode 26.6 has no M2 type).
- XCUITests run over SSH without a GUI session or Automation Mode.
- 16 GiB RAM shared with the owner's own session: Gradle peaks at ~4.6 GB during the framework link.
  `check` and each lock acquisition print the free memory. Runs of all containers reuse one warm
  Gradle daemon while `gradle.properties` (JVM args) is the same, so `check` should show 1; after the last Mac step of a session:
  `run './gradlew --stop'` and `sim shutdown` (both take the lock, so they never cut another
  container's run short; the next run boots the simulator again).
- Known red on the Mac exactly as on CI: `KeychainSelfTests` -34018 (simulator app unsigned; status
  backlog item), not a setup problem.
- Never install software or change settings on the Mac yourself; the build user is not an admin.
  Missing tool: tell the user what to install (`docs/ios-remote-mac.md`).
