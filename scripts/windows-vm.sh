#!/usr/bin/env bash
set -euo pipefail

ROOT=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
VM_DIR=${WINDOWS_VM_DIR:-$ROOT/.local/windows-vm}
BASE=${WINDOWS_VM_BASE:-$VM_DIR/base.qcow2}
RAM=${WINDOWS_VM_RAM:-6G}
CPUS=${WINDOWS_VM_CPUS:-4}
SSH_PORT=${WINDOWS_VM_SSH_PORT:-2222}
STATE="$VM_DIR/state"
RUNS="$VM_DIR/runs"
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
s = socket.socket(socket.AF_UNIX)
s.connect(path)
f = s.makefile("rwb", buffering=0)
f.readline()
f.write(b'{"execute":"qmp_capabilities"}\r\n')
f.readline()
f.write(json.dumps(json.loads(command)).encode() + b"\r\n")
reply = f.readline()
print(reply.decode().strip())
s.close()
PY
}

alive() { [[ -f "$1" ]] && kill -0 "$(<"$1")" 2>/dev/null; }

start_vm() {
  mkdir -p "$VM_DIR" "$RUNS" "$STATE"
  if alive "$STATE/qemu.pid"; then error 'VM is already running'; fi
  local code=/usr/share/OVMF/OVMF_CODE_4M.secboot.fd vars=/usr/share/OVMF/OVMF_VARS_4M.ms.fd
  [[ -r "$code" ]] || error "OVMF secure boot firmware not found: $code (install ovmf)"
  [[ -r "$vars" ]] || error "OVMF variables template not found: $vars (install ovmf)"
  [[ -f "$BASE" ]] || error "base disk not found: $BASE (install/import a Windows base image first)"
  command -v "$QEMU" >/dev/null || error "QEMU executable not found: $QEMU"
  command -v swtpm >/dev/null || error 'swtpm executable not found'
  command -v qemu-img >/dev/null || error 'qemu-img executable not found'
  local id run_dir
  id="$(date -u +%Y%m%dT%H%M%S).$$.$RANDOM"
  run_dir="$RUNS/$id"
  mkdir -p "$run_dir/tpm"
  cp "$vars" "$run_dir/OVMF_VARS.ms.fd"
  qemu-img create -f qcow2 -F qcow2 -b "$BASE" "$run_dir/overlay.qcow2" >/dev/null
  swtpm socket --tpm2 --tpmstate "dir=$run_dir/tpm" --ctrl "type=unixio,path=$run_dir/tpm.sock" --daemon --pid file="$run_dir/swtpm.pid" || error 'failed to start swtpm'
  local qmp_sock="$run_dir/qmp.sock"
  "$QEMU" -name windows-vm -enable-kvm -m "$RAM" -smp "$CPUS" \
    -drive "if=pflash,format=raw,readonly=on,file=$code" \
    -drive "if=pflash,format=raw,file=$run_dir/OVMF_VARS.ms.fd" \
    -chardev "socket,id=chrtpm,path=$run_dir/tpm.sock" -tpmdev emulator,id=tpm0,chardev=chrtpm \
    -device tpm-tis,tpmdev=tpm0 -device ahci -drive "file=$run_dir/overlay.qcow2,if=none,id=disk0,format=qcow2" \
    -device ide-hd,drive=disk0 -netdev user,id=net0,hostfwd="tcp::${SSH_PORT}-:22" \
    -device e1000e,netdev=net0 -display none -serial mon:stdio \
    -qmp "unix:$qmp_sock,server=on,wait=off" >"$run_dir/qemu.log" 2>&1 &
  local qpid=$!
  printf '%s\n' "$run_dir" > "$STATE/run-dir"
  printf '%s\n' "$qpid" > "$STATE/qemu.pid"
  printf '%s\n' "$(<"$run_dir/swtpm.pid")" > "$STATE/swtpm.pid"
  for _ in {1..50}; do
    kill -0 "$qpid" 2>/dev/null || { rm -f "$STATE/qemu.pid" "$STATE/swtpm.pid" "$STATE/run-dir"; error 'QEMU exited during startup'; }
    [[ -S "$qmp_sock" ]] && { echo "Windows VM started (run $id)"; return; }
    sleep .1
  done
  stop_vm
  error 'timed out waiting for QEMU QMP socket'
}

stop_vm() {
  [[ -f "$STATE/qemu.pid" ]] || { echo 'Windows VM is not running'; return; }
  local qpid tpid
  qpid=$(<"$STATE/qemu.pid")
  tpid=$(<"$STATE/swtpm.pid")
  if kill -0 "$qpid" 2>/dev/null; then kill "$qpid"; wait "$qpid" 2>/dev/null || true; fi
  if kill -0 "$tpid" 2>/dev/null; then kill "$tpid"; fi
  rm -f "$STATE/qemu.pid" "$STATE/swtpm.pid" "$STATE/run-dir"
  echo 'Windows VM stopped'
}

[[ $# -gt 0 ]] || usage
cmd=$1; shift
case "$cmd" in
  install) error 'install is not implemented; provide a Windows base image at WINDOWS_VM_BASE' ;;
  start) start_vm ;;
  stop) stop_vm ;;
  status) if alive "$STATE/qemu.pid"; then echo "running (PID $(<"$STATE/qemu.pid"))"; else echo 'stopped'; fi ;;
  ssh) exec ssh -p "$SSH_PORT" -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null "${WINDOWS_VM_USER:-windows}@127.0.0.1" "$@" ;;
  scp-to) [[ $# == 2 ]] || usage; exec scp -P "$SSH_PORT" -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null "$1" "${WINDOWS_VM_USER:-windows}@127.0.0.1:$2" ;;
  scp-from) [[ $# == 2 ]] || usage; exec scp -P "$SSH_PORT" -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null "${WINDOWS_VM_USER:-windows}@127.0.0.1:$1" "$2" ;;
  shot) [[ $# == 1 ]] || usage; mkdir -p "$VM_DIR/shots"; run_dir=$(<"$STATE/run-dir"); qmp "$run_dir/qmp.sock" "{\"execute\":\"screendump\",\"arguments\":{\"filename\":\"$VM_DIR/shots/$1.png\"}}" ;;
  type) [[ $# -ge 1 ]] || usage; run_dir=$(<"$STATE/run-dir"); qmp "$run_dir/qmp.sock" "{\"execute\":\"human-monitor-command\",\"arguments\":{\"command-line\":\"sendkey $(printf '%s' "$*" | sed 's/ /+/g')\"}}" ;;
  key) [[ $# == 1 ]] || usage; run_dir=$(<"$STATE/run-dir"); qmp "$run_dir/qmp.sock" "{\"execute\":\"human-monitor-command\",\"arguments\":{\"command-line\":\"sendkey $1\"}}" ;;
  *) usage ;;
esac
