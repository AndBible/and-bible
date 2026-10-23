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
`Connection closed by 127.0.0.1 port 2224` means the host's relay to the Mac is down or the
`mac-ssh` host port is not forwarded (`jailbee apply` on the host); check `ss -ltnp | grep 2224` here
and ask the user to check the relay service. A timeout on a direct address means the Mac's address
is missing from egress: ask for `jailbee net egress add <mac-ip>:22 --repo`. Never look for the key elsewhere.

## Quick reference

| Goal | Command | Measured |
|------|---------|-----------|
| CI `jsword-ios-tests` job (Native tests on the simulator) | `native-tests` | seconds if nothing changed; 3 min for the four small modules; +4 min when jsword recompiles |
| Only some modules | `native-tests jsword sharedCore` | |
| A real rerun on an unchanged tree (not `UP-TO-DATE`) | `native-tests --force sharedLogic sharedUi` | deletes those modules' remote test-results first |
| A whole checkpoint under one lock and one sync | `batch link native-tests "xcode-test iphone" "xcode-test ipad"` | stops at the first red step |
| What is running / who holds the Mac | `check` | seconds |
| Stop my own forgotten or hung run | `cancel` | seconds |
| CI `ios` job (bibleview-js, xcodegen, XCUITests, screenshots) | `xcode-test iphone` | 9.5 min (first run) |
| One UI test | `xcode-test iphone -only-testing:iosAppUITests/PocUITests/testEdgeSwipeBack` | |
| iPad screenshots (as CI) | `xcode-test ipad -only-testing:iosAppUITests/PocUITests/testScreenshotsAllScenarios` | |
| Any Gradle task | `gradle :sharedUi:linkDebugFrameworkIosSimulatorArm64` | |
| Build, install and launch the app by hand | `xcode-build iphone`, then `sim shot <name>` | 20 s warm |
| Any command in the remote tree (synced first; `run --no-sync` keeps the Mac tree as it is) | `run '<command>'` | |

Results land in `.local/mac/<container>/`: `native-tests/<module>/test-results/…/*.xml`,
`xcode/xcodebuild-<device>.log`, `xcode/app-<device>.log` (the app's unified log during the run),
`xcode/screenshots-<device>/<Test>/<name>.png` (read them with the Read tool), `shots/`, `crashes/`.
The `xcode/…` paths are links to the **latest** run; every run keeps its own evidence in
`runs/<run-id>/xcode/step<N>-<device>/` (logs, `ios-<device>.xcresult`, screenshots), so a later run never
overwrites it (the newest 20 run directories are kept, `MAC_KEEP_RUNS`).

Every lock-taking call also writes a run record `runs/<run-id>/`: `meta` (command, local pid, token),
`output.log` (everything the call printed, including a `heartbeat` line every 2 minutes while a step
runs, with the idle time of the newest remote log), `steps` and, when the call ends (also on timeout,
failure, SIGTERM/INT/HUP), `exit` (code, reason, end time, failing step). No `exit` file and a dead pid
means the call was killed hard (`check` calls that LOST).

## Running

- Steps longer than a few minutes (`native-tests`, `xcode-test`, `xcode-build`, cold Gradle): Bash
  `run_in_background: true` with output redirected to a file, then wait for the completion
  notification. Do not poll, do not sleep. Shorter steps: foreground with `timeout: 600000`.
- **Several containers share the Mac.** One Mac-wide lock (`~/work/.build-lock`) lets only one heavy
  step run at a time (16 GiB RAM, shared with the owner's session). `gradle`, `native-tests`,
  `xcode-test`, `xcode-build`, `run` and the simulator-driving `sim boot|shot|launch|shutdown` take
  it; `sync`, `fetch`, `check` and `sim list|log|sample|crashes` do not. A call that finds the lock
  taken waits, printing the holder every 5 minutes (`MAC_LOCK_WAIT`, default 90 min). Waiting
  behind another container's run is normal: do not `unlock`, do not retry in a loop. When the wait
  message says "held by this container: probably a forgotten run of yours (local pid N)", the holder
  is your own old run: `check` shows it, `cancel` stops it and frees the lock.
- **Chain steps with `batch`, never with `;` or several separate calls.** `batch <step>...` takes the
  lock once, syncs once, runs the steps in order and stops at the first failing step; nobody can
  slip in between two steps and a red step cannot start the next one. Each step is one quoted
  argument: `batch link native-tests "xcode-test iphone -only-testing:iosAppUITests/X" "xcode-test ipad"`
  (`link` = Gradle `:sharedUi:linkDebugFrameworkIosSimulatorArm64`; steps are split on whitespace, no
  inner quoting). The exit code is the failing step's; the output ends with a per-step result list.
- **No dead-lock waits.** The lock records its owner (token, container, pid, process start time). A waiter
  breaks the lock by itself when the holder process is gone (killed, Mac rebooted, pid reused by
  another process); only one waiter wins the break. A holder (or waiter) whose container side
  disappears cleanly (ssh connection closed: local job terminated, session ended) is stopped on the Mac by a
  watchdog within ~10 s (`check` shows its last lines). If the connection merely goes silent (network
  down, or the local script SIGKILLed so its ssh child lingers), the Mac frees the lock by itself after
  `MAC_IDLE_MAX` seconds (default 1800) without the next step or the end of the session, and the
  step's own timeout covers a running step; `cancel` is the fast way for your own run. Only the owner token releases a
  lock, so a finished run never frees another run's lock. Therefore: a waiting Mac call belongs to the
  controller, in the background; a subagent never starts one (it cannot wait that long). `unlock` is
  only for a case `check` shows as stuck (a holder that is alive but wedged for another container);
  for your own runs use `cancel`.
- **`check` and `cancel`.** `check` lists this container's last 5 runs (running with elapsed time and
  `output.log` idle time and the last heartbeat, exited with code and reason, or LOST), then the Mac:
  lock holder with its age and "holder is THIS container (local pid N, run R)" when it is yours, and the
  idle time of the remote `build/*.log` files. `cancel [run-id]` kills this container's running
  mac-remote process trees by pid (never `pkill -f`) and releases the Mac lock only if its owner token
  belongs to those runs (it waits for the Mac-side session to end, then kills it). Run `check` before starting a new Mac run when an earlier one may be alive.
- Between two separate calls another container may use the simulator (e.g. between `xcode-build`
  and `sim shot`). If a screenshot shows something unexpected, repeat the pair.
- **Overlap the container and the Mac: they are different machines.** Mac runs do not count against
  the container's one-Gradle-run rule. The working tree is copied to the Mac when the call starts
  (after the lock is acquired, so a run that waited behind another container still runs the tree as it
  was when it actually started), so the usual pattern is: start the Mac round in the background, then run
  the container gate (verify, jvmTest, assemble) in the foreground at the same time, and read both
  results. Editing files after the call printed `synced` does not affect that Mac run; edits made while
  it waits for the lock are included. `run` syncs too; `run --no-sync` (and `batch --no-sync`) run in the
  Mac tree as it is, e.g. to inspect or rerun what a previous run left there.
- Count results from the fetched XML (classes, tests, failures, skipped), not from
  "BUILD SUCCESSFUL": a filter matching nothing is green too. A test task reported `UP-TO-DATE` did
  not run: its inputs equal an earlier run's, whose XML you are reading. That is a valid result for
  an unchanged module; to force a real rerun (e.g. "three consecutive runs" for a flake check) use
  `native-tests --force <modules>`, which deletes those modules' remote results first. Do not use the
  plain `gradle` step for `iosSimulatorArm64Test` tasks: it boots no simulator (sharedCore/sharedUi
  tests crash with SIGTRAP) and the script warns about it.
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

## Long runs: how the controller waits

A UI class takes 1-25 minutes, a multi-class batch can take over an hour. Waiting badly costs more than the run.

- Start the run as the background command itself (`run_in_background`); the harness notifies on exit. Never
  wrap it in a poll loop, and never `pgrep -f`/`pkill -f` a text that occurs in your own command line (the
  waiter matches itself and hangs forever; `pkill -f` kills your own shell, exit 144).
- **`run` is a bounded, synced step, not a raw shell.** `run <command>` syncs the tree with `--delete` first
  (use `run --no-sync` to keep the Mac tree as it is), is limited by `MAC_TIMEOUT` (default 3000 s; a long
  script such as the I3b `run-on-mac.sh generate` flow may need e.g. `MAC_TIMEOUT=7200`), and runs the
  command with `bash -c` in the tree, so the script's helper functions (`sim_boot`, `sim_udid`) are not
  available in it. On timeout the whole process group of the command is killed (exit 142).
- Every remote step has a built-in wall-clock limit (`MAC_TIMEOUT`: 2400 s for `xcode-test`, 3000 s for the
  rest, per step; a step-level watchdog kills everything the step started, including npm/xcodegen/simulator
  boot, `MAC_STEP_MARGIN` = 600 s after that). A stuck step ends by itself, the exit code is 142 and the run's `exit` marker says
  `timeout`; a `batch` then stops there. For a full UI suite pass a larger `MAC_TIMEOUT` (e.g. 9000).
- Prompt-cache TTL is one hour. If a wait will exceed ~55 minutes, schedule a wake-up no later than minute 55
  (`CronCreate` / `ScheduleWakeup`) so the session never goes cold on a long wait, and use it to check
  `check`, the log's mtime and the result.
- Check on every long job at least every 30 minutes with `scripts/mac-remote.sh check` (state, elapsed,
  `output.log` idle time, last heartbeat, remote log idle time), even when a notification is expected: a run
  whose remote logs have been idle for 30 minutes is suspect, not "still running". If it is hung,
  `cancel` it; do not wait for the timeout.
- Before starting a new Mac run, `check` for an old run of your own (`RUNNING`, or the lock held by
  "THIS container"); `cancel` it first instead of queueing behind it.
