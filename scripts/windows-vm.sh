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

start_vm() {
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
  cp "$vars" "$run_dir/OVMF_VARS.ms.fd"
  qemu-img create -f qcow2 -F qcow2 -b "$BASE" "$run_dir/overlay.qcow2" >/dev/null
  touch "$run_dir/lock"
  local qmp_sock="$run_dir/qmp.sock"
  "$QEMU" -name windows-vm -enable-kvm -m "$RAM" -smp "$CPUS" \
    -drive "if=pflash,format=raw,readonly=on,file=$code" \
    -drive "if=pflash,format=raw,file=$run_dir/OVMF_VARS.ms.fd" \
    -device ahci,id=sata0 -drive "file=$run_dir/overlay.qcow2,if=none,id=disk0,format=qcow2" \
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
  type|key) [[ $# -ge 1 ]] || usage; run_dir=$(<"$STATE/run-dir"); mode=$cmd; [[ $mode == type || $# == 1 ]] || usage; python3 - "$run_dir/qmp.sock" "$mode" "$*" "${WINDOWS_VM_QMP_TIMEOUT:-3}" <<'PYQMP'
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
