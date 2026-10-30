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
if [[ "$1" == create ]]; then
  args=("$@")
  for ((i=0; i<${#args[@]}; i++)); do
    if [[ ${args[i]} == -b && ${args[i+1]} != /* ]]; then
      printf 'relative backing file rejected: %s\n' "${args[i+1]}" >&2
      exit 1
    fi
  done
  last=$((${#args[@]} - 1))
  [[ ${args[$last]} =~ ^[0-9]+[KMGTP]?$ ]] && ((last-=1))
  touch "${args[$last]}"
fi
SH
cat > "$FAKE/qemu-system-x86_64" <<'SH'
#!/usr/bin/env bash
printf '%s\n' "$*" >> "$WINDOWS_VM_TEST_LOG/qemu"
if [[ ${FAKE_QEMU_EXIT:-0} == 1 ]]; then exit 0; fi
if [[ ${FAKE_QEMU_NO_QMP:-0} == 1 ]]; then exec sleep 300; fi
while (($#)); do
  if [[ "$1" == -qmp ]]; then
    socket_path=${2#unix:}; socket_path=${socket_path%%,*}
    exec python3 -c 'import json,socket,sys,os,signal,ctypes; signal.signal(signal.SIGTERM, signal.SIG_IGN if os.environ.get("FAKE_QEMU_IGNORE_TERM") else (lambda *_: sys.exit(0))) if os.environ.get("FAKE_QEMU_IGNORE_TERM") or os.environ.get("FAKE_QEMU_CLEAN_EXIT") else None; ctypes.CDLL(None).prctl(15, b"qemu test", 0, 0, 0) if os.environ.get("FAKE_QEMU_IGNORE_TERM") else None; p=sys.argv[1]; s=socket.socket(socket.AF_UNIX); s.bind(p); s.listen();
if os.environ.get("FAKE_QEMU_IGNORE_TERM"): open(os.environ["WINDOWS_VM_TEST_LOG"]+"/ignore-term-ready", "w").close()
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
cat > "$FAKE/curl" <<'SH'
#!/usr/bin/env bash
while (($#)); do if [[ $1 == -o ]]; then printf fake-msi > "$2"; exit; fi; shift; done
exit 1
SH
cat > "$FAKE/sha256sum" <<'SH'
#!/usr/bin/env bash
[[ ${FAKE_BAD_MSI:-0} != 1 ]]
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

qmpPartialLinesHonorDeadline() {
  python3 - "$ROOT/scripts/windows-vm.sh" "$TMP/partial-qmp" <<'PY'
import json, os, pathlib, socket, subprocess, sys, threading, time
script, root = sys.argv[1:]
failures = []
for stage in ("greeting", "reply"):
    for mode, argument in (("shot", "partial"), ("key", "ctrl"), ("type", "x")):
        directory = pathlib.Path(root) / (stage + "-" + mode)
        run = directory / "run"
        run.mkdir(parents=True)
        state = directory / "state" / os.environ["WINDOWS_VM_ID"]
        state.mkdir(parents=True)
        (state / "run-dir").write_text(str(run))
        listener = socket.socket(socket.AF_UNIX)
        listener.bind(str(run / "qmp.sock"))
        listener.listen()
        def serve(listener=listener, stage=stage):
            connection, _ = listener.accept()
            with connection:
                try:
                    if stage == "reply":
                        connection.sendall(b'{"QMP":{}}\r\n')
                        stream = connection.makefile("rb")
                        request = json.loads(stream.readline())
                        connection.sendall(json.dumps({"return": {}, "id": request["id"]}).encode() + b'\r\n')
                        json.loads(stream.readline())
                    connection.sendall(b'{"QMP":' if stage == "greeting" else b'{"return":')
                    # Bytes keep arriving inside the socket timeout, but never finish a line.
                    for _ in range(100):
                        connection.sendall(b' ')
                        time.sleep(.02)
                except OSError:
                    pass
        server = threading.Thread(target=serve, daemon=True)
        server.start()
        env = dict(os.environ, WINDOWS_VM_DIR=str(directory), WINDOWS_VM_QMP_TIMEOUT=".2")
        before = time.monotonic()
        try:
            result = subprocess.run(["bash", script, mode, argument], env=env, capture_output=True, timeout=.8)
            elapsed = time.monotonic() - before
            if result.returncode == 0 or b"QMP response deadline exceeded" not in result.stderr or elapsed >= .8:
                failures.append(f"{stage}/{mode}: did not report the absolute deadline ({elapsed:.3f}s): {result.stderr!r}")
        except subprocess.TimeoutExpired:
            failures.append(f"{stage}/{mode}: exceeded .8s with a .2s QMP deadline")
        server.join(timeout=.2)
        listener.close()
assert not failures, "\n".join(failures)
PY
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
  rm -f "$WINDOWS_VM_TEST_LOG/ignore-term-ready"
  FAKE_QEMU_IGNORE_TERM=1 run start
  local pid state; pid=$(<"$STATE_DIR/qemu.pid")
  for _ in {1..100}; do
    [[ -e "$WINDOWS_VM_TEST_LOG/ignore-term-ready" ]] && break
    sleep .01
  done
  [[ -e "$WINDOWS_VM_TEST_LOG/ignore-term-ready" ]] || fail 'fake QEMU did not become ready'
  [[ $(<"/proc/$pid/comm") == 'qemu test' ]] || fail 'fake process name did not contain a space'
  run stop >"$TMP/stop-escalate.out"
  [[ ! -e "$STATE_DIR/qemu.pid" ]] || fail 'SIGKILL stop retained state'
  state=$(python3 - "$pid" <<'PY'
import sys
try:
    with open(f"/proc/{sys.argv[1]}/stat") as proc:
        data = proc.read()
except FileNotFoundError:
    print("gone")
else:
    end = data.rfind(")")
    if end < 0:
        raise ValueError("invalid process stat")
    print(data[end + 2:].split()[0])
PY
  ) || fail 'could not inspect QEMU process state'
  [[ "$state" == gone || "$state" == Z ]] || fail 'SIGKILL did not terminate QEMU'
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

longRunPathUsesShortQmpSocket() {
  local longroot="$TMP/$(printf '%090d' 0)" run_dir socket
  WINDOWS_VM_DIR="$longroot" run start >/dev/null
  run_dir=$(<"$longroot/state/$WINDOWS_VM_ID/run-dir")
  socket=$(<"$run_dir/qmp-path")
  [[ ${#socket} -lt 108 && -S "$socket" ]] || fail 'QMP socket path is too long or unavailable'
  WINDOWS_VM_DIR="$longroot" run key enter >/dev/null
  WINDOWS_VM_DIR="$longroot" run stop >/dev/null
  [[ ! -e "$socket" ]] || fail 'QMP socket was not cleaned up'
}

relativePathsResolveBackingAndScreenshots() {
  local root="$TMP/relative-vm" iso="$TMP/relative.iso" output="$TMP/relative.out"
  printf fake > "$iso"
  cat > "$FAKE/7z" <<'SH'
#!/usr/bin/env bash
printf 'sources/install.wim\nsources/boot.wim\n'
SH
  cat > "$FAKE/ssh" <<'SH'
#!/usr/bin/env bash
exit 1
SH
  chmod +x "$FAKE/7z" "$FAKE/ssh"
  (
    cd "$TMP"
    touch relative-base.qcow2
    WINDOWS_VM_DIR=relative-vm WINDOWS_VM_BASE=relative-base.qcow2 run start
    WINDOWS_VM_DIR=relative-vm WINDOWS_VM_BASE=relative-base.qcow2 run stop >/dev/null
    touch relative-vm/base.qcow2
    WINDOWS_VM_DIR=relative-vm WINDOWS_VM_BASE= run start
    WINDOWS_VM_DIR=relative-vm WINDOWS_VM_BASE= run stop >/dev/null
    rm relative-vm/base.qcow2 relative-base.qcow2
    if WINDOWS_VM_DIR=relative-vm WINDOWS_VM_BASE=relative-base.qcow2 WINDOWS_VM_ISO=relative.iso WINDOWS_VM_INSTALL_TIMEOUT=2 run install >"$output" 2>&1; then fail 'installer did not time out'; fi
    grep -qi 'timed out' "$output" || fail 'relative ISO install did not reach timeout'
    [[ $(readlink "$root/install-shots") == /* && -f "$root/install-shots/final.png" ]] || fail 'relative install screenshot link is broken'
  )
  rm -f "$FAKE/7z" "$FAKE/ssh"
}

installTimeoutPreservesFinalScreenshot() {
  local root="$TMP/timeout-vm" iso="$TMP/fake.iso" output="$TMP/install-timeout.out"
  printf 'fake' > "$iso"
  cat > "$FAKE/7z" <<'SH'
#!/usr/bin/env bash
printf 'sources/install.wim\nsources/boot.wim\n'
SH
  cat > "$FAKE/ssh" <<'SH'
#!/usr/bin/env bash
exit 1
SH
  chmod +x "$FAKE/7z" "$FAKE/ssh"
  if WINDOWS_VM_DIR="$root" WINDOWS_VM_BASE="$root/base.qcow2" WINDOWS_VM_ISO="$iso" WINDOWS_VM_INSTALL_TIMEOUT=2 run install >"$output" 2>&1; then fail 'installer did not time out'; fi
  [[ -f "$root/install-shots/final.png" ]] || fail 'timeout final screenshot missing'
  grep -Fq "$root/install-shots/final.png" "$output" || fail 'timeout did not report final screenshot path'
  rm -f "$FAKE/7z" "$FAKE/ssh"
}

installSuccessCleansStateAndPromotesBase() {
  local root="$TMP/success-vm" iso="$TMP/success.iso" output="$TMP/success.out" run_dir
  printf fake > "$iso"
  cat > "$FAKE/7z" <<'SH'
#!/usr/bin/env bash
printf 'sources/install.wim\nsources/boot.wim\n'
SH
  cat > "$FAKE/ssh" <<'SH'
#!/usr/bin/env bash
for arg in "$@"; do
  if [[ "$arg" == UserKnownHostsFile=* ]]; then printf 'fake host key\n' > "${arg#*=}"; fi
done
if [[ "$*" == *shutdown.exe* ]]; then kill "$(<"$WINDOWS_VM_DIR/state/$WINDOWS_VM_ID/qemu.pid")"; fi
exit 0
SH
  chmod +x "$FAKE/7z" "$FAKE/ssh"
  if ! FAKE_QEMU_CLEAN_EXIT=1 WINDOWS_VM_DIR="$root" WINDOWS_VM_BASE="$root/base.qcow2" WINDOWS_VM_ISO="$iso" WINDOWS_VM_INSTALL_TIMEOUT=15 run install >"$output" 2>&1; then fail "successful install exited nonzero: $(<"$output")"; fi
  [[ -f "$root/base.qcow2" && -f "$root/base.qcow2.vars" ]] || fail 'successful install did not promote base and firmware'
  [[ $(stat -c %a "$root/base.qcow2") == 400 ]] || fail 'base image not read-only'
  [[ ! -e "$root/state/$WINDOWS_VM_ID/qemu.pid" && ! -e "$root/state/$WINDOWS_VM_ID/run-dir" ]] || fail 'successful install left stale state'
  run_dir=$(find "$root/runs/$WINDOWS_VM_ID" -mindepth 1 -maxdepth 1 -type d -print -quit)
  [[ ! -e "$run_dir/qmp-path" ]] || fail 'successful install left QMP metadata'
  rm -f "$FAKE/7z" "$FAKE/ssh"
}

installRejectsCorruptMsi() {
  local root="$TMP/corrupt-vm" iso="$TMP/corrupt.iso" output="$TMP/corrupt.out"
  printf fake > "$iso"
  cat > "$FAKE/7z" <<'SH'
#!/usr/bin/env bash
printf 'sources/install.wim\nsources/boot.wim\n'
SH
  chmod +x "$FAKE/7z"
  if FAKE_BAD_MSI=1 WINDOWS_VM_DIR="$root" WINDOWS_VM_BASE="$root/base.qcow2" WINDOWS_VM_ISO="$iso" run install >"$output" 2>&1; then fail 'corrupt MSI accepted'; fi
  grep -q 'SHA256 mismatch' "$output" || fail 'checksum error not reported'
  [[ ! -e "$root/base.qcow2" ]] || fail 'corrupt MSI promoted base'
  rm -f "$FAKE/7z"
}

installerUsesPinnedOfflineSshAndHyperv() {
  local script="$ROOT/scripts/windows-vm.sh" ps="$ROOT/scripts/windows-vm/firstlogon.ps1"
  [[ $(grep -c -- '-cpu "$CPU_MODEL"' "$script") == 2 ]] || fail 'Hyper-V CPU model not used on every boot'
  grep -q 'hv-relaxed.*hv-vapic.*hv-spinlocks.*hv-time.*hv-vpindex.*hv-synic.*hv-stimer.*hv-frequencies' "$script" || fail 'Hyper-V features missing'
  grep -q '788d8643464a01eb63640a2fd746f9ed23d106b5a901a35f88dc8cd40e446ac9' "$script" || fail 'official MSI digest not pinned'
  grep -q 'sha256sum' "$script" || fail 'MSI digest is not checked'
  grep -qi 'msiexec' "$ps" || fail 'MSI not installed locally'
  if grep -qi 'Add-WindowsCapability' "$ps"; then fail 'guest network capability installation remains'; fi
}

installShutdownTimeoutsPreserveDiagnostics() {
  local root iso="$TMP/shutdown.iso" output mode
  printf 'fake' > "$iso"
  cat > "$FAKE/7z" <<'SH'
#!/usr/bin/env bash
printf 'sources/install.wim\nsources/boot.wim\n'
SH
  cat > "$FAKE/ssh" <<'SH'
#!/usr/bin/env bash
if [[ "$*" == *shutdown.exe* ]]; then
  if [[ "$FAKE_SHUTDOWN_MODE" == hang ]]; then sleep 20; fi
  exit 0
fi
if [[ "$FAKE_SHUTDOWN_MODE" == late ]]; then sleep 3; fi
exit 0
SH
  chmod +x "$FAKE/7z" "$FAKE/ssh"
  for mode in late hang noexit; do
    root="$TMP/shutdown-$mode"; output="$TMP/shutdown-$mode.out"
    if FAKE_SHUTDOWN_MODE="$mode" WINDOWS_VM_DIR="$root" WINDOWS_VM_BASE="$root/base.qcow2" WINDOWS_VM_ISO="$iso" WINDOWS_VM_INSTALL_TIMEOUT=3 run install >"$output" 2>&1; then fail "$mode install succeeded"; fi
    [[ -f "$root/install-shots/final.png" ]] || fail "$mode final screenshot missing"
    grep -Fq "$root/install-shots/final.png" "$output" || fail "$mode screenshot path not reported"
    [[ ! -e "$root/base.qcow2" ]] || fail "$mode promoted base"
  done
  rm -f "$FAKE/7z" "$FAKE/ssh"
}

answerIsoExposesWindowsFilenames() {
  local source="$TMP/iso-source" iso="$TMP/test-answer.iso" listing
  mkdir -p "$source"
  cp "$ROOT/scripts/windows-vm/autounattend.xml" "$source/autounattend.xml"
  cp "$ROOT/scripts/windows-vm/firstlogon.ps1" "$source/firstlogon.ps1"
  touch "$source/authorized_key.pub"
  xorriso -as mkisofs -quiet -J -iso-level 3 -V ABANSWER -o "$iso" "$source" 2>/dev/null
  listing=$(xorriso -read_fs norock -indev "$iso" -find / -type f 2>/dev/null)
  [[ "$listing" == *autounattend.xml* && "$listing" == *firstlogon.ps1* && "$listing" == *authorized_key.pub* ]] || fail 'Joliet filenames are truncated'
}

installerReviewRegressions() {
  python3 - "$ROOT/scripts/windows-vm.sh" "$ROOT/scripts/windows-vm/firstlogon.ps1" <<'PY'
import sys
script, ps = (open(p).read() for p in sys.argv[1:])
assert '-J -iso-level 3' in script, 'answer ISO lacks long-name extensions'
assert '"$VM_DIR/credentials"' in script, 'missing credentials file'
assert 'timeout ' in script, 'SSH commands are not bounded'
assert 'known_hosts' in script and 'run_dir/known_hosts' in script, 'missing per-install host pin'
assert 'AutoLogonCount' in ps and 'AutoAdminLogon' in ps and 'DefaultPassword' in ps, 'autologon not cleared'
assert 'install-shots' in script and 'final.png' in script, 'missing stable screenshot interface'
PY
}

installAnswerHasRequiredConfiguration() {
  python3 - "$ROOT/scripts/windows-vm/autounattend.xml" <<'PY'
import sys, xml.etree.ElementTree as ET
root = ET.parse(sys.argv[1]).getroot()
text = ' '.join(root.itertext())
for expected in ('BypassTPMCheck', 'BypassSecureBootCheck', 'BypassRAMCheck', 'fi-FI', 'abtest', 'ABANSWER'):
    assert expected in text, expected
assert text.count('REG_DWORD /d 1') == 3
PY
}

installRejectsMissingIso() {
  if WINDOWS_VM_BASE="$TMP/no-base" WINDOWS_VM_ISO="$TMP/missing.iso" run install >"$TMP/install-missing.out" 2>&1; then fail 'install accepted missing ISO'; fi
  grep -qi 'ISO not readable' "$TMP/install-missing.out" || fail 'missing ISO not explained'
  [[ ! -e "$TMP/no-base" ]] || fail 'missing ISO created base'
}

installRefusesExistingBase() {
  if run install >"$TMP/install-existing.out" 2>&1; then fail 'install overwrote existing base'; fi
  grep -qi 'base.*exist' "$TMP/install-existing.out" || fail 'install did not report existing base'
}

resetLogs() { rm -f "$WINDOWS_VM_TEST_LOG/qemu" "$WINDOWS_VM_TEST_LOG/qemu-img" "$WINDOWS_VM_TEST_LOG/ssh" "$WINDOWS_VM_TEST_LOG/scp" "$WINDOWS_VM_TEST_LOG/pushed.tar"; }
installFakeSshScp() {
  cat > "$FAKE/ssh" <<'SH'
#!/usr/bin/env bash
printf '%s\n' "$*" >> "$WINDOWS_VM_TEST_LOG/ssh"
exit "${FAKE_SSH_EXIT:-0}"
SH
  cat > "$FAKE/scp" <<'SH'
#!/usr/bin/env bash
printf '%s\n' "$*" >> "$WINDOWS_VM_TEST_LOG/scp"
for a in "$@"; do [[ "$a" == */src.tar && -f "$a" ]] && cp "$a" "$WINDOWS_VM_TEST_LOG/pushed.tar"; done
exit 0
SH
  chmod +x "$FAKE/ssh" "$FAKE/scp"
}
removeFakeSshScp() { rm -f "$FAKE/ssh" "$FAKE/scp"; }

devOverlayIsPersistentAndSeparate() {
  resetLogs
  local dev="$WINDOWS_VM_DIR/dev-$WINDOWS_VM_ID.qcow2"
  run dev-start
  [[ -f "$dev" && -f "$WINDOWS_VM_DIR/dev-$WINDOWS_VM_ID.vars" ]] || fail 'dev overlay or vars not created'
  [[ $(grep -c . "$WINDOWS_VM_TEST_LOG/qemu-img") == 1 ]] || fail 'dev overlay not created exactly once'
  grep -q -- "-b $WINDOWS_VM_BASE" "$WINDOWS_VM_TEST_LOG/qemu-img" || fail 'dev overlay not backed by base'
  grep -q "file=$dev," "$WINDOWS_VM_TEST_LOG/qemu" || fail 'QEMU does not use the dev overlay'
  grep -q "file=$WINDOWS_VM_DIR/dev-$WINDOWS_VM_ID.vars" "$WINDOWS_VM_TEST_LOG/qemu" || fail 'QEMU does not use persistent vars'
  [[ -d "$(<"$STATE_DIR/run-dir")" ]] || fail 'no run dir'
  if run start >/dev/null 2>&1; then fail 'start allowed while dev VM running'; fi
  run stop >/dev/null
  [[ -f "$dev" ]] || fail 'stop deleted dev overlay'
  : > "$WINDOWS_VM_TEST_LOG/qemu-img"
  run dev-start
  [[ ! -s "$WINDOWS_VM_TEST_LOG/qemu-img" ]] || fail 'dev overlay recreated on second dev-start'
  run stop >/dev/null
  : > "$WINDOWS_VM_TEST_LOG/qemu"
  run start
  grep -q 'overlay.qcow2' "$WINDOWS_VM_TEST_LOG/qemu" || fail 'plain start does not use a run overlay'
  grep -q "dev-$WINDOWS_VM_ID" "$WINDOWS_VM_TEST_LOG/qemu" && fail 'plain start used the dev overlay'
  run stop >/dev/null
  rm -f "$dev" "$WINDOWS_VM_DIR/dev-$WINDOWS_VM_ID.vars"
  return 0
}

pushRepoExcludesBuildAndGit() {
  resetLogs; installFakeSshScp
  local repo="$TMP/pushrepo" sub
  rm -rf "$repo"; mkdir -p "$repo"
  git -C "$repo" init -q
  git -C "$repo" config user.email t@t; git -C "$repo" config user.name t
  mkdir -p "$repo/app/src" "$repo/build" "$repo/app/build" "$repo/app/bibleview-js/dist" "$repo/node_modules/x" "$repo/.local" "$repo/.gradle"
  echo a > "$repo/app/src/A.kt"; echo b > "$repo/tracked name.txt"
  echo '*.secret' > "$repo/.gitignore"; echo '/app/bibleview-js/dist/' >> "$repo/.gitignore"
  git -C "$repo" add -A; git -C "$repo" commit -qm init
  echo u > "$repo/untracked.txt"; echo s > "$repo/x.secret"
  echo z > "$repo/build/out.bin"; echo z > "$repo/app/build/out.bin"
  echo z > "$repo/node_modules/x/i.js"; echo z > "$repo/.local/l"; echo z > "$repo/.gradle/g"
  echo js > "$repo/app/bibleview-js/dist/index.html"
  for sub in jsword kdom; do
    mkdir -p "$repo/$sub/src" "$repo/$sub/build"
    git -C "$repo/$sub" init -q
    echo j > "$repo/$sub/src/J.java"; echo ig > "$repo/$sub/.gitignore"; echo ig > "$repo/$sub/ig"
    echo z > "$repo/$sub/build/o"
    git -C "$repo/$sub" config user.email t@t; git -C "$repo/$sub" config user.name t
    git -C "$repo/$sub" add src .gitignore; git -C "$repo/$sub" commit -qm i
    echo n > "$repo/$sub/new.txt"
  done
  run push-repo "$repo" >/dev/null
  [[ -s "$WINDOWS_VM_TEST_LOG/pushed.tar" ]] || fail 'no tarball was sent'
  local list; list=$(tar -tf "$WINDOWS_VM_TEST_LOG/pushed.tar")
  local want
  for want in app/src/A.kt 'tracked name.txt' untracked.txt .gitignore jsword/src/J.java jsword/new.txt kdom/src/J.java kdom/new.txt app/bibleview-js/dist/index.html; do
    grep -qxF "$want" <<<"$list" || fail "tarball lacks $want"
  done
  grep -qE '(^|/)(\.git|build|\.gradle|node_modules|\.local)(/|$)' <<<"$list" && fail 'tarball contains excluded directory'
  grep -qE 'x\.secret|/ig$' <<<"$list" && fail 'tarball contains ignored file'
  grep -q 'C:/abtest/src.tar' "$WINDOWS_VM_TEST_LOG/scp" || fail 'tarball not copied to guest'
  grep -q 'tar.exe -xf' "$WINDOWS_VM_TEST_LOG/ssh" || fail 'guest unpack missing'
  grep -q 'Remove-Item' "$WINDOWS_VM_TEST_LOG/ssh" && fail 'guest dir removed without --clean'
  [[ ! -e "$WINDOWS_VM_DIR/out/$WINDOWS_VM_ID/src.tar" ]] || fail 'local tarball not removed'
  run push-repo --clean "$repo" >/dev/null
  grep -q 'Remove-Item -Recurse -Force' "$WINDOWS_VM_TEST_LOG/ssh" || fail '--clean did not remove guest dir'
  removeFakeSshScp
}

devGradleQuotesArgsAndPropagatesExit() {
  resetLogs; installFakeSshScp
  local rc=0
  FAKE_SSH_EXIT=3 run dev-gradle :desktopApp:test --tests "it's a test" >/dev/null 2>&1 || rc=$?
  [[ $rc == 3 ]] || fail "dev-gradle exit code was $rc, expected 3"
  grep -q "gradlew.bat ':desktopApp:test' '--tests' 'it''s a test'" "$WINDOWS_VM_TEST_LOG/ssh" || fail 'args not quoted for PowerShell'
  grep -q 'JAVA_HOME' "$WINDOWS_VM_TEST_LOG/ssh" || fail 'JAVA_HOME not set'
  grep -q 'build.compose.binaries' "$WINDOWS_VM_TEST_LOG/ssh" || fail 'artifacts not fetched after failure'
  rc=0; run dev-gradle tasks >/dev/null 2>&1 || rc=$?
  [[ $rc == 0 ]] || fail 'dev-gradle success exit code lost'
  removeFakeSshScp
}

devResetRefusesWhileRunning() {
  local dev="$WINDOWS_VM_DIR/dev-$WINDOWS_VM_ID.qcow2"
  run dev-start
  if run dev-reset >/dev/null 2>&1; then fail 'dev-reset allowed while running'; fi
  [[ -f "$dev" ]] || fail 'dev-reset deleted overlay while running'
  run stop >/dev/null
  run dev-reset >/dev/null
  [[ ! -e "$dev" && ! -e "$WINDOWS_VM_DIR/dev-$WINDOWS_VM_ID.vars" ]] || fail 'dev-reset did not delete overlay and vars'
}

for test in devOverlayIsPersistentAndSeparate pushRepoExcludesBuildAndGit devGradleQuotesArgsAndPropagatesExit devResetRefusesWhileRunning relativePathsResolveBackingAndScreenshots installSuccessCleansStateAndPromotesBase installRejectsCorruptMsi installerUsesPinnedOfflineSshAndHyperv installShutdownTimeoutsPreserveDiagnostics installTimeoutPreservesFinalScreenshot answerIsoExposesWindowsFilenames installerReviewRegressions longRunPathUsesShortQmpSocket installAnswerHasRequiredConfiguration installRejectsMissingIso installRefusesExistingBase qmpPartialLinesHonorDeadline startUsesKvmOvmfAhci silentQmpGreetingTimesOut qemuArgsOmitTpmDevices typeUsesValidQcodesForPrintableAscii qmpCapabilityErrorsAbortCommand keyAcceptsPlusAndHyphenCombos qmpEventFloodHonorsDeadline qemuExitBeforeQmpCleansState startupTimeoutCleansState typeRejectsUnicodeBeforeSendingAnything stoppingAfterQemuExitCleansState concurrentStartsAreSerialized qmpCommandsUseValidKeysAndPng containersHaveIndependentState twoRunsGetSeparateOverlays baseIsNeverOpenedWritable startRefusesWhenRunning staleRestartDoesNotHoldRunLock stopEscalatesAndHandlesSpacedComm stopKillsQemu; do
  printf '%s ... ' "$test"
  "$test"
  printf 'PASS\n'
done
