# Remote Mac for iOS builds

Use the `ios-mac` project skill for remote builds, simulator driving and cleanup. Use
`verifying-on-a-runtime` to choose between iOS simulator, Android (`android-emulator`),
Windows (`windows-vm`) and Linux desktop (`desktop-linux-live`); a simulator does not
prove a real iOS-on-Mac app launch or physical-device/GPU behavior (issue #3946).

A real Mac shortens the iOS development loop: instead of commit, push from the host, wait for the
GitHub macOS runner (`.github/workflows/ios.yml`) and read the result with `gh`, a jailbee container
syncs its working tree to the Mac over SSH, runs the Gradle / `xcodebuild` steps there and copies the
logs, `.xcresult` bundles and screenshots back.

The Mac gets a dedicated, non-admin user account that exists only for this. Containers reach it with
one SSH key, which jailbee attaches as an optional mount (`mac-ssh`, see `.jailbee/config.yaml`), so
the key is absent from containers until a session asks for it.

```
jailbee container ──ssh/rsync (127.0.0.1:2224, host_ports mac-ssh, via the host's relay)──▶ Mac, user `andbible`
  ~/.ssh-mac/ (optional mount, ro)          ~/work/shared/and-bible     (locked warm tree)
                                            Xcode, JDK 17, Node 24, xcodegen (shared, admin-installed)
```

Names used below (change them consistently if you pick others):

| What | Value |
|---|---|
| Mac build user | `andbible` |
| Mac LAN address | `192.168.1.50` (example; give the Mac a DHCP reservation so it stays fixed) |
| Host directory holding the key | `~/.config/andbible-mac/` |
| Mount inside containers | `/home/dev/.ssh-mac/` (`jailbee mount mac-ssh <container>`) |
| SSH host alias | `andbible-mac` |

## 1. Mac: the build user

As your own (admin) user on the Mac:

```bash
# Standard (non-admin) account; prompts for its password.
sudo sysadminctl -addUser andbible -fullName "AndBible build" -password -

# Developer tools without authorization prompts (simulator, debugging, Instruments).
sudo DevToolsSecurity -enable
sudo dseditgroup -o edit -a andbible -t user _developer
```

Enable SSH: *System Settings → General → Sharing → Remote Login* on, and under the (i) button set
*Allow access for* to *Only these users* with `andbible` (plus yourself, if you use SSH too).

Then restrict SSH for this user to keys only. macOS's `sshd_config` includes `sshd_config.d/*`:

```bash
sudo tee /etc/ssh/sshd_config.d/100-andbible.conf >/dev/null <<'EOF'
Match User andbible
    PasswordAuthentication no
    KbdInteractiveAuthentication no
    AllowAgentForwarding no
    X11Forwarding no
EOF
sudo sshd -t && sudo launchctl kickstart -k system/com.openssh.sshd
```

Keep the Mac awake while it serves builds (*System Settings → Energy → Prevent automatic sleeping when
the display is off*, or `sudo pmset -a sleep 0`).

## 2. Mac: toolchain

Shared tools are installed once by the admin user; the build user only reads them.

**Xcode.** Use the same version as CI (`Xcode_26.0.1` in `.github/workflows/ios.yml`). Install it as
`/Applications/Xcode_26.0.1.app` (e.g. from developer.apple.com/download or with `xcodes`), so it can
sit beside whatever Xcode you use yourself. Then:

```bash
sudo xcodebuild -license accept
sudo DEVELOPER_DIR=/Applications/Xcode_26.0.1.app/Contents/Developer xcodebuild -runFirstLaunch
DEVELOPER_DIR=/Applications/Xcode_26.0.1.app/Contents/Developer xcodebuild -downloadPlatform iOS
```

**Homebrew packages** (as the admin user who owns `/opt/homebrew`):

```bash
brew install xcodegen rsync node@24
brew install --cask temurin@17
```

`temurin@17` goes to `/Library/Java/JavaVirtualMachines`, so `/usr/libexec/java_home -v 17` finds it
for every user. `rsync` from Homebrew matters: macOS ships `openrsync` as `/usr/bin/rsync`, which does
not support every option of the GNU rsync in the container.

**Build user environment.** SSH commands run a non-interactive, non-login zsh, which reads only
`~/.zshenv`. Log in as `andbible` once (`su - andbible` or a GUI login) and create it:

```bash
cat > ~/.zshenv <<'EOF'
export DEVELOPER_DIR=/Applications/Xcode_26.0.1.app/Contents/Developer
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
export PATH="/opt/homebrew/opt/node@24/bin:/opt/homebrew/bin:$PATH"
export LANG=en_US.UTF-8
EOF
mkdir -p ~/work ~/.ssh && chmod 700 ~/.ssh
```

`DEVELOPER_DIR` selects Xcode for this user only, so the global `xcode-select` stays yours.

**Simulators.** Simulator devices are per user, and the default set follows the Xcode version (Xcode
26.6 creates iPhone 17 / iPad Air 11-inch (M4) and no `iPad Air 11-inch (M2)` device type at all). CI
uses `iPhone 16` and `iPad Air 11-inch (M2)`; check what the build user has, and create a missing
`iPhone 16` if a script expects the CI name:

```bash
xcrun simctl list devices available | grep -E "iPhone 16 |iPad Air 11-inch \(M2\)"
xcrun simctl list runtimes            # pick the iOS runtime id for the next line
xcrun simctl create "iPhone 16" "iPhone 16" <runtime-id>
```

**UI tests need no GUI session.** XCUITests (`iosAppUITests`) ran over SSH with the build user never
logged in to the GUI and Automation Mode disabled (macOS 26.7, Xcode 26.6, 2026-10-18). If a future
macOS starts refusing with "Timed out while enabling automation mode", log the build user in once
through the login window and run `sudo automationmodetool enable-automationmode-without-authentication`.

**No Android SDK needed.** Gradle configures `:app` too, which wants an SDK location; the sync script
points `local.properties` at an empty stub directory, which is enough for the iOS tasks.

## 3. Host: the key, SSH config and known_hosts

Create these on the **host**, never in a container and never in `.local/` (which every container
sees):

```bash
mkdir -p ~/.config/andbible-mac && chmod 700 ~/.config/andbible-mac
cd ~/.config/andbible-mac
ssh-keygen -t ed25519 -N '' -C andbible-jailbee -f id_ed25519
```

Install the public key on the Mac as the `andbible` user. The `from=` option limits the key to the
jailbee host: container traffic leaves through the host's NAT, so the Mac sees the host's LAN address
(replace `192.168.1.10`; drop `from=` if the host's address is not stable):

```bash
# on the Mac, as andbible; paste the content of id_ed25519.pub after the options
echo 'from="192.168.1.10",no-agent-forwarding,no-X11-forwarding ssh-ed25519 AAAA... andbible-jailbee' >> ~/.ssh/authorized_keys
chmod 600 ~/.ssh/authorized_keys
```

Pin the Mac's host key. Fetch it on the host and compare the fingerprint with the one the Mac prints
(`ssh-keygen -lf /etc/ssh/ssh_host_ed25519_key.pub` on the Mac):

```bash
ssh-keyscan -t ed25519 192.168.1.50 2>/dev/null | grep -v '^#' | sed 's/^[^ ]*/andbible-mac/' > known_hosts
ssh-keygen -lf known_hosts
```

The entry is stored under the alias `andbible-mac` (`HostKeyAlias` below), so a new Mac address only
needs the config and the egress entry changed, not `known_hosts`.

Write the SSH config that containers use (the paths are the container-side mount paths):

```bash
cat > config <<'EOF'
Host andbible-mac
    HostName 192.168.1.50
    User andbible
    IdentityFile /home/dev/.ssh-mac/id_ed25519
    IdentitiesOnly yes
    HostKeyAlias andbible-mac
    UserKnownHostsFile /home/dev/.ssh-mac/known_hosts
    StrictHostKeyChecking yes
    ServerAliveInterval 30
    ControlMaster auto
    ControlPath /tmp/ssh-andbible-mac-%C
    ControlPersist 10m
EOF
chmod 600 id_ed25519 config
```

`ControlMaster` reuses one connection for the many short `ssh`/`rsync` calls of a build loop. The
socket is in the container's own `/tmp`, not in a shared cache directory.

Test from the host:

```bash
ssh -i ~/.config/andbible-mac/id_ed25519 -o IdentitiesOnly=yes \
    -o HostKeyAlias=andbible-mac -o UserKnownHostsFile=~/.config/andbible-mac/known_hosts \
    andbible@192.168.1.50 'xcodebuild -version && java -version && node --version && xcodegen --version && rsync --version | head -1'
```

If the key is refused (`Permission denied (publickey)` while `ssh -v` shows the key offered), the
reason is only in the Mac's log. Watch it while retrying:
`sudo log stream --predicate 'process == "sshd-session" OR process == "sshd"' --level debug`.
Usual causes: the user is missing from the Remote Login list, `authorized_keys` lacks the key, or
`~`, `~/.ssh` or `authorized_keys` is group/world writable.

## 4. Jailbee: the port forward and the mount

The host and the Mac can be on different networks, so containers do not connect to the Mac's
address. The host keeps a relay up (a systemd user service running `ssh <relay>` with
`LocalForward 2224 localhost:2224` on the host, and `RemoteForward 2224 localhost:22` on the Mac's
own tunnel to the same relay). The committed `host_ports` entry `mac-ssh` forwards container
`127.0.0.1:2224` to host `127.0.0.1:2224`, and the ssh config in the mount points at
`HostName 127.0.0.1`, `Port 2224`. Apply it to an existing container with `jailbee apply`.

If the Mac and the host share a network, a direct connection also works: put the Mac's address in
the ssh config and allow it host-locally (it is specific to your network, so not an `egress_allow`
entry in the committed config): `jailbee net egress add 192.168.1.50:22 --repo`.

The `mac-ssh` optional mount is declared in `.jailbee/config.yaml`. Attach it for a session and detach
it afterwards:

```bash
jailbee mount mac-ssh <container>
jailbee unmount mac-ssh <container>
```

`rsync` is in the golden image's `extra_apt_packages`; a golden image built before that change needs a
rebuild (or `sudo apt-get install rsync` in the container).

## 5. Use from a container

`scripts/mac-remote.sh` does the sync, the remote run under a Mac-wide build lock and the fetch of
results into `.local/mac/<container>/`; the agent-facing guide is the `ios-mac` skill
(`.claude/skills/ios-mac/SKILL.md`).

```bash
scripts/mac-remote.sh check                  # this container's runs, then connection, toolchain, simulators, lock
scripts/mac-remote.sh cancel                 # stop this container's own running runs, release their Mac lock
scripts/mac-remote.sh native-tests           # the CI jsword-ios-tests job (--force: delete old results first)
scripts/mac-remote.sh xcode-test iphone --smoke   # the UI smoke set (iosApp/iosAppUITests/smoke-tests.txt), as CI runs it
MAC_TIMEOUT=5400 scripts/mac-remote.sh xcode-test iphone --parallel 2   # the full UI suite in 2 simulator clones (batch ends)
scripts/mac-remote.sh batch link native-tests "xcode-test iphone" "xcode-test ipad"   # one lock, one sync, stop at first red
scripts/mac-remote.sh batch --keep-going "xcode-test iphone -only-testing:iosAppUITests/AppUITests" "xcode-test iphone -only-testing:iosAppUITests/PocUITests"   # every step runs; exit = first red
scripts/mac-remote.sh sim shot <name>        # screenshot of the booted simulator
scripts/mac-remote.sh cleanup                # end of every Mac session: simulators off, own leftovers killed, own junk removed
```

`reset-tree` takes the execution lock and clears regenerable outputs of the selected tree: project
`build/`, `.gradle/`, `.kotlin/` and `node_modules`. It deliberately makes the next build cold. It never
clears external Xcode `ModuleCache.noindex`/`SDKStatCaches.noindex`. Use it for a demonstrably broken
warm state, not routinely after a run. Safe explicit workdirs have the form `work/<name>`; nested paths,
symlink aliases and custom `GRADLE_USER_HOME` are rejected rather than bypassing ownership/cache guards.

A shared `run --no-sync`, `batch --no-sync` or `fetch` requires this client to own the last completed sync;
otherwise sync first. The owner is invalidated before mutation and published only after successful sync
and setup. Run metadata records the source revision, dirty-content fingerprint and selected tree.

The sync sends the working tree only, uncommitted changes included: no `.git` (the container's git
objects live in an alternate on the host, and the repository is meant to stay in the container),
no `.local/`, `docs/`, `website/`, build outputs or `node_modules`. Excluded paths survive
`--delete` on the Mac, so its build outputs and macOS-native `node_modules` stay warm. A one-commit
stub repository on the Mac satisfies `:app`'s `git rev-parse` / `git describe`.

New clients use one warm tree, `~/work/shared/and-bible`, under the existing execution lock. Project-local
Gradle/Kotlin outputs, macOS `node_modules` and Xcode DerivedData keep the same path when another container
syncs its revision, so only changed inputs rebuild. `MAC_OWN_TREE=1` opts into the old private
`~/work/<container>/and-bible` tree; an explicit safe `MAC_WORKDIR` still selects a private work directory.
`~/.gradle` and `~/.konan` remain shared. Source sync (including standalone `sync`) is now locked too.

**Mixed deployment:** old scripts must retain their default private tree. Never point an old script at
`MAC_WORKDIR=work/shared`: its standalone sync does not take the lock. New clients still take the exact
`~/work/.build-lock` understood by old clients, so neither can execute through the other's live lock.
FIFO fairness applies between new queued clients only; old clients do not know about tickets and can
still win a release/poll race. Update all clients to obtain full FIFO fairness.

**One slot, deliberately.** The 16 GiB Mac must accommodate an 8 GiB Release Kotlin/Native compiler plus
Gradle and the owner's session. `MAC_SLOTS` defaults to 1 and other values are rejected, not silently
accepted. Parallel slots require retiring old clients (they shut down all simulators), separate warm
trees, explicit simulator UDIDs for every operation including shutdown, and measured memory admission.
No parallel light slot is enabled by this change.

How a call runs: one remote `bash -s` session per call. It registers a FIFO ticket on the Mac and
waits until that ticket is eligible, then takes the existing lock
(`~/work/.build-lock`, a directory renamed into place together with its owner record: random token,
container, pid, process start time, local pid, run id), then the tree is rsynced (so a call that waited
behind another container runs the tree as it is when it starts; `run --no-sync` skips this), then the
steps run, each with its own `MAC_TIMEOUT`. `batch` runs several steps in the same session, so the lock
is taken once and nobody gets in between. A waiter breaks the lock when its holder is gone (pid missing,
or alive with another process start time); breakers are serialised and the lock is moved aside with an
atomic rename and its token re-checked, so only one wins. Only the owner token releases a lock. Each
session has a watchdog that stops it (waiting or running) when its own ssh connection ends (remote runs
use a separate, non-multiplexed connection for that reason). A connection that only goes silent (network
down; a SIGKILLed local script whose ssh child lingers) is covered by an idle guard: the Mac releases the lock
and ends the session when no next step arrives within `MAC_IDLE_MAX` (default 1800 s; it must exceed the
longest instruction gap). Active sync/evidence transfers disarm that idle timer while retaining the
execution lock and use a separate bounded lease, `MAC_TRANSFER_TIMEOUT` (default 1800 seconds per
operation). Each step runs from a script file in its own process group under
`MAC_TIMEOUT` plus a step-level watchdog (`MAC_STEP_MARGIN`, default 600 s), so a timeout kills every
process the step started. ssh and rsync use `ServerAliveInterval=30`. Process start times are compared
under `LC_ALL=C TZ=UTC0`. Watchdog events go to `~/work/.mac-remote-watchdog.log` (shown by `check`).

Container side, per call: `.local/mac/<container>/runs/<run-id>/` with `meta`, `output.log`, `steps` and
an `exit` marker (code, reason `ok|failed|timeout|signal ...|cancelled|no lock|sync failed`, end time, failing step; timeouts are always
exit 142; `no lock` = the lock wait ran out (75) or the connection failed, `sync failed` = rsync/ssh to the Mac
failed before any step ran; the generated script of each step is kept as `step-<n>.sh`); the
newest 20 run directories are kept (not overwritten is not permanent retention).
Native XML/HTML/status lives in `runs/<run-id>/native-tests/step<N>/<module>/`; legacy
`native-tests/<module>` links to the latest capture. `run` snapshots only new/changed top-level
`build/release` and `build/shots` artifacts under `runs/<run-id>/run/step<N>/` before releasing the lock.
Missing optional artifacts are distinct from a failed transfer; capture failure fails an otherwise
successful step. `xcode-test` / `xcode-build` evidence (logs, `.xcresult`,
screenshots) is fetched into `runs/<run-id>/xcode/step<N>-<device>/`, and `.local/mac/<container>/xcode/<name>-<device>`
are links to the latest run. `check` summarises the runs (running / exited N / LOST, elapsed, log idle
time), the lock holder with its age and whether it is this container, and the idle time of the remote
logs; `cancel` stops this container's runs by pid and releases the Mac lock only when the owner token is
theirs. `unlock` removes any lock and is a last resort for a wedged foreign holder. Never use it to
move ahead in the queue or shorten a normal wait; use orchestrator-owned queue controls instead.

`run [--no-sync] <command>` syncs with `--delete`, is bounded by `MAC_TIMEOUT` (default 3000 s) and runs in
`bash -c` in the tree (the script's `sim_boot` helpers are not available); raise `MAC_TIMEOUT` for long
scripts. Update `scripts/mac-remote.sh` in every container before relying on the new lock: containers
running the old script only partly understand the new owner record (they never break a new-style lock,
and they do not honour the idle guard or the step process groups).

### Clean up after use

The Mac has 16 GiB of RAM and a data volume that fills up; it is shared, so the rule is the same as for
`.local/` (maintainer, 2026-11-04): **release session-owned runtime/scratch resources after use**.
The shared warm compilation tree, node_modules and reusable build caches intentionally remain; they are
shared tool inputs, not abandoned session scratch.

- `gradle`, `native-tests`, `xcode-test`, `xcode-build`, `run` and `batch` shut down all simulators when their locked
  session ends, on success and on failure (also when the session is killed: the Mac-side exit trap does it), and print
  one line saying what was shut down. `MAC_KEEP_SIM=1` (or `--keep-sim` right after the command name) keeps them.
- The `sim` helpers (`boot`, `shot`, `launch`, `log`, ...) leave the simulator booted, because they are a sequence of calls.
  End the sequence with `sim shutdown` or, better, `cleanup`.
- **Every Mac session ends with `scripts/mac-remote.sh cleanup`** (a child includes it before `done`). It takes the lock,
  shuts down simulators and prints memory/disk. In a shared tree it preserves warm compilation, node_modules, Gradle caches
  and daemons. Only successfully captured release/shots scratch with a matching owner/content receipt is removed;
  changed/foreign scratch and shared-tree processes are report-only. `--derived-data` explicitly removes tree-local
  `build/dd-*`, never Xcode's global `ModuleCache.noindex` or `SDKStatCaches.noindex`. A private tree retains its previous
  cleanup policy: kill own leftovers, report foreign leftovers, prune old release archives (newest kept), remove shots,
  and optionally remove its own DerivedData.
- `check` ends with a "leftovers" section and a disk report: free space, the size and last modification of every
  `~/work/<tree>`, and CoreSimulator, DerivedData, `~/.konan` and `~/.gradle`. It warns above 85 % full.
- `prune [--trees <name...>] [--older-than <days>] [--sims] [--yes]` deletes **other** containers' trees (and their DerivedData) and,
  with `--sims`, simulator devices other than the script's iPhone/iPad (plus `simctl delete unavailable`). It is a dry run listing
  sizes unless `--yes` is given, and never touches this container's tree, the lock holder or a tree with running processes. A
  container cannot know which others are alive: only the maintainer decides. Above 85 % full the orchestrator asks the maintainer
  and passes the names; a child never runs `prune --yes`.

Tests for the script (fake ssh/rsync, no Mac): `scripts/test-mac-remote.sh`.

### FIFO queue and orchestrator controls

```bash
scripts/mac-remote.sh queue                         # all waiting tickets, in admission order
scripts/mac-remote.sh queue priority <ticket> 10    # higher priority first, then FIFO
scripts/mac-remote.sh queue reserve 120             # bounded reservation, prints its ticket
scripts/mac-remote.sh queue release <ticket>        # release that reservation
```

Queue tickets live outside the source tree in `~/work/.mac-queue`; sequence numbers remain monotonic
when the queue empties. Dead waiters and reused PIDs are removed using PID plus process start time.
Timeout/cancellation removes the ticket. Reservations expire 1-3600 seconds after creation, not after
reaching the head. A reservation occupies its queue position until released or expired; it does not
preempt the current holder and legacy clients cannot see it.

Everyone may inspect. Priority/reservation controls require the orchestrator role and matching group;
an orchestrator may control only its own children's tickets. Children derive the group from
`JAILBEE_ORCHESTRATOR`, while an orchestrator uses its container identity. This is cooperative safety
on a shared SSH account, not a security boundary. Equal priority is FIFO. Corrupt queue metadata fails
closed instead of silently dropping another client's ticket.

### Mac build cache and forced tests

Mac runs enable Gradle's local build cache without changing the repository's Linux defaults (tracked
separately in #3957). The cache configuration is also inherited by Gradle launched from Xcode's
framework build phase. Test task outputs are excluded from the cache: cached compilation is useful,
but a test report from another device/session is not proof that this session ran tests.

A normal unchanged test task may still be `UP-TO-DATE`; this is distinct from `FROM-CACHE`.
`native-tests --force <modules>` forces the selected test tasks to execute while keeping compilation
incremental. Read and count the captured XML, and retain its run ID with any verification claim.
Never use `--rerun-tasks` merely to force tests: that also reruns expensive compilation.

### Debug and Release builds

By default the script builds and launches the Debug identity (`org.andbible.ios.dev`, product `AndBibleDev`). The Release configuration (`org.andbible.ios`, product `AndBible`) is built with `scripts/mac-remote.sh run 'cd iosApp && xcodegen generate && xcodebuild ... -configuration Release ... build'`; set `MAC_APP_ID` and `MAC_APP_PRODUCT` (for example `MAC_APP_ID=org.andbible.ios MAC_APP_PRODUCT=AndBible`) so `sim launch`, `sim log`, `sim sample` and `sim crashes` follow it. `xcode-build` and `xcode-test` build the Debug identity and ignore these overrides, unless `--release` follows the device (`xcode-test iphone --release [args]`, `xcode-build iphone --release`): then they build the Release configuration, stream and launch the Release app, and uninstall `org.andbible.ios` from the simulator first, so every such run is a fresh install (Release ignores `-resetData` and the other debug launch arguments). `OnboardingUITests` is the Release fresh-install onboarding check. Set `MAC_RELEASE_KEEP_INSTALL=1` to skip that uninstall, e.g. to run the test over old Swift app data seeded into the Release container (recipe: `docs/superpowers/status/2026-11-02-ios-release-onboarding-loop.md`). Every sync first generates the gitignored `iosApp/Config/Version.xcconfig` (`MARKETING_VERSION`, from `version.properties`, and `AND_BIBLE_COMMIT`, from the full git revision) locally with `scripts/ios-version-xcconfig.sh`, so it travels with the tree and `xcodegen` on the Mac picks it up through `Base.xcconfig`. Builds without git metadata leave `AND_BIBLE_COMMIT` blank; set that environment variable explicitly only when a known revision should be supplied. `run --no-sync` and `batch --no-sync` skip the sync and therefore do not regenerate `Version.xcconfig`.

### UI tests that need simulator fixtures

- `FilesUITests.testImageSelectionCopiesExpectedProviderAsset` picks one known photo in the real PHPicker.
  Add the fixture once per simulator and pass its picker label (the label carries the import time, so
  read it from the picker's element tree, e.g. a run with a placeholder label attaches it):

  ```bash
  scripts/mac-remote.sh run 'python3 -I iosApp/scripts/generate-files-test-fixtures.py --output-dir build/files-fixtures &&
      xcrun simctl addmedia booted build/files-fixtures/andbible-port-quadrants.png'
  TEST_RUNNER_FILES_TEST_PHOTO_LABEL='Photo, October 21, 0.23' scripts/mac-remote.sh xcode-test iphone
  ```

  Without the variable the test fails at once with "Controller must provide the deterministic fixture
  asset's exact Photos label".
- The real-app tests reset what they need through debug launch arguments (`-resetData`, `-resetDatabases`,
  `-task6ResetCrashState`; `IosDebugLaunchArgs.kt`). XCTest kills the app without a background transition,
  which the crash-loop gate counts as a crash: a UI test class that launches the app without
  `-task6ResetCrashState` lands on the "An error has occurred" recovery screen after two such kills in an hour.
- Device settings persist across test classes on one simulator: `FeatureUITests` switches the UI to Finnish, and
  `-AppleLanguages (en)` does not override the app's own `locale_pref`. A test that reads English labels launches
  with `-standardTestFixture` (English, no disguise).
- Opt-in, controller-prepared classes skip in an ordinary run: `SyncAcceptanceUITests`
  (`TEST_RUNNER_TASK7_SYNC_ACCEPTANCE=1` plus a fixture) and the two `DiscreteUITests` unlock scenarios
  (`TEST_RUNNER_TASK6_DISCRETE_ACCEPTANCE=1`, after persisting `discrete_mode=true` on the simulator).
