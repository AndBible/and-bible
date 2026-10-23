# Remote Mac for iOS builds

A real Mac shortens the iOS development loop: instead of commit, push from the host, wait for the
GitHub macOS runner (`.github/workflows/ios.yml`) and read the result with `gh`, a jailbee container
syncs its working tree to the Mac over SSH, runs the Gradle / `xcodebuild` steps there and copies the
logs, `.xcresult` bundles and screenshots back.

The Mac gets a dedicated, non-admin user account that exists only for this. Containers reach it with
one SSH key, which jailbee attaches as an optional mount (`mac-ssh`, see `.jailbee/config.yaml`), so
the key is absent from containers until a session asks for it.

```
jailbee container ──ssh/rsync (127.0.0.1:2224, host_ports mac-ssh, via the host's relay)──▶ Mac, user `andbible`
  ~/.ssh-mac/ (optional mount, ro)          ~/work/<branch>/and-bible   (synced tree)
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
scripts/mac-remote.sh xcode-test iphone      # the CI ios job (XCUITests + screenshots)
scripts/mac-remote.sh batch link native-tests "xcode-test iphone" "xcode-test ipad"   # one lock, one sync, stop at first red
scripts/mac-remote.sh sim shot <name>        # screenshot of the booted simulator
```

The sync sends the working tree only, uncommitted changes included: no `.git` (the container's git
objects live in an alternate on the host, and the repository is meant to stay in the container),
no `.local/`, `docs/`, `website/`, build outputs or `node_modules`. Excluded paths survive
`--delete` on the Mac, so its build outputs and macOS-native `node_modules` stay warm. A one-commit
stub repository on the Mac satisfies `:app`'s `git rev-parse` / `git describe`.

Each container gets its own tree under `~/work/<container>`; `~/.gradle` and `~/.konan` on the Mac
are shared by all of them, and the build lock lets only one heavy run go at a time.

How a call runs: one remote `bash -s` session per call. It first waits for the lock
(`~/work/.build-lock`, a directory renamed into place together with its owner record: random token,
container, pid, process start time, local pid, run id), then the tree is rsynced (so a call that waited
behind another container runs the tree as it is when it starts; `run --no-sync` skips this), then the
steps run, each with its own `MAC_TIMEOUT`. `batch` runs several steps in the same session, so the lock
is taken once and nobody gets in between. A waiter breaks the lock when its holder is gone (pid missing,
or alive with another process start time); breakers are serialised and the lock is moved aside with an
atomic rename and its token re-checked, so only one wins. Only the owner token releases a lock. Each
session has a watchdog that stops it (waiting or running) when its own ssh connection ends (remote runs
use a separate, non-multiplexed connection for that reason), so a killed container-side job never leaves
the Mac locked or busy. Watchdog events go to `~/work/.mac-remote-watchdog.log` (shown by `check`).

Container side, per call: `.local/mac/<container>/runs/<run-id>/` with `meta`, `output.log`, `steps` and
an `exit` marker (code, reason `ok|failed|timeout|signal ...|cancelled`, end time, failing step); the
newest 20 run directories are kept. `xcode-test` / `xcode-build` evidence (logs, `.xcresult`,
screenshots) is fetched into `runs/<run-id>/xcode/step<N>-<device>/`, and `.local/mac/<container>/xcode/<name>-<device>`
are links to the latest run. `check` summarises the runs (running / exited N / LOST, elapsed, log idle
time), the lock holder with its age and whether it is this container, and the idle time of the remote
logs; `cancel` stops this container's runs by pid and releases the Mac lock only when the owner token is
theirs. `unlock` removes any lock and is a last resort for a wedged foreign holder.

Tests for the script (fake ssh/rsync, no Mac): `scripts/test-mac-remote.sh`.

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
