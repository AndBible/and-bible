# Remote Mac for iOS builds

A real Mac shortens the iOS development loop: instead of commit, push from the host, wait for the
GitHub macOS runner (`.github/workflows/ios.yml`) and read the result with `gh`, a jailbee container
syncs its working tree to the Mac over SSH, runs the Gradle / `xcodebuild` steps there and copies the
logs, `.xcresult` bundles and screenshots back.

The Mac gets a dedicated, non-admin user account that exists only for this. Containers reach it with
one SSH key, which jailbee attaches as an optional mount (`mac-ssh`, see `.jailbee/config.yaml`), so
the key is absent from containers until a session asks for it.

```
jailbee container ──ssh/rsync (TCP 22)──▶ Mac, user `andbible`
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

## 4. Jailbee: egress and the mount

Strict containers reach only allowlisted destinations. The Mac's address is specific to your network,
so it is a host-local override, not an `egress_allow` entry in the committed config:

```bash
jailbee net egress add 192.168.1.50:22 --repo    # every and-bible container on this host
```

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
scripts/mac-remote.sh check                  # connection, toolchain, simulators, lock
scripts/mac-remote.sh native-tests           # the CI jsword-ios-tests job
scripts/mac-remote.sh xcode-test iphone      # the CI ios job (XCUITests + screenshots)
scripts/mac-remote.sh sim shot <name>        # screenshot of the booted simulator
```

The sync sends the working tree only, uncommitted changes included: no `.git` (the container's git
objects live in an alternate on the host, and the repository is meant to stay in the container),
no `.local/`, `docs/`, `website/`, build outputs or `node_modules`. Excluded paths survive
`--delete` on the Mac, so its build outputs and macOS-native `node_modules` stay warm. A one-commit
stub repository on the Mac satisfies `:app`'s `git rev-parse` / `git describe`.

Each container gets its own tree under `~/work/<container>`; `~/.gradle` and `~/.konan` on the Mac
are shared by all of them, and the build lock lets only one heavy run go at a time.
