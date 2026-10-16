#!/usr/bin/env bash
# Proves andbible-emu.sh's one-emulator gate and GPU-buffer accounting see their subject
# (memory: a-check-that-cannot-see-its-subject). Needs no emulator: adb and pgrep are stubbed and
# a python process named qemu-system-x86 stands in for qemu.
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
T="$(mktemp -d)"
fake=
trap '[ -n "$fake" ] && kill "$fake" 2>/dev/null; rm -rf "$T"' EXIT
fail() { echo "FAIL: $*"; exit 1; }

# adb: no device attached (an emulator still booting, offline, or started outside the script).
cat > "$T/adb" <<'EOS'
#!/usr/bin/env bash
[ "${1:-}" = devices ] && echo "List of devices attached"
exit 0
EOS
# pgrep: the stand-in for qemu; everything else goes to the real pgrep.
cat > "$T/pgrep" <<EOS
#!/usr/bin/env bash
if [ "\$*" = "-x qemu-system-x86" ]; then cat "$T/qemu.pid" 2>/dev/null || exit 1; exit 0; fi
exec $(command -v pgrep) "\$@"
EOS
chmod +x "$T/adb" "$T/pgrep"
# ANDROID_HOME points at an empty SDK so a boot that gets past the gate dies instead of launching.
emu() { PATH="$T:$PATH" ANDROID_HOME="$T/sdk" ANDROID_AVD_HOME="$T/avd" "$HERE/andbible-emu.sh" "$@" 2>&1; }

# 1. No qemu: health says so.
out=$(emu health) || fail "health without qemu exited nonzero: $out"
grep -q "no qemu running" <<<"$out" || fail "health without qemu: $out"

# A qemu stand-in: an -avd argument, and two descriptors on one render-node open file (one DRM
# client), which must be counted once.
# Only some drivers report DRM fdinfo (i915 does; nvidia does not): pick a node that does.
node=$(python3 - <<'EOS'
import glob, os
for p in sorted(glob.glob("/dev/dri/renderD*")):
    try:
        fd = os.open(p, os.O_RDWR)
    except OSError:
        continue
    if "drm-client-id" in open("/proc/self/fdinfo/%d" % fd).read():
        print(p)
        break
EOS
)
python3 - "$node" -avd fake_avd_api36 <<'EOS' &
import os, sys, time
if sys.argv[1]:
    fd = os.open(sys.argv[1], os.O_RDWR)
    os.dup(fd)
time.sleep(60)
EOS
fake=$!
echo "$fake" > "$T/qemu.pid"
sleep 0.5

# 2. boot refuses while a qemu runs even though adb lists no device, and names the AVD.
out=$(emu boot) && fail "boot started a second emulator: $out"
grep -q "already running" <<<"$out" || fail "boot gate message: $out"
grep -q "fake_avd_api36" <<<"$out" || fail "boot gate does not name the running AVD: $out"

# 3. health reports the qemu's GPU buffers (they are not part of its RSS).
out=$(emu health) || fail "health with qemu exited nonzero: $out"
if [ -n "$node" ]; then
  grep -qE "GPU buffers [0-9]+ MB \([0-9]+ MB resident; 1 DRM client\)" <<<"$out" || fail "health GPU buffers: $out"
else
  grep -q "GPU buffers: not reported" <<<"$out" || fail "health GPU buffers without node: $out"
fi
echo "ANDBIBLE-EMU OK"
