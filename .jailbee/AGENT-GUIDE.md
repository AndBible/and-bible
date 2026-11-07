# JailBee container guide for Claude Code

Read this before running builds, tests, adb or git operations in a JailBee container.
Each rule: the instruction, then why. Background and measurements:
`docs/superpowers/reference/jailbee-gradle-background.md` (private submodule; may be absent).

## Gradle

- `GRADLE_OPTS` is preset by the container (check `echo $GRADLE_OPTS`; empty means the container predates the config and needs `jailbee apply` + restart) (2 test forks, 2g fork heap, in-process Kotlin compiler, a daemon that returns heap). Pass `-PtestForks=N` / `-PtestHeap=...` to override for one run; do not pass `-Dorg.gradle.jvmargs`.
  Why: the preset keeps a cold compile plus full verify inside the container memory; `-Dorg.gradle.jvmargs` makes Gradle fork a second daemon.
- Run one Gradle invocation at a time; wait for a running one instead of starting another.
  Why: a second daemon costs about 4 GiB and a minute.
- Never `./gradlew --stop`, never kill Gradle/Kotlin daemons or workers you did not start, never delete `~/.gradle` lock files.
  Why: a stopped daemon kills whoever is building, and a dead owner's lock does not block the next run.
- Do not add `-Dorg.gradle.native=false` or a stale-lock prelude. Older plans and status docs may prescribe either: ignore both unless the matching recovery recipe below applies.
  Why: neither problem reproduces, and mixing flagged and unflagged runs forks a second daemon.
- A gate over 10 min is moved to the background by the harness: wait for the completion notification, do not poll. If Gradle was chained with other commands, read `BUILD SUCCESSFUL|BUILD FAILED` from the log, not the notification's exit code.
  Why: heavy gates take 7-25 min.
- Heavy gates (assemble, full `:app` suite, Roborazzi verify/record) run in the controller session, one at a time. Subagents run only focused `--tests` runs and compiles.
  Why: the subagent prompt cache lasts 5 min, so long waits there are paid uncached.
- Do not run an emulator during a heavy gate.
  Why: the emulator takes about 6 GiB of the shared memory.
- `./gradlew :app:install<Variant>` is safe only while the container's SDK adb (`/opt/android-sdk/platform-tools/adb version`) is the same version as the host's adb server (the user's `adb version` on the host); otherwise use `adb -s <serial> install -r -d <apk>`.
  Why: AGP runs the SDK adb, and an adb client of another version replaces the shared host server, which cannot restart through the port forward (2026-08-28). With both at 37.0.1 (2026-10-23) the Gradle install to a phone worked and the server survived.
- In Claude Code's Bash, plain `grep` skips binary-looking files; use `grep -a` on `/proc/*/cmdline`.
  Why: given a `/proc/<pid>/cmdline` file path directly, a plain grep treats it as binary and silently matches nothing.

### Shared local build cache and real test runs

- `org.gradle.caching=true` reuses task outputs from the default
  `${GRADLE_USER_HOME:-$HOME/.gradle}/caches/build-cache-1`. JailBee shares this home
  between containers; module `build/` directories remain container-local. Do not delete
  shared cache entries, locks, or the cache directory to make a build cold.
- Test tasks (`AbstractTestTask`, including JVM and Kotlin/Native tests) and Roborazzi
  record/verify/compare tasks neither load nor store cached results. They also opt out of
  local `UP-TO-DATE` skipping, so a requested gate really runs its tests even after a
  previous green invocation. This also applies to the separate `kdom` included build.
- To repeat a gate, request it again, for example
  `./gradlew :app:verifyRoborazziStandardGoogleplayDebug`. Compilation can still be
  `UP-TO-DATE` or `FROM-CACHE`; the test task must not be. For a full rebuild of all
  selected dependencies use `--rerun-tasks --no-build-cache` (expensive).
  `--no-build-cache` alone does not disable local `UP-TO-DATE` checks.
- Scoped test runs still overwrite JUnit XML. Do not report their counts as a full gate.
  Use `scripts/test-gradle-build-cache.sh` for the small cache-policy regression test
  (one Gradle invocation at a time; no other build may be running).
- `bash scripts/install-gradle-cache-retention.sh` installs the versioned init script
  into the active Gradle home's `init.d/`, atomically and idempotently. `on_start` runs
  it for new/restarted JailBee containers; run it once manually in existing containers.
  It retains build-cache entries for three unused days, with Gradle's default periodic
  cleanup (normally at most daily, at daemon shutdown). This is age retention, not a
  hard byte quota; monitor disk usage. Do not run manual cache pruning alongside live
  containers. Gradle 9 no longer supports retention in `settings.gradle.kts`.
- Custom Exec/JavaExec/Sync and fixture-generator tasks have not been opted into
  caching. In particular npm/BibleView builds and `generateIosStrings` remain
  non-cacheable: declaring outputs is not proof that environment/toolchain inputs
  are complete. Audit inputs and relocation before adding `@CacheableTask`/`cacheIf`.

### Recovery recipes (only when the failure actually happens)

- Gradle client `SIGSEGV` in `ld-linux`/`libnative-platform` on exit: rerun with `-Dorg.gradle.native=false`, and use it for every run of that session.
  Why: mixing flagged and unflagged runs forks a second daemon.
- `Timeout waiting to lock ... Owner PID <dead>`: first check that no Gradle JVM is alive, then remove only that lock file.
  ```bash
  for p in $(pgrep -x java); do tr '\0' ' ' </proc/$p/cmdline | grep -aqE 'GradleDaemon|KotlinCompileDaemon|GradleWorkerMain' && echo "live: $p"; done
  ```
  Why: a lock held by a live daemon must stay. No `live:` line means the owner is gone.
- Exit 134 with an `app/hs_err_pid*.log`: move the log to `.local/` and rerun once.
  Why: a native SQLite crash is not a test result; a repeat means a background DB job is unowned.

## adb and emulator

- Never `adb kill-server` or `adb start-server`; an empty `adb devices` is a fact to report, not to fix.
  Why: the host's adb server on port 5037 is shared by every container.
- The container emulator uses its own adb server on 5038, only via `scripts/with-container-adb.sh`. Drive the emulator with the `android-emulator` skill.
  Why: that server may be started and killed; the shared one may not.

## Git and GitHub

- Never push from the container; commit locally. No worktrees, no branch switching unless asked (an orchestrator may use the worktrees its `jailbee-orchestration` skill allows).
  Why: the user pushes from the host, and the container is already a per-task sandbox.
- `gh` is read-only; stage outward writes with the jailbee skills (issue management, PR review).
  Why: outward writes are published by a human on the host.

## Files and privileges

- Hand files to the host through `.local/` (shared, gitignored).
- `sudo -n` works for root; `pkexec` is not installed.
- Edits to `.jailbee/config.yaml` (`container.env`, `host_mounts`, `GRADLE_OPTS`) need `jailbee apply` and a container restart on the host; `jailbee new` does not refresh the shared profile. Only `host_ports` applies at create time.
  Why: the config only takes effect after that.

## Test Sword modules

- Before any `:app` test run, run `scripts/check-test-sword-modules.sh`; if modules are missing, extract `.local/testmods.zip` into `~/.sword`.
  Why: without them hundreds of tests fail with "no module installed" symptoms.
