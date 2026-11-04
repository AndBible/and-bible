# Windows VM runbook

Run commands from the repository root. `W` is a shell array, so paths/arguments retain quoting.
Read `.jailbee/AGENT-GUIDE.md` before builds or git. Never print the VM's credentials or keys.

## Preflight and boot-only smoke

Check KVM, tool availability, own state and competing processes before allocating 6 GiB:

```bash
W=(bash scripts/windows-vm.sh)
id=${WINDOWS_VM_ID:-$(hostname)}
vm=${WINDOWS_VM_DIR:-$PWD/.local/windows-vm}
test -r /dev/kvm && test -w /dev/kvm
test -r "$vm/base.qcow2"
command -v qemu-system-x86_64 qemu-img python3
# Filter the process NAME, not arguments (the agent prompt contains these words).
python3 -I - <<'PY'
import subprocess
rows = subprocess.check_output(['ps', '-eo', 'pid,comm,args'], text=True)
for row in rows.splitlines()[1:]:
    fields = row.split(None, 2)
    if len(fields) > 1 and fields[1].startswith(('qemu-system', 'emulator')):
        print(row)
PY
"${W[@]}" status
```

An empty process search is normal (exit 1). If your Android emulator runs, stop it with
`bash scripts/andbible-emu.sh kill`. Never kill another container's processes. `start` refuses
an already running VM in this namespace. SSH defaults to loopback port 2222; if occupied,
choose a free `WINDOWS_VM_SSH_PORT` and export it for **every** command in the session.
Keep `WINDOWS_VM_ID` / `WINDOWS_VM_DIR` consistent too; the default ID is the hostname.
If the base is missing or expired, ask the orchestrator/maintainer for provisioning; do not
start a 90-minute ISO install or change a base with live dependent overlays.

```bash
"${W[@]}" start
# start only waits for QMP. A bounded readiness loop, including SSH connection time:
python3 -I - <<'PY'
import subprocess, time
end = time.monotonic() + 180
while time.monotonic() < end:
    try:
        result = subprocess.run(
            ['bash', 'scripts/windows-vm.sh', 'ssh', 'Write-Output ready'],
            timeout=min(10, max(0.1, end - time.monotonic())),
            stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        if result.returncode == 0:
            print('Windows SSH ready')
            break
    except subprocess.TimeoutExpired:
        pass
    time.sleep(min(2, max(0, end - time.monotonic())))
else:
    raise SystemExit('Windows SSH not ready within 180 seconds')
PY
"${W[@]}" status
"${W[@]}" key shift
"${W[@]}" shot "$id-boot"
```

Open `$vm/shots/$id-boot.png` with Read. A black frame: wake with `key shift` and recapture;
inspect the run's `qemu.log` on failure. If SSH times out and the screenshot remains at
TianoCore / Windows Boot Manager, record an environment-blocked boot (not a successful
Windows runtime test), stop, and ask the orchestrator before changing firmware/CPU/base.
Do not claim an app test from the Windows lock screen. Always execute the cleanup section,
even if boot/readiness fails.

## Log on and install a build

SSH is available at the login screen; a fresh throwaway overlay needs manual console logon.
Capture first; dismiss the lock screen with `key enter` (or `key ctrl+alt+delete` if requested).
Once the password field is visible, pass the credential directly, without printing or tracing:

```bash
set +x
"${W[@]}" type "$(<"$vm/credentials")"
"${W[@]}" key enter
"${W[@]}" ssh 'powercfg /change monitor-timeout-ac 0'
"${W[@]}" shot "$id-desktop"
```

Inspect the screenshot to confirm logon. `type` emits US-ASCII key sequences, not clipboard
text; `win+spc` switches layout if input is wrong. Never capture a revealed password.
Use a test-specific profile; don't overwrite another run's data. For a profile created over
SSH under `C:\Users`, grant `abtest:(OI)(CI)F` using `icacls <dir> /grant ... /T` before GUI use
(the elevated SSH token and UAC-filtered desktop have different access).

Use a known build, not an unrelated cached artifact. For MSI (substitute the real local file):

```bash
"${W[@]}" scp-to /absolute/path/AndBible.msi C:/abtest/AndBible.msi
"${W[@]}" ssh '$p = Start-Process msiexec.exe -ArgumentList "/i C:\abtest\AndBible.msi /qn /norestart /L*v C:\abtest\msi.log" -Wait -PassThru; exit $p.ExitCode'
"${W[@]}" ssh 'Get-ChildItem "C:\Program Files", "C:\Users\abtest\AppData\Local" -Filter AndBible.exe -Recurse -ErrorAction SilentlyContinue | Select-Object -ExpandProperty FullName'
```

Check the MSI exit code (3010 means reboot required) and `C:\abtest\msi.log`. Use the returned
exe path; don't assume installation scope. ZIP alternative, in a fresh destination:

```bash
"${W[@]}" scp-to /absolute/path/AndBible.zip C:/abtest/AndBible.zip
"${W[@]}" ssh 'Expand-Archive C:\abtest\AndBible.zip C:\abtest\skill-app; Get-ChildItem C:\abtest\skill-app -Filter AndBible.exe -Recurse | Select-Object -ExpandProperty FullName'
# Example only: replace with the discovered exe path. cmd.exe syntax, not PowerShell:
"${W[@]}" gui-run '"C:\abtest\skill-app\AndBible\AndBible.exe"'
"${W[@]}" shot "$id-app"
```

`gui-run` prints a unique ID; stdout/stderr and eventual exit code live at
`C:\abtest\gui\<ID>.log` / `.exit`. Fetch relevant logs with `scp-from`. An absent `.exit`
usually means the app is still running, not success. A GUI app launched directly by `ssh`
runs on a noninteractive window station: blank/invisible windows and Skia access violations
there do not diagnose the logged-on desktop.

## Input and display scaling

```bash
"${W[@]}" mouse click 640 400 left
"${W[@]}" mouse wheel 640 400 -3
"${W[@]}" key ctrl+a
"${W[@]}" type 'John 3:16'
"${W[@]}" shot "$id-after-input"
# 120 = 125%, 144 = 150%; use the same user's HKCU:
"${W[@]}" ssh 'Set-ItemProperty "HKCU:\Control Panel\Desktop" LogPixels -Type DWord -Value 144; Set-ItemProperty "HKCU:\Control Panel\Desktop" Win8DpiScaling -Type DWord -Value 1'
"${W[@]}" gui-run 'shutdown /l'
```

Log on again, relaunch and screenshot. Coordinates are **guest screen pixels** from the
latest screenshot; do not multiply them by DPI a second time. Capture after each important
interaction, and verify typed text before submitting. Test relevant dark/light, monochrome
and no-animation variants when the change affects UI; a screenshot alone cannot prove input.

## Building in a persistent dev guest

Stop a throwaway VM first. Reuse only this task's own dev overlay:

```bash
"${W[@]}" dev-start
# Run the bounded SSH readiness loop above again.
"${W[@]}" dev-provision
"${W[@]}" ssh 'Restart-Service sshd'
# Recheck SSH readiness after its restart (the command may lose its own connection).
(cd app/bibleview-js && npm run build-production)
"${W[@]}" push-repo
"${W[@]}" dev-gradle :desktopApp:packageWindowsZip
```

Provisioning installs pinned JDK 17 and Git; restarting sshd makes the new PATH visible.
The guest has no Node: build the BibleView bundle in the container before `push-repo`.
The repo copy is a stub git checkout, not a place to commit. `push-repo --clean` deletes the
guest repo/build outputs; use only when intentional. `dev-gradle` fetches zips and reports to
`$vm/out/$id/` and returns the build's exit code. Cold packaging can take 25 minutes: run it
in the controller's background, wait for notification, and inspect the log/result. Do not
run a second guest build concurrently or infer a pass from a preexisting output file.
After a base replacement, reset and re-provision the dev overlay before reuse.

## Stop and clean (also on failure)

First copy **only meaningful, inspected** screenshots/log excerpts into a dated directory
under `docs/superpowers/status/` and commit them (no credentials, answer media or full disks).
Then, in the same shell namespace:

```bash
"${W[@]}" stop
"${W[@]}" status
"${W[@]}" dev-reset
# Inspect the targets before removal. Never delete other containers' namespaces.
ls -ld "$vm/runs/$id" "$vm/out/$id" 2>/dev/null || true
# Once confirmed these are this task's scratch:
rm -rf -- "$vm/runs/$id" "$vm/out/$id"
rm -f -- "$vm/shots/$id-boot.png" "$vm/shots/$id-desktop.png" \
  "$vm/shots/$id-app.png" "$vm/shots/$id-after-input.png"
```

`stop` terminates QEMU; it does not cleanly shut down Windows or remove overlays/run files.
`dev-reset` removes only this ID's persistent disk and firmware, refusing while running.
Delete any additional shot names you made explicitly. Keep the shared base/firmware, keys,
credentials and other containers' files. A stopped `state/$id/lock` is harmless control state.
Report cleanup and the scope actually tested; physical GPU/hardware checks stay separate
(issue #3946), but ordinary Windows checks do not get handed to the maintainer.
