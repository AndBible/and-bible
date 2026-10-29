#!/usr/bin/env bash
set -euo pipefail

ROOT=$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)
TMP=$(mktemp -d)
cleanup() {
  for pidfile in "$TMP"/vm-root/state/*/qemu.pid "$TMP"/vm-root/state/*/swtpm.pid; do
    [[ -f "$pidfile" ]] || continue
    kill "$(<"$pidfile")" 2>/dev/null || true
  done
  for pidfile in "$TMP"/vm-root/runs/*/*/swtpm.pid; do [[ -f "$pidfile" ]] && kill "$(<"$pidfile")" 2>/dev/null || true; done
  for pidfile in "$TMP"/log/qmp.pid; do [[ -f "$pidfile" ]] && kill "$(<"$pidfile")" 2>/dev/null || true; done
  rm -rf "$TMP"
}
trap cleanup EXIT
FAKE="$TMP/bin"
mkdir -p "$FAKE" "$TMP/vm-root" "$TMP/log"
cat > "$FAKE/qemu-img" <<'SH'
#!/usr/bin/env bash
printf '%s\n' "$*" >> "$WINDOWS_VM_TEST_LOG/qemu-img"
if [[ "$1" == create ]]; then touch "${@: -1}"; fi
SH
cat > "$FAKE/qemu-system-x86_64" <<'SH'
#!/usr/bin/env bash
printf '%s\n' "$*" >> "$WINDOWS_VM_TEST_LOG/qemu"
if [[ ${FAKE_QEMU_EXIT:-0} == 1 ]]; then exit 0; fi
if [[ ${FAKE_QEMU_NO_QMP:-0} == 1 ]]; then exec sleep 300; fi
while (($#)); do
  if [[ "$1" == -qmp ]]; then
    socket_path=${2#unix:}; socket_path=${socket_path%%,*}
    exec python3 -c 'import json,socket,sys; p=sys.argv[1]; s=socket.socket(socket.AF_UNIX); s.bind(p); s.listen();
while True:
 c,_=s.accept(); f=c.makefile("rwb",buffering=0); f.write(b"{\"QMP\":{}}\r\n")
 while True:
  line=f.readline()
  if not line:break
  r=json.loads(line); open(sys.argv[2],"a").write(json.dumps(r)+"\n"); f.write(b"{\"event\":\"RESET\"}\r\n");
  if r["execute"]=="qmp_capabilities" and __import__("os").environ.get("FAKE_QMP_CAP_ERROR"): f.write(json.dumps({"error":{"desc":"capability denied"},"id":r["id"]}).encode()+b"\r\n")
  elif __import__("os").environ.get("FAKE_QMP_EVENT_FLOOD"):
   while True:f.write(b"{\"event\":\"NOISE\"}\r\n")
  else:
   if r["execute"]=="screendump":
    from PIL import Image
    Image.new("RGB",(1,1)).save(r["arguments"]["filename"],format="PPM")
   f.write(json.dumps({"return":{},"id":r["id"]}).encode()+b"\r\n")' "$socket_path" "$WINDOWS_VM_TEST_LOG/qmp-requests"
  fi
  shift
done
exit 1
SH
cat > "$FAKE/swtpm" <<'SH'
#!/usr/bin/env bash
printf '%s\n' "$*" >> "$WINDOWS_VM_TEST_LOG/swtpm"
for arg in "$@"; do
  if [[ "$arg" == file\=* ]]; then sleep 300 & echo $! > "${arg#file=}"; exit 0; fi
done
exit 1
SH
chmod +x "$FAKE"/*
export PATH="$FAKE:$PATH"
export WINDOWS_VM_TEST_LOG="$TMP/log"
export WINDOWS_VM_DIR="$TMP/vm-root"
export WINDOWS_VM_BASE="$TMP/base.qcow2"
export WINDOWS_VM_ID=test-container
STATE_DIR="$WINDOWS_VM_DIR/state/$WINDOWS_VM_ID"
touch "$WINDOWS_VM_BASE"

fail() { printf 'FAIL: %s\n' "$*" >&2; exit 1; }
run() { bash "$ROOT/scripts/windows-vm.sh" "$@"; }

startUsesKvmOvmfTpmAhci() {
  run start
  local args
  args=$(<"$WINDOWS_VM_TEST_LOG/qemu")
  [[ "$args" == *'-enable-kvm'* ]] || fail 'QEMU was not started with KVM'
  [[ "$args" == *'/usr/share/OVMF/OVMF_CODE_4M.secboot.fd'* ]] || fail 'secure OVMF code missing'
  [[ "$args" == *'OVMF_VARS.ms.fd'* ]] || fail 'copied OVMF vars missing'
  [[ "$args" == *'socket,id=chrtpm,path='* && "$args" == *'-tpmdev emulator,id=tpm0,chardev=chrtpm'* ]] || fail 'TPM chardev missing'
  [[ "$args" == *'-device ahci,id=sata0'* && "$args" == *'bus=sata0.0'* ]] || fail 'AHCI controller or disk bus missing'
  [[ "$args" == *'readonly=on,file=/usr/share/OVMF/OVMF_CODE_4M.secboot.fd'* ]] || fail 'firmware code drive is not readonly'
  [[ "$args" == *'hostfwd=tcp:127.0.0.1:2222-:22'* ]] || fail 'SSH forwarding is not bound to loopback'
  run stop >/dev/null
}

qmpCapabilityErrorsAbortCommand() {
  FAKE_QMP_CAP_ERROR=1 run start
  if WINDOWS_VM_QMP_TIMEOUT=.2 run key ctrl >"$TMP/qmp-cap-error.out" 2>&1; then fail 'QMP capability error was ignored'; fi
  grep -qi 'capability denied' "$TMP/qmp-cap-error.out" || fail 'capability failure was not reported'
  run stop >/dev/null
}

keyAcceptsPlusAndHyphenCombos() {
  run start
  : > "$WINDOWS_VM_TEST_LOG/qmp-requests"
  run key ctrl-alt-delete
  run key ctrl+alt+delete
  python3 - "$WINDOWS_VM_TEST_LOG/qmp-requests" <<'PY'
import json, sys
rows=[json.loads(line) for line in open(sys.argv[1])]
keys=[r["arguments"]["keys"] for r in rows if r.get("execute")=="send-key"]
expected=[{"type":"qcode","data":k} for k in ("ctrl","alt","delete")]
assert len(keys)==2 and all(item==expected for item in keys), keys
PY
  run stop >/dev/null
}

qmpEventFloodHonorsDeadline() {
  FAKE_QMP_EVENT_FLOOD=1 run start
  local before=$SECONDS
  if WINDOWS_VM_QMP_TIMEOUT=.2 run shot flood >"$TMP/qmp-flood.out" 2>&1; then fail 'event flood did not time out'; fi
  (( SECONDS - before < 3 )) || fail 'event flood exceeded bounded deadline'
  run stop >/dev/null
}

qemuExitBeforeQmpCleansTpm() {
  if FAKE_QEMU_EXIT=1 run start >"$TMP/qemu-exit.out" 2>&1; then fail 'early QEMU exit unexpectedly started'; fi
  [[ ! -f "$STATE_DIR/qemu.pid" && ! -f "$STATE_DIR/swtpm.pid" ]] || fail 'early QEMU exit left state files'
  local pid
  pid=$(find "$WINDOWS_VM_DIR/runs/test-container" -name swtpm.pid -print -quit)
  ! kill -0 "$(<"$pid")" 2>/dev/null || fail 'early QEMU exit leaked swtpm'
}

startupTimeoutCleansTpm() {
  WINDOWS_VM_START_POLLS=2 FAKE_QEMU_NO_QMP=1 timeout 5 bash "$ROOT/scripts/windows-vm.sh" start >"$TMP/start-timeout.out" 2>&1 && fail 'QMP startup timeout unexpectedly succeeded'
  [[ ! -f "$STATE_DIR/qemu.pid" && ! -f "$STATE_DIR/swtpm.pid" ]] || fail 'startup timeout retained process state'
  local tpm
  tpm=$(find "$WINDOWS_VM_DIR/runs/test-container" -name swtpm.pid -print -quit)
  [[ -n "$tpm" ]] || fail 'startup timeout run directory missing TPM record'
  ! kill -0 "$(<"$tpm")" 2>/dev/null || fail 'startup timeout left swtpm alive'
}

typeRejectsUnicodeBeforeSendingAnything() {
  run start
  : > "$WINDOWS_VM_TEST_LOG/qmp-requests"
  if run type 'oké' >"$TMP/type.out" 2>&1; then fail 'type accepted unsupported non-ASCII'; fi
  [[ $(grep -c '"execute": "send-key"' "$WINDOWS_VM_TEST_LOG/qmp-requests" || true) == 0 ]] || fail 'type sent a partial prefix before rejecting Unicode'
  run stop >/dev/null
}

qmpCommandsUseValidKeysAndPng() {
  run start
  run type 'Hi! a.,-/='
  run key ctrl-alt-delete
  mkdir -p "$WINDOWS_VM_DIR/shots"
  run shot test-shot
  python3 - "$WINDOWS_VM_DIR/shots/test-shot.png" <<'PY'
import sys
from PIL import Image
assert Image.open(sys.argv[1]).format == "PNG"
PY
  local requests
  requests=$(<"$WINDOWS_VM_TEST_LOG/qmp-requests")
  [[ "$requests" == *'"execute": "send-key"'* ]] || fail 'key/type did not use QMP send-key'
  [[ "$requests" == *'"execute": "screendump"'* ]] || fail 'shot did not request screenshot'
  [[ "$requests" == *'"keys":'* ]] || fail 'type did not send individual key events'
  [[ $(grep -c '"execute": "send-key"' <<<"$requests") -ge 9 ]] || fail 'type did not send individual characters'
  [[ "$requests" == *'"data": "shift"'* ]] || fail 'shifted punctuation/capital letters were not encoded'
  python3 - "$WINDOWS_VM_TEST_LOG/qmp-requests" <<'PY'
import json, sys
rows=[json.loads(line) for line in open(sys.argv[1])]
keys=[r["arguments"]["keys"] for r in rows if r.get("execute")=="send-key"]
assert keys[-1] == [{"type":"qcode","data":"ctrl"},{"type":"qcode","data":"alt"},{"type":"qcode","data":"delete"}], keys[-1]
assert [{"type":"qcode","data":"dot"}] in keys
assert [{"type":"qcode","data":"minus"}] in keys
assert [{"type":"qcode","data":"slash"}] in keys
assert [{"type":"qcode","data":"equal"}] in keys
assert all(r["arguments"].get("hold-time")==10 for r in rows if r.get("execute")=="send-key")
PY
  run stop >/dev/null
}

containersHaveIndependentState() {
  run start
  local first_run first_pid
  first_run=$(<"$STATE_DIR/run-dir")
  first_pid=$(<"$STATE_DIR/qemu.pid")
  WINDOWS_VM_ID=other-container run start
  [[ -f "$WINDOWS_VM_DIR/state/other-container/qemu.pid" ]] || fail 'second container did not get independent state'
  [[ "$(<"$STATE_DIR/qemu.pid")" == "$first_pid" ]] || fail 'second start replaced first container state'
  [[ "$first_run" != "$(<"$WINDOWS_VM_DIR/state/other-container/run-dir")" ]] || fail 'containers shared a run directory'
  run stop >/dev/null
  WINDOWS_VM_ID=other-container run stop >/dev/null
}

twoRunsGetSeparateOverlays() {
  run start
  local first
  first=$(<"$STATE_DIR/run-dir")/overlay.qcow2
  run stop >/dev/null
  run start
  local second
  second=$(<"$STATE_DIR/run-dir")/overlay.qcow2
  [[ -n "$first" && -n "$second" && "$first" != "$second" ]] || fail 'runs did not get unique overlays'
  run stop >/dev/null
}

baseIsNeverOpenedWritable() {
  run start
  local args
  args=$(<"$WINDOWS_VM_TEST_LOG/qemu")
  [[ "$args" == *'overlay.qcow2'* ]] || fail 'QEMU is not using a per-run overlay'
  [[ "$args" != *"$WINDOWS_VM_BASE"* ]] || fail 'base image was opened directly as a writable disk'
  local img_args
  img_args=$(<"$WINDOWS_VM_TEST_LOG/qemu-img")
  [[ "$img_args" == *"backing_file=$WINDOWS_VM_BASE"* || "$img_args" == *"$WINDOWS_VM_BASE"* ]] || fail 'overlay not backed by base image'
  [[ "$img_args" == *'-b '* && "$img_args" == *'-F qcow2'* ]] || fail 'overlay backing format is unspecified'
  run stop >/dev/null
}

concurrentStartsAreSerialized() {
  local before
  before=$(find "$WINDOWS_VM_DIR/runs/test-container" -mindepth 1 -maxdepth 1 -type d | wc -l)
  run start >"$TMP/start-a.out" 2>&1 & local a=$!
  run start >"$TMP/start-b.out" 2>&1 & local b=$!
  wait "$a" || true
  wait "$b" || true
  local count
  count=$(find "$WINDOWS_VM_DIR/runs/test-container" -mindepth 1 -maxdepth 1 -type d | wc -l)
  [[ "$count" == "$((before + 1))" ]] || fail "concurrent starts created $((count - before)) run directories"
  run stop >/dev/null
}

startRefusesWhenRunning() {
  run start
  if run start >"$TMP/duplicate.out" 2>&1; then fail 'second start unexpectedly succeeded'; fi
  grep -qi 'already running' "$TMP/duplicate.out" || fail 'duplicate start did not explain running state'
  run stop >/dev/null
}

stoppingAfterQemuExitCleansSwtpm() {
  run start
  local qemu swtpm
  qemu=$(<"$STATE_DIR/qemu.pid"); swtpm=$(<"$STATE_DIR/swtpm.pid")
  kill "$qemu"
  sleep .1
  run stop >/dev/null
  kill -0 "$swtpm" 2>/dev/null && [[ $(ps -o stat= -p "$swtpm" | tr -d ' ') != Z* ]] && fail 'stop left swtpm running after QEMU exited'
  [[ ! -f "$STATE_DIR/qemu.pid" && ! -f "$STATE_DIR/swtpm.pid" ]] || fail 'stop did not clear stale state'
}

stopKillsQemuAndSwtpm() {
  run start
  local qemu swtpm
  qemu=$(<"$STATE_DIR/qemu.pid")
  swtpm=$(<"$STATE_DIR/swtpm.pid")
  run stop
  for _ in {1..20}; do
    kill -0 "$qemu" 2>/dev/null || break
    [[ $(ps -o stat= -p "$qemu" | tr -d ' ') == Z* ]] && break
    sleep .05
  done
  if kill -0 "$qemu" 2>/dev/null && [[ $(ps -o stat= -p "$qemu" | tr -d ' ') != Z* ]]; then fail 'QEMU remained alive after stop'; fi
  for _ in {1..20}; do
    kill -0 "$swtpm" 2>/dev/null || break
    [[ $(ps -o stat= -p "$swtpm" | tr -d ' ') == Z* ]] && break
    sleep .05
  done
  if kill -0 "$swtpm" 2>/dev/null && [[ $(ps -o stat= -p "$swtpm" | tr -d ' ') != Z* ]]; then fail 'swtpm remained alive after stop'; fi
}

for test in startUsesKvmOvmfTpmAhci qmpCapabilityErrorsAbortCommand keyAcceptsPlusAndHyphenCombos qmpEventFloodHonorsDeadline qemuExitBeforeQmpCleansTpm startupTimeoutCleansTpm typeRejectsUnicodeBeforeSendingAnything stoppingAfterQemuExitCleansSwtpm concurrentStartsAreSerialized qmpCommandsUseValidKeysAndPng containersHaveIndependentState twoRunsGetSeparateOverlays baseIsNeverOpenedWritable startRefusesWhenRunning stopKillsQemuAndSwtpm; do
  printf '%s ... ' "$test"
  "$test"
  printf 'PASS\n'
done
