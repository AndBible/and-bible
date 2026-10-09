#!/usr/bin/env bash
# Proves l1-readiness.sh sees each kind of violation (memory: a-check-that-cannot-see-its-subject).
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
T="$(mktemp -d)"; trap 'rm -rf "$T"' EXIT
mkdir -p "$T/scripts" "$T/app/src/main/java/net/bible/android/control/demo"
printf 'demo\tandroid/control/demo\n' > "$T/scripts/l1-readiness-domains.txt"
: > "$T/scripts/l1-readiness-platform.txt"
echo demo > "$T/scripts/l1-readiness-done.txt"
cat > "$T/app/src/main/java/net/bible/android/control/demo/Demo.kt" <<'EOS'
package net.bible.android.control.demo
import android.util.Log
import androidx.room3.RoomDatabase
import org.json.JSONObject
import java.io.File
import net.bible.android.view.activity.base.Dialogs
fun a() = blockingDb { 1 }
fun b() = blockingDb { 2 } // L1-edge: binder thread
fun c() = runBlocking { 3 }
private fun <T> blocking(core: suspend () -> T): T = TODO()
fun d() = blocking { 4 }
fun e() = CommonUtils.settings
fun f() = R.string.okay
fun g() = blockingDb { 5 } // L1-pending(demo)
EOS
out=$("$HERE/l1-readiness.sh" --root "$T" --domain demo --check 2>/dev/null) && { echo "FAIL: --check passed on a dirty done domain"; exit 1; }
row=$(echo "$out" | awk -F'\t' '$1=="demo"')
expect=$'demo\t3\t1\t1\t1\t3\t1\t1'
[ "$row" = "$expect" ] || { echo "FAIL: got [$row] expected [$expect]"; exit 1; }

# A done domain that is clean except for a caller still bridged with L1-pending(<itself>) must fail
# --check (callers of a done domain are converted, not bridged); the same domain without it passes.
T2="$(mktemp -d)"; trap 'rm -rf "$T" "$T2"' EXIT
mkdir -p "$T2/scripts" "$T2/app/src/main/java/net/bible/android/control/clean" "$T2/app/src/main/java/net/bible/android/view/other"
printf 'clean\tandroid/control/clean\n' > "$T2/scripts/l1-readiness-domains.txt"
: > "$T2/scripts/l1-readiness-platform.txt"
echo clean > "$T2/scripts/l1-readiness-done.txt"
cat > "$T2/app/src/main/java/net/bible/android/control/clean/Clean.kt" <<'EOS'
package net.bible.android.control.clean
suspend fun clean() = 1
EOS
"$HERE/l1-readiness.sh" --root "$T2" --domain clean --check >/dev/null 2>&1 \
  || { echo "FAIL: --check failed on a clean done domain"; exit 1; }
cat > "$T2/app/src/main/java/net/bible/android/view/other/Caller.kt" <<'EOS'
package net.bible.android.view.other
fun caller() = blockingDb { clean() } // L1-pending(clean)
EOS
out=$("$HERE/l1-readiness.sh" --root "$T2" --domain clean --check 2>/dev/null) \
  && { echo "FAIL: --check passed on a done domain with a pending caller"; exit 1; }
row=$(echo "$out" | awk -F'\t' '$1=="clean"')
expect=$'clean\t0\t0\t1\t0\t0\t0\t0'
[ "$row" = "$expect" ] || { echo "FAIL: got [$row] expected [$expect]"; exit 1; }
echo "l1-readiness self-test OK"
