#!/usr/bin/env bash
set -euo pipefail

ROOT=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
VM_DIR=${WINDOWS_VM_DIR:-$ROOT/.local/windows-vm}
BASE=${WINDOWS_VM_BASE:-$VM_DIR/base.qcow2}
RAM=${WINDOWS_VM_RAM:-6G}
CPUS=${WINDOWS_VM_CPUS:-4}
SSH_PORT=${WINDOWS_VM_SSH_PORT:-2222}
VM_ID=${WINDOWS_VM_ID:-$(hostname)}
[[ "$VM_ID" =~ ^[a-zA-Z0-9._-]+$ ]] || { printf 'windows-vm: invalid container id: %s\n' "$VM_ID" >&2; exit 2; }
STATE="$VM_DIR/state/$VM_ID"
RUNS="$VM_DIR/runs/$VM_ID"
QEMU=${QEMU:-qemu-system-x86_64}

usage() {
  printf '%s\n' 'Usage: windows-vm.sh {install|start|stop|status|ssh [cmd]|scp-to src dst|scp-from src dst|shot name|type text|key combo}' >&2
  exit 2
}
error() { printf 'windows-vm: %s\n' "$*" >&2; exit 1; }

qmp() {
  python3 - "$1" "$2" <<'PY'
import json, socket, sys
path, command = sys.argv[1:]
s = socket.socket(socket.AF_UNIX); s.settimeout(3); s.connect(path)
f = s.makefile("rwb", buffering=0)
def receive():
    while True:
        line = f.readline()
        if not line: raise RuntimeError("QMP connection closed")
        response = json.loads(line)
        if "event" in response: continue
        yield_response = response
        return yield_response
receive()
f.write(b'{"execute":"qmp_capabilities","id":"capabilities"}\r\n')
while receive().get("id") != "capabilities": pass
request = json.loads(command); request["id"] = "command"
f.write(json.dumps(request).encode() + b"\r\n")
while True:
    response = receive()
    if response.get("id") == "command":
        if "error" in response: raise RuntimeError(response["error"])
        print(json.dumps(response.get("return", {}))); break
s.close()
PY
}

process_start() {
  [[ -r "/proc/$1/stat" ]] || return 1
  local stat rest
  IFS= read -r stat < "/proc/$1/stat" || return 1
  rest=${stat##*) }
  set -- $rest
  printf '%s\n' "${20}"
}
owned_alive() {
  [[ -r "$1" && -r "$2" ]] || return 1
  local pid expected state actual
  pid=$(<"$1"); expected=$(<"$2")
  read -r state actual < <(awk '{print $3, $22}' "/proc/$pid/stat" 2>/dev/null) || return 1
  [[ "$state" != Z && "$actual" == "$expected" ]]
}
terminate_owned() {
  local pidfile=$1 startfile=$2 pid
  owned_alive "$pidfile" "$startfile" || return 0
  pid=$(<"$pidfile"); kill "$pid" 2>/dev/null || true
  for _ in {1..50}; do owned_alive "$pidfile" "$startfile" || return 0; sleep .1; done
  owned_alive "$pidfile" "$startfile" && kill -KILL "$pid" 2>/dev/null || true
}

start_vm() {
  mkdir -p "$VM_DIR" "$RUNS" "$STATE"
  exec 9>"$STATE/lock"
  flock 9
  [[ -f "$STATE/qemu.pid" ]] && owned_alive "$STATE/qemu.pid" "$STATE/qemu.start" && error 'VM is already running'
  if [[ -f "$STATE/qemu.pid" ]]; then
    run_dir=$(<"$STATE/run-dir")
    if [[ -e "$run_dir/lock" ]]; then
      owned_alive "$STATE/qemu.pid" "$STATE/qemu.start" || error 'stale VM state; refusing to overwrite its run record'
    fi
  fi
  local code=/usr/share/OVMF/OVMF_CODE_4M.secboot.fd vars=/usr/share/OVMF/OVMF_VARS_4M.ms.fd
  [[ -r "$code" ]] || error "OVMF secure boot firmware not found: $code (install ovmf)"
  [[ -r "$vars" ]] || error "OVMF variables template not found: $vars (install ovmf)"
  [[ -f "$BASE" ]] || error "base disk not found: $BASE (install/import a Windows base image first)"
  command -v "$QEMU" >/dev/null || error "QEMU executable not found: $QEMU"
  command -v swtpm >/dev/null || error 'swtpm executable not found'
  command -v qemu-img >/dev/null || error 'qemu-img executable not found'
  local id run_dir
  id="$(date -u +%Y%m%dT%H%M%S).$$.$RANDOM"
  mkdir -p "$RUNS"
  run_dir="$RUNS/$id"
  mkdir -p "$run_dir/tpm"
  cp "$vars" "$run_dir/OVMF_VARS.ms.fd"
  qemu-img create -f qcow2 -F qcow2 -b "$BASE" "$run_dir/overlay.qcow2" >/dev/null
  if ! swtpm socket --tpm2 --tpmstate "dir=$run_dir/tpm" --ctrl "type=unixio,path=$run_dir/tpm.sock" --daemon --pid file="$run_dir/swtpm.pid"; then rm -rf "$run_dir"; error 'failed to start swtpm'; fi
  local swtpm_pid
  swtpm_pid=$(<"$run_dir/swtpm.pid")
  for _ in {1..20}; do
    process_start "$swtpm_pid" > "$run_dir/swtpm.start" 2>/dev/null && break
    sleep .01
  done
  [[ -s "$run_dir/swtpm.start" ]] || { kill "$swtpm_pid" 2>/dev/null || true; error 'could not record swtpm process identity'; }
  local qmp_sock="$run_dir/qmp.sock"
  "$QEMU" 9>&- -name windows-vm -enable-kvm -m "$RAM" -smp "$CPUS" \
    -drive "if=pflash,format=raw,readonly=on,file=$code" \
    -drive "if=pflash,format=raw,file=$run_dir/OVMF_VARS.ms.fd" \
    -chardev "socket,id=chrtpm,path=$run_dir/tpm.sock" -tpmdev emulator,id=tpm0,chardev=chrtpm \
    -device tpm-tis,tpmdev=tpm0 -device ahci,id=sata0 -drive "file=$run_dir/overlay.qcow2,if=none,id=disk0,format=qcow2" \
    -device ide-hd,drive=disk0,bus=sata0.0 -netdev user,id=net0,hostfwd="tcp:127.0.0.1:${SSH_PORT}-:22" \
    -device e1000e,netdev=net0 -display none -serial mon:stdio \
    -qmp "unix:$qmp_sock,server=on,wait=off" >"$run_dir/qemu.log" 2>&1 9>&- &
  local qpid=$!
  printf '%s\n' "$qpid" > "$STATE/qemu.pid"
  for _ in {1..20}; do
    process_start "$qpid" > "$STATE/qemu.start" 2>/dev/null && break
    sleep .01
  done
  [[ -s "$STATE/qemu.start" ]] || { terminate_owned "$run_dir/swtpm.pid" "$run_dir/swtpm.start"; error 'could not record QEMU process identity'; }
  printf '%s\n' "$swtpm_pid" > "$STATE/swtpm.pid"
  cp "$run_dir/swtpm.start" "$STATE/swtpm.start"
  printf '%s\n' "$run_dir" > "$STATE/run-dir"
  touch "$run_dir/lock"
  for _ in {1..50}; do
    owned_alive "$STATE/qemu.pid" "$STATE/qemu.start" || { terminate_owned "$STATE/swtpm.pid" "$STATE/swtpm.start"; rm -f "$STATE/qemu.pid" "$STATE/qemu.start" "$STATE/swtpm.pid" "$STATE/swtpm.start" "$STATE/run-dir"; error 'QEMU exited during startup'; }
    [[ -S "$qmp_sock" ]] && { flock -u 9; echo "Windows VM started (run $id)"; return; }
    sleep .1
  done
  stop_vm
  error 'timed out waiting for QEMU QMP socket'
}

stop_vm() {
  mkdir -p "$STATE"
  exec 9>"$STATE/lock"
  flock 9
  if ! owned_alive "$STATE/qemu.pid" "$STATE/qemu.start"; then echo 'Windows VM is not running'; return; fi
  local run_dir=$(<"$STATE/run-dir")
  [[ -e "$run_dir/lock" ]] || error 'VM run lock is missing; refusing to stop unverified processes'
  exec 8>"$run_dir/lock"
  flock -n 8 || error 'VM run is busy; refusing to stop during another operation'
  terminate_owned "$STATE/qemu.pid" "$STATE/qemu.start"
  terminate_owned "$STATE/swtpm.pid" "$STATE/swtpm.start"
  rm -f "$STATE/qemu.pid" "$STATE/qemu.start" "$STATE/swtpm.pid" "$STATE/swtpm.start" "$STATE/run-dir"
  echo 'Windows VM stopped'
}

[[ $# -gt 0 ]] || usage
cmd=$1; shift
case "$cmd" in
  install) error 'install is not implemented; provide a Windows base image at WINDOWS_VM_BASE' ;;
  start) start_vm ;;
  stop) stop_vm ;;
  status) if owned_alive "$STATE/qemu.pid" "$STATE/qemu.start"; then echo "running (PID $(<"$STATE/qemu.pid"))"; else echo 'stopped'; fi ;;
  ssh) exec ssh -p "$SSH_PORT" -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null "${WINDOWS_VM_USER:-windows}@127.0.0.1" "$@" ;;
  scp-to) [[ $# == 2 ]] || usage; exec scp -P "$SSH_PORT" -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null "$1" "${WINDOWS_VM_USER:-windows}@127.0.0.1:$2" ;;
  scp-from) [[ $# == 2 ]] || usage; exec scp -P "$SSH_PORT" -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null "${WINDOWS_VM_USER:-windows}@127.0.0.1:$1" "$2" ;;
  shot) [[ $# == 1 ]] || usage; mkdir -p "$VM_DIR/shots"; run_dir=$(<"$STATE/run-dir"); raw="$VM_DIR/shots/$1.ppm"; qmp "$run_dir/qmp.sock" "{\"execute\":\"screendump\",\"arguments\":{\"filename\":\"$raw\"}}"; python3 - "$raw" "$VM_DIR/shots/$1.png" <<'PY'
from PIL import Image
import sys
Image.open(sys.argv[1]).save(sys.argv[2], format="PNG")
PY
rm -f "$raw" ;;
  type) [[ $# -ge 1 ]] || usage; run_dir=$(<"$STATE/run-dir"); python3 - "$run_dir/qmp.sock" "$*" <<'PY'
import json, socket, sys, time
path, text = sys.argv[1:]
shift = {"!":"1", "@":"2", "#":"3", "$":"4", "%":"5", "^":"6", "&":"7", "*":"8", "(":"9", ")":"0", "_":"minus", "+":"equal", ":":"semicolon", "?":"slash", "<":"comma", ">":"dot", "\"":"apostrophe"}
def send(combo):
 s=socket.socket(socket.AF_UNIX); s.settimeout(3); s.connect(path); f=s.makefile("rwb",buffering=0); f.readline()
 def recv():
  while True:
   r=json.loads(f.readline())
   if "event" not in r:return r
 f.write(b'{"execute":"qmp_capabilities","id":"cap"}\r\n')
 while recv().get("id")!="cap":pass
 f.write(json.dumps({"execute":"send-key","arguments":{"keys":[{"type":"qcode","data":k} for k in combo.split("+")]},"id":"key"}).encode()+b"\r\n")
 while True:
  r=recv()
  if r.get("id")=="key":
   if "error" in r: raise SystemExit(str(r["error"]))
   break
 s.close()
for ch in text:
 if ch.isalpha(): send(("shift+" if ch.isupper() else "")+ch.lower())
 elif ch.isdigit() or ch==" ":send("spc" if ch==" " else ch)
 elif ch in shift:send("shift+"+shift[ch])
 else: raise SystemExit("unsupported text character: "+repr(ch))
 time.sleep(.03)
PY
  ;;
  key) [[ $# == 1 ]] || usage; run_dir=$(<"$STATE/run-dir"); python3 - "$run_dir/qmp.sock" "$1" <<'PY'
import json, socket, sys
p, combo = sys.argv[1:]
s=socket.socket(socket.AF_UNIX); s.settimeout(3); s.connect(p); f=s.makefile("rwb",buffering=0); f.readline()
def recv():
 while True:
  r=json.loads(f.readline())
  if "event" not in r:return r
f.write(b'{"execute":"qmp_capabilities","id":"c"}\r\n')
while recv().get("id")!="c":pass
request={"execute":"send-key","arguments":{"keys":[{"type":"qcode","data":k} for k in combo.split("+")]},"id":"key"}
f.write(json.dumps(request).encode()+b"\r\n")
while True:
 r=recv()
 if r.get("id")=="key":
  if "error" in r:raise SystemExit(str(r["error"]))
  break
PY
  ;;
  *) usage ;;
esac
