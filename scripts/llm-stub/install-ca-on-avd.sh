#!/usr/bin/env bash
# Explicit serial AND AVD name required: never trust the shared host adb server.
set -euo pipefail
repo="$(cd "$(dirname "$0")/../.." && pwd)"
die() { printf 'install-ca: %s\n' "$*" >&2; exit 1; }
[ "$#" -ge 2 ] && [ "$#" -le 3 ] || die "usage: $0 emulator-SERIAL EXPECTED_AVD_NAME [ca.crt]"
serial="$1"
expected_avd="$2"
ca="${3:-$repo/.local/llm-stub/certs/ca.crt}"
[[ "$serial" =~ ^emulator-[0-9]+$ ]] || die 'only an explicit emulator serial is accepted'
[ -n "$expected_avd" ] || die 'expected AVD name is required'
[ -f "$ca" ] || die "CA not found: $ca"
hash="$(openssl x509 -in "$ca" -subject_hash_old -noout)"
[[ "$hash" =~ ^[a-f0-9]{8}$ ]] || die 'invalid certificate subject hash'
# The wrapper normally honours an inherited port; override it here, unconditionally.
adb_private() { ANDROID_ADB_SERVER_PORT=5038 timeout 30 "$repo/scripts/with-container-adb.sh" adb -s "$serial" "$@"; }
prop() { adb_private shell getprop "$1" | tr -d '\r'; }
[ "$(prop ro.kernel.qemu)" = 1 ] || die 'target is not a QEMU emulator'
case "$(prop ro.hardware)" in ranchu|goldfish) ;; *) die 'target hardware is not an Android AVD' ;; esac
[ "$(prop ro.boot.qemu.avd_name)" = "$expected_avd" ] || die 'AVD name does not match; refusing root or writes'
adb_private root
adb_private wait-for-device
[ "$(adb_private shell id -u | tr -d '\r')" = 0 ] || die 'AVD must be rootable (not a Play Store image)'
adb_private remount || die 'remount failed: boot with ANDBIBLE_EMU_WRITABLE_SYSTEM=1; if verity requires reboot, handle that explicitly and rerun'
# Probe the actual filesystem: some adb versions print a remount failure but exit zero.
adb_private shell "touch /system/etc/security/cacerts/.andbible-write-test && rm /system/etc/security/cacerts/.andbible-write-test" \
    || die 'system trust store is not writable; reboot a writable-system AVD and rerun'
adb_private push "$ca" "/data/local/tmp/$hash.0"
adb_private shell "cp /data/local/tmp/$hash.0 /system/etc/security/cacerts/$hash.0 && chmod 644 /system/etc/security/cacerts/$hash.0 && chown 0:0 /system/etc/security/cacerts/$hash.0 && chcon u:object_r:system_file:s0 /system/etc/security/cacerts/$hash.0"
sdk="$(prop ro.build.version.sdk)"
if [ "$sdk" -ge 34 ]; then
    # Android 14+ uses Conscrypt's APEX store. A bind mount in the shell alone is
    # invisible to apps forked from zygote; install into each zygote mount namespace.
    store=/data/local/tmp/andbible-llm-cacerts-$$
    apex=/apex/com.android.conscrypt/cacerts
    adb_private shell "test -d $apex && mkdir -p $store && cp $apex/* $store/ && cp /system/etc/security/cacerts/$hash.0 $store/ && chown -R 0:0 $store && chmod 755 $store && chmod 644 $store/* && chcon -R u:object_r:system_file:s0 $store && mount --bind $store $apex"
    found=0
    for zygote in zygote64 zygote; do
        pids="$(adb_private shell pidof "$zygote" | tr -d '\r')" || pids=
        for pid in $pids; do
            [[ "$pid" =~ ^[0-9]+$ ]] || die 'invalid zygote PID'
            adb_private shell "nsenter -t $pid -m -- mount --bind $store $apex && nsenter -t $pid -m -- test -r $apex/$hash.0"
            found=1
        done
    done
    [ "$found" = 1 ] || die 'no zygote found; APEX CA is NOT installed for apps'
fi
adb_private shell "rm /data/local/tmp/$hash.0"
printf 'Installed %s.0 on private AVD %s (%s).\n' "$hash" "$expected_avd" "$serial"
printf 'Force-stop and restart the test app before connecting. APEX mounts are temporary: rerun after every reboot.\n'
