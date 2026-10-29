#!/usr/bin/env bash
set -euo pipefail

ROOT=$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)
TMP=$(mktemp -d)
trap 'for pidfile in "$TMP/vm-root/state/qemu.pid" "$TMP/vm-root/state/swtpm.pid" "$TMP/log/qmp.pid"; do if [[ -f "$pidfile" ]]; then kill "$(<"$pidfile")" 2>/dev/null || true; fi; done; rm -rf "$TMP"' EXIT
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
while (($#)); do
  if [[ "$1" == -qmp ]]; then
    socket_path=${2#unix:}; socket_path=${socket_path%%,*}
    python3 -c 'import socket,sys,time; s=socket.socket(socket.AF_UNIX); s.bind(sys.argv[1]); s.listen(); time.sleep(300)' "$socket_path" &
    echo $! > "$WINDOWS_VM_TEST_LOG/qmp.pid"
    exec sleep 300
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
  [[ "$args" == *'-device ahci'* ]] || fail 'AHCI controller missing'
  run stop >/dev/null
}

twoRunsGetSeparateOverlays() {
  run start
  local first
  first=$(<"$WINDOWS_VM_DIR/state/run-dir")/overlay.qcow2
  run stop >/dev/null
  run start
  local second
  second=$(<"$WINDOWS_VM_DIR/state/run-dir")/overlay.qcow2
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
  run stop >/dev/null
}

startRefusesWhenRunning() {
  run start
  if run start >"$TMP/duplicate.out" 2>&1; then fail 'second start unexpectedly succeeded'; fi
  grep -qi 'already running' "$TMP/duplicate.out" || fail 'duplicate start did not explain running state'
  run stop >/dev/null
}

stopKillsQemuAndSwtpm() {
  run start
  local qemu swtpm
  qemu=$(<"$WINDOWS_VM_DIR/state/qemu.pid")
  swtpm=$(<"$WINDOWS_VM_DIR/state/swtpm.pid")
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

for test in startUsesKvmOvmfTpmAhci twoRunsGetSeparateOverlays baseIsNeverOpenedWritable startRefusesWhenRunning stopKillsQemuAndSwtpm; do
  printf '%s ... ' "$test"
  "$test"
  printf 'PASS\n'
done
