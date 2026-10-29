#!/usr/bin/env bash
set -euo pipefail

ROOT=$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)
TMP=$(mktemp -d)
cleanup() {
  for pidfile in "$TMP"/vm-root/state/*/qemu.pid; do
    [[ -f "$pidfile" ]] || continue
    kill "$(<"$pidfile")" 2>/dev/null || true
  done
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
    exec python3 -c 'import json,socket,sys,os,signal,ctypes; signal.signal(signal.SIGTERM, signal.SIG_IGN) if os.environ.get("FAKE_QEMU_IGNORE_TERM") else None; ctypes.CDLL(None).prctl(15, b"qemu test", 0, 0, 0) if os.environ.get("FAKE_QEMU_IGNORE_TERM") else None; p=sys.argv[1]; s=socket.socket(socket.AF_UNIX); s.bind(p); s.listen();
while True:
 c,_=s.accept(); f=c.makefile("rwb",buffering=0);
 if __import__("os").path.exists(__import__("os").environ["WINDOWS_VM_TEST_LOG"]+"/silent-greeting"):
  import time
  time.sleep(1); c.close(); continue
 f.write(b"{\"QMP\":{}}\r\n")
 while True:
  line=f.readline()
  if not line:break
  r=json.loads(line); open(sys.argv[2],"a").write(json.dumps(r)+"\n"); f.write(b"{\"event\":\"RESET\"}\r\n");
  qcodes=set("abcdefghijklmnopqrstuvwxyz0123456789")|set("minus equal semicolon apostrophe backslash comma dot slash ctrl alt shift delete esc ret tab spc backspace up down left right home end pgup pgdn insert print pause f1 f2 f3 f4 f5 f6 f7 f8 f9 f10 f11 f12 bracket_left bracket_right grave_accent".split())
  if r["execute"]=="send-key" and any(k["data"] not in qcodes for k in r["arguments"]["keys"]): f.write(json.dumps({"error":{"desc":"invalid QKeyCode"},"id":r["id"]}).encode()+b"\r\n")
  elif r["execute"]=="qmp_capabilities" and __import__("os").environ.get("FAKE_QMP_CAP_ERROR"): f.write(json.dumps({"error":{"desc":"capability denied"},"id":r["id"]}).encode()+b"\r\n")
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

startUsesKvmOvmfAhci() {
  run start
  local args
  args=$(<"$WINDOWS_VM_TEST_LOG/qemu")
  [[ "$args" == *'-enable-kvm'* ]] || fail 'QEMU was not started with KVM'
  [[ "$args" == *'-m 6G'* && "$args" == *'-smp 4'* ]] || fail 'VM defaults are not 6 GiB and 4 CPUs'
  [[ "$args" == *'/usr/share/OVMF/OVMF_CODE_4M.secboot.fd'* ]] || fail 'secure OVMF code missing'
  [[ "$args" == *'OVMF_VARS.ms.fd'* ]] || fail 'copied OVMF vars missing'
  [[ "$args" != *'tpm'* && "$args" != *'swtpm'* ]] || fail 'QEMU arguments contain TPM setup'
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
  run key enter
  run key grave
  run key bracketleft
  run key del
  python3 - "$WINDOWS_VM_TEST_LOG/qmp-requests" <<'PY'
import json, sys
rows=[json.loads(line) for line in open(sys.argv[1])]
keys=[r["arguments"]["keys"] for r in rows if r.get("execute")=="send-key"]
expected=[{"type":"qcode","data":k} for k in ("ctrl","alt","delete")]
assert keys[:2]==[expected, expected], keys
assert [x[0]["data"] for x in keys[2:]] == ["ret", "grave_accent", "bracket_left", "delete"]
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

qemuExitBeforeQmpCleansState() {
  if FAKE_QEMU_EXIT=1 run start >"$TMP/qemu-exit.out" 2>&1; then fail 'early QEMU exit unexpectedly started'; fi
  [[ ! -f "$STATE_DIR/qemu.pid" && ! -f "$STATE_DIR/run-dir" ]] || fail 'early QEMU exit left state files'
}

startupTimeoutCleansState() {
  WINDOWS_VM_START_POLLS=2 FAKE_QEMU_NO_QMP=1 timeout 5 bash "$ROOT/scripts/windows-vm.sh" start >"$TMP/start-timeout.out" 2>&1 && fail 'QMP startup timeout unexpectedly succeeded'
  [[ ! -f "$STATE_DIR/qemu.pid" && ! -f "$STATE_DIR/run-dir" ]] || fail 'startup timeout retained process state'
}

typeRejectsUnicodeBeforeSendingAnything() {
  run start
  : > "$WINDOWS_VM_TEST_LOG/qmp-requests"
  if run type 'oké' >"$TMP/type.out" 2>&1; then fail 'type accepted unsupported non-ASCII'; fi
  [[ $(grep -c '"execute": "send-key"' "$WINDOWS_VM_TEST_LOG/qmp-requests" || true) == 0 ]] || fail 'type sent a partial prefix before rejecting Unicode'
  run stop >/dev/null
}

silentQmpGreetingTimesOut() {
  run start
  local before=$SECONDS
  touch "$WINDOWS_VM_TEST_LOG/silent-greeting"
  if WINDOWS_VM_QMP_TIMEOUT=.2 run key ctrl >"$TMP/silent-qmp.out" 2>&1; then fail 'silent QMP greeting unexpectedly succeeded'; fi
  (( SECONDS - before < 3 )) || fail 'silent greeting exceeded deadline'
  sleep 1
  if WINDOWS_VM_QMP_TIMEOUT=.2 run type x >"$TMP/silent-type.out" 2>&1; then fail 'type accepted silent greeting'; fi
  rm -f "$WINDOWS_VM_TEST_LOG/silent-greeting"
  run stop >/dev/null
}

qemuArgsOmitTpmDevices() {
  run start
  local args
  args=$(<"$WINDOWS_VM_TEST_LOG/qemu")
  [[ "$args" != *tpm* && "$args" != *swtpm* ]] || fail 'QEMU arguments include TPM setup'
  run stop >/dev/null
}

typeUsesValidQcodesForPrintableAscii() {
  run start
  : > "$WINDOWS_VM_TEST_LOG/qmp-requests"
  run type " !\"#$%&'()*+,-./0123456789:;<=>?@ABCDEFGHIJKLMNOPQRSTUVWXYZ[\\]^_\`abcdefghijklmnopqrstuvwxyz{|}~"
  python3 - "$WINDOWS_VM_TEST_LOG/qmp-requests" <<'PY'
import json, sys
rows=[json.loads(line) for line in open(sys.argv[1])]
requests=[r for r in rows if r.get("execute")=="send-key"]
assert len(requests)==95, len(requests)
keys=[r["arguments"]["keys"] for r in requests]
plain = dict(zip(".,/-=;'\\[]`", "dot comma slash minus equal semicolon apostrophe backslash bracket_left bracket_right grave_accent".split()))
shift = dict(zip('!@#$%^&*()_+:?<>"{}|~', '1 2 3 4 5 6 7 8 9 0 minus equal semicolon slash comma dot apostrophe bracket_left bracket_right backslash grave_accent'.split()))
for char, actual in zip(map(chr, range(32,127)), keys):
    if char == ' ': expected = ['spc']
    elif char in shift: expected = ['shift', shift[char]]
    elif char in plain: expected = [plain[char]]
    elif char.isupper(): expected = ['shift', char.lower()]
    else: expected = [char]
    assert [k['data'] for k in actual] == expected, (char, actual, expected)
assert [{"type":"qcode","data":"bracket_left"}] in keys
assert [{"type":"qcode","data":"bracket_right"}] in keys
assert [{"type":"qcode","data":"grave_accent"}] in keys
assert all(r["arguments"].get("hold-time")==10 for r in requests)
PY
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

stoppingAfterQemuExitCleansState() {
  run start
  local qemu
  qemu=$(<"$STATE_DIR/qemu.pid")
  kill "$qemu"
  sleep .1
  run stop >/dev/null
  [[ ! -f "$STATE_DIR/qemu.pid" && ! -f "$STATE_DIR/run-dir" ]] || fail 'stop did not clear stale state'
}


stopEscalatesAndHandlesSpacedComm() {
  FAKE_QEMU_IGNORE_TERM=1 run start
  local pid; pid=$(<"$STATE_DIR/qemu.pid")
  [[ $(<"/proc/$pid/comm") == 'qemu test' ]] || fail 'fake process name did not contain a space'
  run stop >"$TMP/stop-escalate.out"
  [[ ! -e "$STATE_DIR/qemu.pid" ]] || fail 'SIGKILL stop retained state'
  [[ ! -e /proc/$pid/stat || $(ps -o stat= -p "$pid" | tr -d ' ') == Z* ]] || fail 'SIGKILL did not terminate QEMU'
}

staleRestartDoesNotHoldRunLock() {
  run start
  local pid old; pid=$(<"$STATE_DIR/qemu.pid"); old=$(<"$STATE_DIR/run-dir")
  kill "$pid"
  sleep .1
  run start
  timeout 2 flock -n "$old/lock" true || fail 'stale run lock inherited by new QEMU'
  run stop >/dev/null
}

stopKillsQemu() {
  run start
  local qemu
  qemu=$(<"$STATE_DIR/qemu.pid")
  run stop
  for _ in {1..20}; do
    kill -0 "$qemu" 2>/dev/null || break
    [[ $(ps -o stat= -p "$qemu" | tr -d ' ') == Z* ]] && break
    sleep .05
  done
  if kill -0 "$qemu" 2>/dev/null && [[ $(ps -o stat= -p "$qemu" | tr -d ' ') != Z* ]]; then fail 'QEMU remained alive after stop'; fi
}

for test in startUsesKvmOvmfAhci silentQmpGreetingTimesOut qemuArgsOmitTpmDevices typeUsesValidQcodesForPrintableAscii qmpCapabilityErrorsAbortCommand keyAcceptsPlusAndHyphenCombos qmpEventFloodHonorsDeadline qemuExitBeforeQmpCleansState startupTimeoutCleansState typeRejectsUnicodeBeforeSendingAnything stoppingAfterQemuExitCleansState concurrentStartsAreSerialized qmpCommandsUseValidKeysAndPng containersHaveIndependentState twoRunsGetSeparateOverlays baseIsNeverOpenedWritable startRefusesWhenRunning staleRestartDoesNotHoldRunLock stopEscalatesAndHandlesSpacedComm stopKillsQemu; do
  printf '%s ... ' "$test"
  "$test"
  printf 'PASS\n'
done
