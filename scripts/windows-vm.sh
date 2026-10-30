#!/usr/bin/env bash
set -euo pipefail

ROOT=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
VM_DIR=$(realpath -m -- "${WINDOWS_VM_DIR:-$ROOT/.local/windows-vm}")
BASE=$(realpath -m -- "${WINDOWS_VM_BASE:-$VM_DIR/base.qcow2}")
RAM=${WINDOWS_VM_RAM:-6G}
CPUS=${WINDOWS_VM_CPUS:-4}
CPU_MODEL=host,hv-relaxed,hv-vapic,hv-spinlocks=0x1fff,hv-time,hv-vpindex,hv-synic,hv-stimer,hv-frequencies
OPENSSH_URL=https://github.com/PowerShell/Win32-OpenSSH/releases/download/10.4.0.0p1-Preview/OpenSSH-Win64-v10.4.0.0.msi
OPENSSH_SHA256=788d8643464a01eb63640a2fd746f9ed23d106b5a901a35f88dc8cd40e446ac9
SSH_PORT=${WINDOWS_VM_SSH_PORT:-2222}
VM_ID=${WINDOWS_VM_ID:-$(hostname)}
[[ "$VM_ID" =~ ^[a-zA-Z0-9._-]+$ ]] || { printf 'windows-vm: invalid container id: %s\n' "$VM_ID" >&2; exit 2; }
STATE="$VM_DIR/state/$VM_ID"
RUNS="$VM_DIR/runs/$VM_ID"
QEMU=${QEMU:-qemu-system-x86_64}

usage() {
  printf '%s\n' 'Usage: windows-vm.sh {install|start|stop|status|ssh [cmd]|scp-to src dst|scp-from src dst|shot name|type text|key combo|dev-start|dev-reset|push-repo [--clean] [dir]|dev-provision|dev-gradle args...}' >&2
  exit 2
}
error() { printf 'windows-vm: %s\n' "$*" >&2; exit 1; }

qmp() {
  python3 - "$1" "$2" <<'PY'
import json, socket, sys
path, command = sys.argv[1:]
import time
deadline = time.monotonic() + float(__import__('os').environ.get('WINDOWS_VM_QMP_TIMEOUT', '3'))
s = socket.socket(socket.AF_UNIX); s.settimeout(max(0, deadline-time.monotonic())); s.connect(path)
pending = b""
def read_line():
    global pending
    while True:
        remaining = deadline - time.monotonic()
        if remaining <= 0: raise TimeoutError("QMP response deadline exceeded")
        if b"\n" in pending:
            line, pending = pending.split(b"\n", 1)
            return line
        # Recompute the remaining budget for every chunk, not every complete line.
        s.settimeout(remaining)
        try: chunk = s.recv(4096)
        except socket.timeout: raise TimeoutError("QMP response deadline exceeded") from None
        if not chunk: raise RuntimeError("QMP connection closed")
        pending += chunk
def receive(expected):
    while True:
        response = json.loads(read_line())
        if "event" in response: continue
        if response.get("id") != expected: continue
        if "error" in response: raise RuntimeError(str(response["error"]))
        return response
read_line()
s.sendall(b'{"execute":"qmp_capabilities","id":"capabilities"}\r\n')
receive("capabilities")
request = json.loads(command); request["id"] = "command"
s.sendall(json.dumps(request).encode() + b"\r\n")
print(json.dumps(receive("command").get("return", {})))
s.close()
PY
}

process_identity() {
  python3 - "$1" <<'PY'
import sys
try:
    data = open(f"/proc/{sys.argv[1]}/stat").read()
    fields = data[data.rfind(")") + 2:].split()
    print(fields[0], fields[19])
except (OSError, IndexError):
    raise SystemExit(1)
PY
}
owned_alive() {
  [[ -r "$1" && -r "$2" ]] || return 1
  local pid expected actual state
  pid=$(<"$1"); expected=$(<"$2"); actual=$(process_identity "$pid" || true)
  state=${actual%% *}; actual=${actual#* }
  [[ "$state" != Z && -n "$actual" && "$actual" == "${expected#* }" ]]
}
terminate_owned() {
  local pidfile=$1 startfile=$2 pid
  owned_alive "$pidfile" "$startfile" || return 0
  pid=$(<"$pidfile"); kill "$pid" 2>/dev/null || true
  for _ in {1..50}; do owned_alive "$pidfile" "$startfile" || return 0; sleep .1; done
  if owned_alive "$pidfile" "$startfile"; then
    kill -KILL "$pid" 2>/dev/null || true
    for _ in {1..20}; do owned_alive "$pidfile" "$startfile" || return 0; sleep .05; done
    owned_alive "$pidfile" "$startfile" && return 1
  fi
  return 0
}

create_qmp_path() {
  local run_dir=$1 dir
  dir=$(mktemp -d "${TMPDIR:-/tmp}/abwin-qmp.XXXXXXXX")
  if (( ${#dir} + 5 >= 108 )); then rmdir "$dir"; dir=$(mktemp -d /tmp/abwin-qmp.XXXXXXXX); fi
  printf '%s/qmp\n' "$dir" > "$run_dir/qmp-path"
}
qmp_path() {
  if [[ -r "$1/qmp-path" ]]; then printf '%s\n' "$(<"$1/qmp-path")"; else printf '%s/qmp.sock\n' "$1"; fi
}
cleanup_qmp_path() {
  local run_dir=$1 path
  [[ -r "$run_dir/qmp-path" ]] || return 0
  path=$(<"$run_dir/qmp-path")
  case "$path" in /tmp/abwin-qmp.*/qmp|"${TMPDIR:-/tmp}"/abwin-qmp.*/qmp) rm -f -- "$path"; rmdir -- "${path%/qmp}" 2>/dev/null || true ;; esac
  rm -f "$run_dir/qmp-path"
}

# start_vm [dev]: without an argument the disk is a throwaway overlay in the run dir;
# with "dev" it is the persistent per-container overlay dev-$VM_ID.qcow2 (+ .vars).
start_vm() {
  local mode=${1:-throwaway}
  mkdir -p "$VM_DIR" "$RUNS" "$STATE"
  exec 9>"$STATE/lock"
  flock 9
  [[ -f "$STATE/qemu.pid" ]] && owned_alive "$STATE/qemu.pid" "$STATE/qemu.start" && error 'VM is already running'
  [[ -f "$STATE/qemu.pid" ]] && stop_locked
  local code=/usr/share/OVMF/OVMF_CODE_4M.secboot.fd vars=/usr/share/OVMF/OVMF_VARS_4M.ms.fd
  [[ -r "$code" ]] || error "OVMF secure boot firmware not found: $code (install ovmf)"
  [[ -r "$vars" ]] || error "OVMF variables template not found: $vars (install ovmf)"
  [[ -f "$BASE" ]] || error "base disk not found: $BASE (install/import a Windows base image first)"
  command -v "$QEMU" >/dev/null || error "QEMU executable not found: $QEMU"
  command -v qemu-img >/dev/null || error 'qemu-img executable not found'
  local id run_dir
  id="$(date -u +%Y%m%dT%H%M%S).$$.$RANDOM"
  mkdir -p "$RUNS"
  run_dir="$RUNS/$id"
  mkdir -p "$run_dir"
  local disk_file="$run_dir/overlay.qcow2" vars_file="$run_dir/OVMF_VARS.ms.fd"
  if [[ "$mode" == dev ]]; then
    disk_file=$(dev_overlay); vars_file=$(dev_vars)
  fi
  if [[ ! -e "$vars_file" ]]; then
    cp "${BASE}.vars" "$vars_file" 2>/dev/null || cp "$vars" "$vars_file"
    chmod u+w "$vars_file"
  fi
  if [[ ! -e "$disk_file" ]]; then
    qemu-img create -f qcow2 -F qcow2 -b "$BASE" "$disk_file" >/dev/null
  fi
  touch "$run_dir/lock"
  create_qmp_path "$run_dir"
  local qmp_sock; qmp_sock=$(qmp_path "$run_dir")
  "$QEMU" -name windows-vm -machine q35,smm=on -cpu "$CPU_MODEL" -enable-kvm -m "$RAM" -smp "$CPUS" \
    -drive "if=pflash,format=raw,readonly=on,file=$code" \
    -drive "if=pflash,format=raw,file=$vars_file" \
    -device ahci,id=sata0 -drive "file=$disk_file,if=none,id=disk0,format=qcow2" \
    -device ide-hd,drive=disk0,bus=sata0.0 -netdev user,id=net0,hostfwd="tcp:127.0.0.1:${SSH_PORT}-:22" \
    -device e1000e,netdev=net0 -display none -serial mon:stdio \
    -qmp "unix:$qmp_sock,server=on,wait=off" >"$run_dir/qemu.log" 2>&1 8>&- 9>&- &
  local qpid=$!
  printf '%s\n' "$qpid" > "$STATE/qemu.pid"
  printf '%s\n' "$run_dir" > "$STATE/run-dir"
  for _ in {1..20}; do
    process_identity "$qpid" > "$STATE/qemu.start" 2>/dev/null && break
    sleep .01
  done
  [[ -s "$STATE/qemu.start" ]] || { kill "$qpid" 2>/dev/null || true; rm -f "$STATE/qemu.pid" "$STATE/run-dir"; error 'could not record QEMU process identity'; }
  touch "$run_dir/lock"
  for ((i=0; i<${WINDOWS_VM_START_POLLS:-50}; i++)); do
    owned_alive "$STATE/qemu.pid" "$STATE/qemu.start" || { stop_locked; error 'QEMU exited during startup'; }
    [[ -S "$qmp_sock" ]] && { flock -u 9; echo "Windows VM started (run $id)"; return; }
    sleep .1
  done
  stop_locked
  flock -u 9
  error 'timed out waiting for QEMU QMP socket'
}

dev_overlay() { printf '%s/dev-%s.qcow2\n' "$VM_DIR" "$VM_ID"; }
dev_vars() { printf '%s/dev-%s.vars\n' "$VM_DIR" "$VM_ID"; }

# dev-reset: the only way the persistent dev overlay goes away.
dev_reset() {
  mkdir -p "$STATE"
  exec 9>"$STATE/lock"
  flock 9
  owned_alive "$STATE/qemu.pid" "$STATE/qemu.start" && error 'VM is running; stop it before dev-reset'
  rm -f -- "$(dev_overlay)" "$(dev_vars)"
  echo 'Dev overlay removed'
}

stop_locked() {
  local qemu_alive=false
  owned_alive "$STATE/qemu.pid" "$STATE/qemu.start" && qemu_alive=true
  local run_dir=''
  [[ -r "$STATE/run-dir" ]] && run_dir=$(<"$STATE/run-dir")
  local run_lock_open=false
  if [[ -n "$run_dir" && -e "$run_dir/lock" ]]; then
    exec 8>"$run_dir/lock"
    flock -n 8 || error 'VM run is busy; refusing to stop during another operation'
    run_lock_open=true
  fi
  terminate_owned "$STATE/qemu.pid" "$STATE/qemu.start" || error 'could not terminate owned QEMU process'
  [[ -z "$run_dir" ]] || cleanup_qmp_path "$run_dir"
  rm -f "$STATE/qemu.pid" "$STATE/qemu.start" "$STATE/run-dir"
  if "$run_lock_open" == true; then exec 8>&-; fi
  if "$qemu_alive" == true; then echo 'Windows VM stopped'; else echo 'Cleaned stale VM state'; fi
}
stop_vm() {
  mkdir -p "$STATE"
  exec 9>"$STATE/lock"
  flock 9
  stop_locked
}

ssh_options() {
  SSH_OPTS=(-i "$VM_DIR/id_ed25519" -o BatchMode=yes -o ConnectTimeout=5 -o StrictHostKeyChecking=accept-new -o "UserKnownHostsFile=${1:-$VM_DIR/known_hosts}")
}

guest_target() { printf '%s@127.0.0.1' "${WINDOWS_VM_USER:-abtest}"; }
# Single-quote one PowerShell argument, doubling embedded single quotes.
ps_quote() { local a=${1//\'/\'\'}; printf "'%s'" "$a"; }
guest_ssh() { ssh_options; ssh "${SSH_OPTS[@]}" -p "$SSH_PORT" "$(guest_target)" "$@"; }
guest_scp_to() { ssh_options; scp "${SSH_OPTS[@]}" -P "$SSH_PORT" "$1" "$(guest_target):$2"; }
guest_scp_from() { ssh_options; scp "${SSH_OPTS[@]}" -P "$SSH_PORT" "$(guest_target):$1" "$2"; }

# push-repo [--clean] [dir]: tar the working tree (tracked + untracked, not ignored; plus the
# jsword and kdom submodules and the gitignored BibleView bundle) and unpack it into C:\abtest\src.
push_repo() {
  local clean=false dir
  if [[ ${1:-} == --clean ]]; then clean=true; shift; fi
  dir=$(cd "${1:-$ROOT}" && pwd) || error 'no such directory'
  local out="$VM_DIR/out/$VM_ID" list
  mkdir -p "$out"
  list=$(mktemp "$out/files.XXXXXX")
  {
    emit_files "$dir" ''
    local sub
    for sub in jsword kdom; do
      [[ -e "$dir/$sub/.git" ]] && emit_files "$dir/$sub" "$sub/"
    done
    if [[ -d "$dir/app/bibleview-js/dist" ]]; then
      (cd "$dir" && find app/bibleview-js/dist -type f -print0)
    fi
  } > "$list"
  tar -C "$dir" --null -T "$list" -cf "$out/src.tar" || { rm -f "$list" "$out/src.tar"; error 'could not build tarball'; }
  rm -f "$list"
  # :app's configuration runs `git rev-parse/describe/log`: the guest copy gets a stub repository with one empty commit.
  # AGP's configuration needs an SDK location, but no Android task runs there: local.properties names an empty stub dir.
  local rc=0 prep='New-Item -ItemType Directory -Force C:\abtest\src | Out-Null; tar.exe -xf C:\abtest\src.tar -C C:\abtest\src; if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }; if (-not (Test-Path C:\abtest\src\.git)) { git -C C:\abtest\src init -q; git -C C:\abtest\src -c user.name=abtest -c user.email=abtest@localhost commit -q --allow-empty -m "windows-vm push-repo" }; if (-not (Test-Path C:\abtest\src\local.properties)) { New-Item -ItemType Directory -Force C:\abtest\android-sdk-stub | Out-Null; Set-Content -Encoding ascii C:\abtest\src\local.properties "sdk.dir=C:/abtest/android-sdk-stub" }; exit $LASTEXITCODE'
  if $clean; then prep='Remove-Item -Recurse -Force C:\abtest\src -ErrorAction SilentlyContinue; '"$prep"; fi
  guest_scp_to "$out/src.tar" 'C:/abtest/src.tar' && guest_ssh "$prep" || rc=$?
  rm -f "$out/src.tar"
  return "$rc"
}
# emit_files repo prefix: NUL-separated existing non-directory files of a git work tree,
# minus .git, build, .gradle, node_modules and .local paths.
emit_files() {
  local repo=$1 prefix=$2 f
  git -C "$repo" ls-files -co --exclude-standard -z | while IFS= read -r -d '' f; do
    [[ -e "$repo/$f" && ! -d "$repo/$f" ]] || continue
    case "/$prefix$f" in */.git/*|*/build/*|*/.gradle/*|*/node_modules/*|*/.local/*) continue ;; esac
    printf '%s\0' "$prefix$f"
  done
}

# dev-gradle args...: run gradlew.bat in the guest, then fetch artifacts and reports.
dev_gradle() {
  local out="$VM_DIR/out/$VM_ID" jh=${WINDOWS_VM_JAVA_HOME:-'C:\Program Files\Eclipse Adoptium\jdk-17.0.20.1-hotspot'} args='' a rc=0
  mkdir -p "$out/build"
  for a in "$@"; do args+=" $(ps_quote "$a")"; done
  guest_ssh "\$env:JAVA_HOME=$(ps_quote "$jh"); cd C:\\abtest\\src; .\\gradlew.bat$args; exit \$LASTEXITCODE" || rc=$?
  local zips z
  zips=$(guest_ssh 'Get-ChildItem -Recurse -Filter *.zip C:\abtest\src\desktopApp\build\compose\binaries -ErrorAction SilentlyContinue | ForEach-Object { $_.FullName }' 2>/dev/null || true)
  while IFS= read -r z; do
    z=${z%$'\r'}; [[ -n "$z" ]] || continue
    guest_scp_from "${z//\\//}" "$out/" || printf 'windows-vm: could not fetch %s\n' "$z" >&2
  done <<<"$zips"
  if guest_ssh 'Remove-Item C:\abtest\results.tar -ErrorAction SilentlyContinue; $d = @("test-results","reports") | Where-Object { Test-Path "C:\abtest\src\desktopApp\build\$_" }; if (-not $d) { exit 1 }; tar.exe -cf C:\abtest\results.tar -C C:\abtest\src\desktopApp\build @d; exit $LASTEXITCODE' >/dev/null 2>&1; then
    if guest_scp_from 'C:/abtest/results.tar' "$out/results.tar"; then
      tar -xf "$out/results.tar" -C "$out/build" || true
    fi
    rm -f "$out/results.tar"
  fi
  return "$rc"
}

install_screenshot() {
  local socket=$1 png=$2 raw="${2%.png}.ppm"
  qmp "$socket" "{\"execute\":\"screendump\",\"arguments\":{\"filename\":\"$raw\"}}" >/dev/null 2>&1 || return 1
  python3 - "$raw" "$png" <<'PYSHOT'
from PIL import Image
import sys
Image.open(sys.argv[1]).save(sys.argv[2])
PYSHOT
  local result=$?
  rm -f "$raw"
  return "$result"
}

install_timeout_failure() {
  local reason=$1
  if ! WINDOWS_VM_QMP_TIMEOUT=3 install_screenshot "$qmp_sock" "$run_dir/install-shots/final.png"; then
    printf 'windows-vm: final screenshot capture failed\n' >&2
  fi
  ln -sfn "$run_dir/install-shots" "$VM_DIR/install-shots.new.$$"
  mv -Tf "$VM_DIR/install-shots.new.$$" "$VM_DIR/install-shots"
  if [[ -f "$run_dir/install-shots/final.png" ]]; then
    error "$reason; final screenshot: $VM_DIR/install-shots/final.png"
  fi
  error "$reason; final screenshot unavailable; screenshots: $run_dir/install-shots"
}

install_vm() {
  local deadline=$((SECONDS + ${WINDOWS_VM_INSTALL_TIMEOUT:-5400}))
  umask 077
  mkdir -p "$VM_DIR" "$STATE" "$RUNS"
  chmod 700 "$VM_DIR"
  exec 7>"$VM_DIR/install.lock"; flock -n 7 || error 'another install is running'
  exec 9>"$STATE/lock"; flock -n 9 || error 'VM state is busy'
  [[ ! -e "$BASE" && ! -e "${BASE}.vars" ]] || error 'base image or firmware already exists; refusing to overwrite'
  owned_alive "$STATE/qemu.pid" "$STATE/qemu.start" && error 'VM is already running'
  local iso=${WINDOWS_VM_ISO:-$ROOT/.local/win.iso} listing run_dir qpid qmp_sock next_shot now boot_deadline remaining shot_count=0
  [[ -r "$iso" && -s "$iso" ]] || error "Windows ISO not readable: $iso"
  command -v 7z >/dev/null || error '7z is required to validate Windows media'
  listing=$(7z l "$iso" 2>/dev/null) || error 'cannot read Windows ISO'
  [[ "${listing,,}" == *'sources/install.wim'* || "${listing,,}" == *'sources/install.esd'* ]] || error 'Windows ISO has no installation image'
  [[ "${listing,,}" == *'sources/boot.wim'* ]] || error 'Windows ISO has no boot image'
  command -v xorriso >/dev/null || error 'xorriso is required to create the answer ISO'
  command -v ssh-keygen >/dev/null || error 'ssh-keygen is required'
  if [[ ! -e "$VM_DIR/credentials" ]]; then
    if [[ -e "$VM_DIR/password" ]]; then cp "$VM_DIR/password" "$VM_DIR/credentials"; else openssl rand -hex 24 > "$VM_DIR/credentials"; fi
  fi
  chmod 600 "$VM_DIR/credentials"
  if [[ ! -e "$VM_DIR/id_ed25519" && ! -e "$VM_DIR/id_ed25519.pub" ]]; then ssh-keygen -q -t ed25519 -N '' -f "$VM_DIR/id_ed25519"; fi
  [[ -s "$VM_DIR/id_ed25519" && -s "$VM_DIR/id_ed25519.pub" ]] || error 'incomplete SSH key pair'
  chmod 600 "$VM_DIR/id_ed25519"
  run_dir="$RUNS/install-$(date -u +%Y%m%dT%H%M%S).$$.$RANDOM"
  mkdir -p "$run_dir/answer" "$run_dir/install-shots"
  python3 - "$ROOT/scripts/windows-vm/autounattend.xml" "$run_dir/answer/autounattend.xml" "$VM_DIR/credentials" <<'PYINSTALL'
import pathlib, sys, xml.sax.saxutils
source, dest, password = map(pathlib.Path, sys.argv[1:])
dest.write_text(source.read_text().replace('@@PASSWORD@@', xml.sax.saxutils.escape(password.read_text().strip())))
PYINSTALL
  cp "$ROOT/scripts/windows-vm/firstlogon.ps1" "$run_dir/answer/firstlogon.ps1"
  cp "$VM_DIR/id_ed25519.pub" "$run_dir/answer/authorized_key.pub"
  curl -fsSL --connect-timeout 15 --max-time 180 -o "$run_dir/answer/openssh.msi" "$OPENSSH_URL" || error 'failed to download pinned OpenSSH MSI'
  printf '%s  %s\n' "$OPENSSH_SHA256" "$run_dir/answer/openssh.msi" | sha256sum -c --status || error 'OpenSSH MSI SHA256 mismatch'
  xorriso -as mkisofs -quiet -J -iso-level 3 -V ABANSWER -o "$run_dir/answer.iso" "$run_dir/answer" 2>"$run_dir/iso.log" || error 'could not create answer ISO'
  chmod 600 "$run_dir/answer.iso" "$run_dir/answer/"*
  cp /usr/share/OVMF/OVMF_VARS_4M.ms.fd "$run_dir/OVMF_VARS.ms.fd"
  qemu-img create -f qcow2 "$run_dir/install.qcow2" "${WINDOWS_VM_DISK_SIZE:-80G}" >"$run_dir/qemu-img.log"
  touch "$run_dir/lock"
  create_qmp_path "$run_dir"; qmp_sock=$(qmp_path "$run_dir")
  "$QEMU" -name windows-vm-install -machine q35,smm=on -cpu "$CPU_MODEL" -enable-kvm -m "$RAM" -smp "$CPUS" \
    -drive if=pflash,format=raw,readonly=on,file=/usr/share/OVMF/OVMF_CODE_4M.secboot.fd \
    -drive "if=pflash,format=raw,file=$run_dir/OVMF_VARS.ms.fd" \
    -device ahci,id=sata0 -drive "file=$run_dir/install.qcow2,if=none,id=disk0,format=qcow2" -device ide-hd,drive=disk0,bus=sata0.0 \
    -drive "file=$iso,if=none,id=cd0,media=cdrom,readonly=on" -device ide-cd,drive=cd0,bus=sata0.1,bootindex=1 \
    -drive "file=$run_dir/answer.iso,if=none,id=cd1,media=cdrom,readonly=on" -device ide-cd,drive=cd1,bus=sata0.2 \
    -netdev user,id=net0,hostfwd="tcp:127.0.0.1:${SSH_PORT}-:22" -device e1000e,netdev=net0 \
    -display none -serial mon:stdio -qmp "unix:$qmp_sock,server=on,wait=off" >"$run_dir/qemu.log" 2>&1 7>&- 9>&- &
  qpid=$!
  printf '%s\n' "$qpid" > "$STATE/qemu.pid"; printf '%s\n' "$run_dir" > "$STATE/run-dir"
  for _ in {1..20}; do process_identity "$qpid" > "$STATE/qemu.start" 2>/dev/null && break; sleep .01; done
  [[ -s "$STATE/qemu.start" ]] || { kill "$qpid" 2>/dev/null || true; error 'could not record QEMU process identity'; }
  trap 'terminate_owned "$STATE/qemu.pid" "$STATE/qemu.start" || true; cleanup_qmp_path "$run_dir"; rm -f "$STATE/qemu.pid" "$STATE/qemu.start" "$STATE/run-dir"' EXIT
  ssh_options "$run_dir/known_hosts"
  next_shot=$SECONDS; boot_deadline=$((SECONDS + 30))
  printf 'Installer running; logs and screenshots: %s\n' "$run_dir"
  while (( SECONDS < deadline )); do
    owned_alive "$STATE/qemu.pid" "$STATE/qemu.start" || error "installer QEMU exited; see $run_dir/qemu.log"
    now=$SECONDS
    if (( now >= next_shot )) && [[ -S "$qmp_sock" ]]; then
      shot_count=$((shot_count+1))
      remaining=$((deadline - SECONDS)); (( remaining > 0 )) || break
      WINDOWS_VM_QMP_TIMEOUT=$remaining install_screenshot "$qmp_sock" "$run_dir/install-shots/$(printf '%04d' "$shot_count").png" || true
      next_shot=$((now + 300))
    fi
    remaining=$((deadline - SECONDS)); (( remaining > 0 )) || break
    if (( now < boot_deadline )) && [[ -S "$qmp_sock" ]]; then WINDOWS_VM_QMP_TIMEOUT=$remaining "$ROOT/scripts/windows-vm.sh" key spc >/dev/null 2>&1 || true; fi
    remaining=$((deadline - SECONDS)); (( remaining > 0 )) || break
    (( remaining > 10 )) && remaining=10
    if timeout "${remaining}s" ssh "${SSH_OPTS[@]}" -p "$SSH_PORT" "${WINDOWS_VM_USER:-abtest}@127.0.0.1" 'if (Test-Path C:\abtest\ready.txt) { exit 0 } else { exit 1 }' >/dev/null 2>&1; then
      remaining=$((deadline - SECONDS)); (( remaining > 0 )) || install_timeout_failure 'install deadline exceeded before shutdown'
      if timeout "${remaining}s" ssh "${SSH_OPTS[@]}" -p "$SSH_PORT" "${WINDOWS_VM_USER:-abtest}@127.0.0.1" 'shutdown.exe /s /t 0' >/dev/null 2>&1; then
        :
      else
        local shutdown_status=$?
        if (( shutdown_status == 124 || SECONDS >= deadline )); then install_timeout_failure 'shutdown request timed out'; fi
        error 'failed to request clean shutdown'
      fi
      for _ in {1..120}; do owned_alive "$STATE/qemu.pid" "$STATE/qemu.start" || break; (( SECONDS < deadline )) || break; sleep 1; done
      if owned_alive "$STATE/qemu.pid" "$STATE/qemu.start"; then install_timeout_failure 'clean shutdown timed out; base not promoted'; fi
      wait "$qpid" || error 'QEMU exited unsuccessfully; base not promoted'
      [[ -s "$run_dir/known_hosts" ]] || error 'SSH host key was not recorded'
      cp "$run_dir/known_hosts" "$VM_DIR/known_hosts.new.$$"
      chmod 600 "$VM_DIR/known_hosts.new.$$"
      chmod a-w "$run_dir/install.qcow2" "$run_dir/OVMF_VARS.ms.fd"
      [[ ! -e "$BASE" && ! -e "${BASE}.vars" ]] || error 'base appeared during install; refusing promotion'
      ln "$run_dir/OVMF_VARS.ms.fd" "${BASE}.vars" || error 'could not promote firmware'
      ln "$run_dir/install.qcow2" "$BASE" || { rm -f "${BASE}.vars"; error 'could not promote base'; }
      mv -f "$VM_DIR/known_hosts.new.$$" "$VM_DIR/known_hosts"
      if (( shot_count > 0 )); then cp "$run_dir/install-shots/$(printf '%04d' "$shot_count").png" "$run_dir/install-shots/final.png" 2>/dev/null || true; fi
      ln -sfn "${run_dir}/install-shots" "$VM_DIR/install-shots.new.$$"
      mv -Tf "$VM_DIR/install-shots.new.$$" "$VM_DIR/install-shots"
      printf 'Windows base installed: %s\n' "$BASE"
      cleanup_qmp_path "$run_dir"
      rm -f "$STATE/qemu.pid" "$STATE/qemu.start" "$STATE/run-dir"
      trap - EXIT
      return
    fi
    remaining=$((deadline - SECONDS)); (( remaining > 0 )) || break
    (( remaining > 5 )) && remaining=5
    sleep "$remaining"
  done
  install_timeout_failure 'install timed out'
}

[[ $# -gt 0 ]] || usage
cmd=$1; shift
case "$cmd" in
  install) install_vm ;;
  start) start_vm ;;
  dev-start) start_vm dev ;;
  dev-reset) dev_reset ;;
  push-repo) push_repo "$@" ;;
  dev-gradle) dev_gradle "$@" ;;
  dev-provision) guest_scp_to "$ROOT/scripts/windows-vm/dev-provision.ps1" C:/abtest/dev-provision.ps1 && guest_ssh 'powershell -NoProfile -ExecutionPolicy Bypass -File C:\abtest\dev-provision.ps1' ;;
  stop) stop_vm ;;
  status) if owned_alive "$STATE/qemu.pid" "$STATE/qemu.start"; then echo "running (PID $(<"$STATE/qemu.pid"))"; ssh_options; ssh "${SSH_OPTS[@]}" -p "$SSH_PORT" "${WINDOWS_VM_USER:-abtest}@127.0.0.1" 'cscript.exe //Nologo C:\Windows\System32\slmgr.vbs /dlv' || echo 'Evaluation status unavailable over SSH'; else echo 'stopped (evaluation status unavailable until running)'; fi ;;
  ssh) ssh_options; exec ssh "${SSH_OPTS[@]}" -p "$SSH_PORT" "${WINDOWS_VM_USER:-abtest}@127.0.0.1" "$@" ;;
  scp-to) [[ $# == 2 ]] || usage; ssh_options; exec scp "${SSH_OPTS[@]}" -P "$SSH_PORT" "$1" "${WINDOWS_VM_USER:-abtest}@127.0.0.1:$2" ;;
  scp-from) [[ $# == 2 ]] || usage; ssh_options; exec scp "${SSH_OPTS[@]}" -P "$SSH_PORT" "${WINDOWS_VM_USER:-abtest}@127.0.0.1:$1" "$2" ;;
  shot) [[ $# == 1 ]] || usage; mkdir -p "$VM_DIR/shots"; run_dir=$(<"$STATE/run-dir"); qmp_sock=$(qmp_path "$run_dir"); raw="$VM_DIR/shots/$1.ppm"; qmp "$qmp_sock" "{\"execute\":\"screendump\",\"arguments\":{\"filename\":\"$raw\"}}"; python3 - "$raw" "$VM_DIR/shots/$1.png" <<'PY'
from PIL import Image
import sys
Image.open(sys.argv[1]).save(sys.argv[2], format="PNG")
PY
rm -f "$raw" ;;
  type|key) [[ $# -ge 1 ]] || usage; run_dir=$(<"$STATE/run-dir"); qmp_sock=$(qmp_path "$run_dir"); mode=$cmd; [[ $mode == type || $# == 1 ]] || usage; python3 - "$qmp_sock" "$mode" "$*" "${WINDOWS_VM_QMP_TIMEOUT:-3}" <<'PYQMP'
import json, socket, sys, time
path, mode, text, timeout = sys.argv[1:]
timeout=float(timeout)
if mode == "type" and (not text.isascii() or any(not ch.isprintable() for ch in text)): raise SystemExit("type accepts printable ASCII only")
shift={"!":"1","@":"2","#":"3","$":"4","%":"5","^":"6","&":"7","*":"8","(":"9",")":"0","_":"minus","+":"equal",":":"semicolon","?":"slash","<":"comma",">":"dot","\"":"apostrophe","{":"bracket_left","}":"bracket_right","|":"backslash","~":"grave_accent"}
plain={".":"dot",",":"comma","/":"slash","-":"minus","=":"equal",";":"semicolon","'":"apostrophe","\\":"backslash","[":"bracket_left","]":"bracket_right","`":"grave_accent"}
valid_qcodes=set("abcdefghijklmnopqrstuvwxyz0123456789")|{"ctrl","alt","shift","delete","esc","ret","tab","spc","backspace","up","down","left","right","home","end","pgup","pgdn","insert","print","pause","f1","f2","f3","f4","f5","f6","f7","f8","f9","f10","f11","f12"}
def qmp(keys):
    deadline=time.monotonic()+timeout
    sock=socket.socket(socket.AF_UNIX); sock.settimeout(max(0, deadline-time.monotonic())); sock.connect(path)
    pending=b""
    def read_line():
        nonlocal pending
        while True:
            remain=deadline-time.monotonic()
            if remain <= 0: raise TimeoutError("QMP response deadline exceeded")
            if b"\n" in pending:
                line,pending=pending.split(b"\n",1)
                return line
            # Recompute the remaining budget for every chunk, not every complete line.
            sock.settimeout(remain)
            try: chunk=sock.recv(4096)
            except socket.timeout: raise TimeoutError("QMP response deadline exceeded") from None
            if not chunk: raise RuntimeError("QMP closed")
            pending+=chunk
    def receive(wanted):
        while True:
            row=json.loads(read_line())
            if "event" in row or row.get("id") != wanted: continue
            if "error" in row: raise RuntimeError(str(row["error"]))
            return row
    read_line()
    sock.sendall(b'{"execute":"qmp_capabilities","id":"cap"}\r\n'); receive("cap")
    request={"execute":"send-key","arguments":{"keys":[{"type":"qcode","data":k} for k in keys],"hold-time":10},"id":"key"}
    sock.sendall(json.dumps(request).encode()+b"\r\n"); receive("key"); sock.close()
if mode == "key":
    aliases={"enter":"ret","return":"ret","space":"spc","pageup":"pgup","pagedown":"pgdn","grave":"grave_accent","bracketleft":"bracket_left","bracketright":"bracket_right","del":"delete"}
    valid_qcodes.update(plain.values())
    keys=[aliases.get(k,k) for k in text.replace("-", "+").split("+")]
    if not keys or any(k not in valid_qcodes for k in keys): raise SystemExit("unsupported QEMU key name")
    qmp(keys)
else:
    for ch in text:
        if ch.isalpha(): keys=(['shift'] if ch.isupper() else [])+[ch.lower()]
        elif ch.isdigit(): keys=[ch]
        elif ch == " ": keys=["spc"]
        elif ch in shift: keys=["shift",shift[ch]]
        elif ch in plain: keys=[plain[ch]]
        else: raise SystemExit("unsupported character: "+repr(ch))
        qmp(keys); time.sleep(.03)
PYQMP
  ;;
  *) usage ;;
esac
