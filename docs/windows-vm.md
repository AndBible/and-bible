# Windows test VM

This developer VM supports Windows desktop testing, not TPM-dependent feature testing. It uses a disposable local `abtest` administrator account; no Microsoft account or Java installation is required. AndBible supplies its own JRE. The Windows Enterprise evaluation is time-limited (nominally 90 days; the reported remaining days change with time). Check `status` before relying on an old image.

## Prerequisites

- x86-64 Linux with working KVM access, QEMU (`qemu-system-x86_64`, `qemu-img`), and OVMF: `/usr/share/OVMF/OVMF_CODE_4M.secboot.fd` and `/usr/share/OVMF/OVMF_VARS_4M.ms.fd`.
- `xorriso`, 7-Zip (`7z`), Python 3 with Pillow (`python3-pil`), `curl`, OpenSSL, and OpenSSH client tools (`ssh`, `scp`, `ssh-keygen`), plus `flock` and `sha256sum`.
- A user-provided Windows 11 Enterprise Evaluation ISO at `.local/win.iso`, or set `WINDOWS_VM_ISO`. Wait until the copy has remained the same size for at least 120 seconds before installing. The supplied ISO was UDF-only: xorriso exposed only a README requiring UDF, so `7z l .local/win.iso` was used to validate `sources/boot.wim` and `sources/install.wim`. The installer validates these paths (also accepting `install.esd`) with 7z; it does not enforce the stability wait or verify a full ISO hash. xorriso creates the separate answer ISO.
- Default allocation is 6 GiB RAM and four vCPUs. Do not run this VM alongside the Android emulator, which also uses roughly 6 GiB.

## Install and use

From the repository root:

```sh
scripts/windows-vm.sh install
scripts/windows-vm.sh start
scripts/windows-vm.sh status
scripts/windows-vm.sh ssh 'echo ok'
scripts/windows-vm.sh scp-to /path/to/local-file 'C:/abtest/file'
scripts/windows-vm.sh scp-from 'C:/abtest/file' /path/to/local-copy
scripts/windows-vm.sh shot desktop
scripts/windows-vm.sh key ctrl+alt+delete
scripts/windows-vm.sh type 'printable ASCII text'
scripts/windows-vm.sh key enter
scripts/windows-vm.sh stop
# persistent dev overlay (JDK, repo copy, Gradle caches survive stop)
scripts/windows-vm.sh dev-start
scripts/windows-vm.sh dev-provision   # once per dev overlay: JDK 17 and Git for Windows
scripts/windows-vm.sh push-repo [--clean] [dir]
scripts/windows-vm.sh dev-gradle :desktopApp:packageWindowsZip
scripts/windows-vm.sh dev-reset
```

`start` returns when QMP is ready, not when Windows or SSH has finished booting. Retry SSH with a bounded timeout before using the guest; a successful `start` alone does not establish SSH readiness.

`install` refuses to overwrite an existing base or firmware, waits for guest readiness, requests a clean Windows shutdown and promotes the image only after QEMU exits successfully. It downloads the official Win32-OpenSSH `10.4.0.0p1-Preview` MSI from GitHub and verifies SHA-256 `788d8643464a01eb63640a2fd746f9ed23d106b5a901a35f88dc8cd40e446ac9`. The guest installs this preview locally from the answer media, without a Windows Update dependency. Internet access to the release is needed at installation time. Guest diagnostics are `C:\abtest\firstlogon.log` and `C:\abtest\openssh-install.log`.

Each `start` creates a fresh ephemeral overlay and a firmware-variable copy; changes made in a run do not persist to the base. `stop` terminates QEMU rather than cleanly shutting down the guest: use it only knowing that the overlay is disposable. Initial base promotion, by contrast, requires clean shutdown. A run's files remain under `runs/`; do not assume `stop` removes them.

### Dev overlay

`dev-start` boots from a persistent per-container overlay `dev-<id>.qcow2` (plus `dev-<id>.vars` firmware variables) in the VM directory, created on top of the base on first use and reused afterwards. Use it for the guest JDK (Temurin 17; the script sets `JAVA_HOME` from `WINDOWS_VM_JAVA_HOME`, default `C:\Program Files\Eclipse Adoptium\jdk-17.0.20.1-hotspot`), the repo copy in `C:\abtest\src` and the Gradle caches. The same per-container lock applies: only one VM, throwaway or dev, runs at a time. `stop` never deletes the dev overlay; only `dev-reset` does, and it refuses while the VM runs. The plain `start` stays the throwaway overlay for clean test runs.

`push-repo` tars the working tree (tracked and untracked-not-ignored files, the `jsword` and `kdom` submodules, and the gitignored `app/bibleview-js/dist/` bundle; never `.git`, `build/`, `.gradle/`, `node_modules/` or `.local/`) and unpacks it over `C:\abtest\src`, keeping guest build outputs; `--clean` removes the guest tree first. `dev-gradle` runs `gradlew.bat` with the given arguments (PowerShell-quoted), returns Gradle's exit code, and always fetches `*.zip` files from `desktopApp\build\compose\binaries` and the `test-results` and `reports` directories into `.local/windows-vm/out/<id>/` (reports under `build/`).

`dev-provision` copies `scripts/windows-vm/dev-provision.ps1` into the guest and runs it; it is idempotent. It installs, each pinned by version and SHA-256: Temurin JDK `17.0.20.1+1` (MSI `OpenJDK17U-jdk_x64_windows_hotspot_17.0.20.1_1.msi`, SHA-256 `a6013774c5e4a34951e11c1997d8b4641d2de7736d3eac839231f4266bd1116f`) with only its main feature, so no `java` lands on the PATH and no `JAVA_HOME` is set (the app must run on its bundled runtime); and Git for Windows `2.56.0.windows.4` (SHA-256 `4961a4a8cc778d8b6e00a5819e5fe4d078b754555aff7aedc2d77a5d74c2de80`) with its Unix tools on the PATH, because `:app`'s configuration runs `git` and `:desktopApp:prepareBibleViewBundle` runs `sh`. Restart `sshd` (or the VM) after the first provisioning so SSH sessions see the new PATH. The guest reaches Maven Central, Google and GitHub directly through QEMU user networking.

The guest copy is not a git checkout: `push-repo` gives it a stub repository with one empty commit (for `:app`'s `git rev-parse`) and a `local.properties` naming an empty `C:\abtest\android-sdk-stub` (AGP wants an SDK location while configuring `:app`, but no Android task runs). Build the BibleView bundle in the container (`cd app/bibleview-js && npm run build-production`) before `push-repo`; the guest has no Node, and `copy-bibleview.sh` only copies an up-to-date `dist/`. A cold `:desktopApp:packageWindowsZip` in the guest took about 25 minutes (4 vCPUs), an incremental one 5 minutes.

GUI testing requires a manual `abtest` logon once per fresh overlay: automatic logon is disabled after provisioning. SSH is available at the login screen. If the screenshot is black, wake the display with a key before capturing it. QMP `type` sends printable US-ASCII key sequences, not clipboard text; the active guest keyboard layout affects the resulting characters. `key` sends QEMU key combinations such as `ctrl+alt+delete`.

## Files and configuration

The default shared directory is `.local/windows-vm` (`WINDOWS_VM_DIR`); treat it as sensitive. `base.qcow2` and `base.qcow2.vars` are promoted read-only. Each container ID gets its own state and lock in `state/$WINDOWS_VM_ID` and per-run overlays, firmware copies and logs in `runs/$WINDOWS_VM_ID`. A shared `install.lock` serializes installation. Screenshots go to `shots/<name>.png`; installer screenshots are linked from `install-shots`. Credentials in `credentials` and the private SSH key `id_ed25519` are mode 600; the public key and `known_hosts` are alongside them. Answer files and answer ISO also contain sensitive material; do not publish them or print the password. SSH forwards only to `127.0.0.1` and accepts a new host key on first use.

Overrides (defaults shown):

| Variable | Default | Purpose |
| --- | --- | --- |
| `WINDOWS_VM_DIR` | `.local/windows-vm` | Shared image and state directory |
| `WINDOWS_VM_BASE` | `$WINDOWS_VM_DIR/base.qcow2` | Base image; firmware is `<base>.vars` |
| `WINDOWS_VM_ISO` | `.local/win.iso` | Installation media |
| `WINDOWS_VM_ID` | hostname | Per-container state/run namespace; letters, digits, `.`, `_`, `-` only |
| `WINDOWS_VM_SSH_PORT` | `2222` | Loopback SSH forwarding port; choose a distinct port for concurrent VMs |
| `WINDOWS_VM_USER` | `abtest` | SSH user |
| `WINDOWS_VM_RAM` / `WINDOWS_VM_CPUS` | `6G` / `4` | QEMU memory and vCPUs |
| `WINDOWS_VM_DISK_SIZE` | `80G` | New installer disk size |
| `WINDOWS_VM_INSTALL_TIMEOUT` | `5400` | Installer deadline in seconds |
| `WINDOWS_VM_START_POLLS` | `50` | QMP startup polls, 0.1 seconds each |
| `WINDOWS_VM_QMP_TIMEOUT` | `3` | QMP timeout in seconds |
| `QEMU` | `qemu-system-x86_64` | QEMU executable |

## Limits and rebuilding

No TPM or swtpm is used: the orchestrator explicitly authorized this override after the enforced AppArmor profile blocked the QEMU-to-swtpm `SCM_RIGHTS` descriptor transfer. No AppArmor policy was changed. Windows Setup uses LabConfig bypasses for TPM, Secure Boot and RAM checks, while QEMU still uses Secure Boot OVMF. Both install and runtime enable Hyper-V enlightenments (`hv-relaxed`, `hv-vapic`, `hv-spinlocks=0x1fff`, `hv-time`, `hv-vpindex`, `hv-synic`, `hv-stimer`, `hv-frequencies`). This configuration cannot validate TPM-dependent behavior.

Windows Update is paused for 35 days, feature updates deferred for 365 days, and active hours set to 08:00–23:00; this is not a permanent update block. Evaluation expiry is likewise not prevented. After expiry, coordinate with every container using the shared directory and stop its VMs and installers. Leave the old VM directory and its base at their original paths as a private archive: overlays can reference the base by absolute path, so moving the directory can break them. Do not blindly delete it; it contains credentials, host keys, logs and potentially valuable overlays. Choose a new unique directory under shared `.local` and consistently set `WINDOWS_VM_DIR` to that path for the fresh install and every subsequent command. If `WINDOWS_VM_BASE` was set, unset it or set it to the new directory's base as well. Verify the new base and SSH access before retiring the old directory. If a move is unavoidable, an offline `qemu-img rebase` with the correct backing path is required for each affected overlay; preserve the original files until the rebased images are verified.
