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
# Longest-prefix ownership: a file under a nested mapped dir belongs to the nested domain only.
T3="$(mktemp -d)"; trap 'rm -rf "$T" "$T2" "$T3"' EXIT
mkdir -p "$T3/scripts" "$T3/app/src/main/java/net/bible/android/control/outer/inner"
printf 'outer\tandroid/control/outer\ninner\tandroid/control/outer/inner\n' > "$T3/scripts/l1-readiness-domains.txt"
: > "$T3/scripts/l1-readiness-platform.txt"; : > "$T3/scripts/l1-readiness-done.txt"
echo 'fun x() = blockingDb { 1 }' > "$T3/app/src/main/java/net/bible/android/control/outer/inner/In.kt"
echo 'fun y() = 2' > "$T3/app/src/main/java/net/bible/android/control/outer/Out.kt"
out=$("$HERE/l1-readiness.sh" --root "$T3")
[ "$(echo "$out" | awk -F'\t' '$1=="outer"{print $2}')" = 0 ] || { echo "FAIL: outer counted inner's bridge"; exit 1; }
[ "$(echo "$out" | awk -F'\t' '$1=="inner"{print $2}')" = 1 ] || { echo "FAIL: inner lost its bridge"; exit 1; }

# @no-pending: any L1-pending anywhere (here an unmapped "view") fails --check; without it, passes.
mkdir -p "$T3/app/src/main/java/net/bible/android/view/x"
echo 'fun z() = blockingDb { 3 } // L1-pending(view)' > "$T3/app/src/main/java/net/bible/android/view/x/V.kt"
"$HERE/l1-readiness.sh" --root "$T3" --check >/dev/null 2>&1 || { echo "FAIL: --check failed without @no-pending"; exit 1; }
echo '@no-pending' >> "$T3/scripts/l1-readiness-done.txt"
"$HERE/l1-readiness.sh" --root "$T3" --check >/dev/null 2>&1 && { echo "FAIL: @no-pending missed an unmapped pending marker"; exit 1; }
echo 'fun z() = 3 // L1-pending: unknown-domain' > "$T3/app/src/main/java/net/bible/android/view/x/V.kt"
"$HERE/l1-readiness.sh" --root "$T3" --check >/dev/null 2>&1 && { echo "FAIL: @no-pending missed alternate marker formatting"; exit 1; }
rm "$T3/app/src/main/java/net/bible/android/view/x/V.kt"
"$HERE/l1-readiness.sh" --root "$T3" --check >/dev/null 2>&1 || { echo "FAIL: @no-pending failed with no markers"; exit 1; }

# @no-unmarked-bridges: an unmarked blockingAtEdge in an unmapped file fails --check; a marked one passes.
# The check stays off until the sentinel is present, and BlockingDb.kt (the definitions) is excluded by path.
T4="$(mktemp -d)"; trap 'rm -rf "$T" "$T2" "$T3" "$T4"' EXIT
mkdir -p "$T4/scripts" "$T4/app/src/main/java/net/bible/android/view/x" "$T4/app/src/main/java/net/bible/service/db"
: > "$T4/scripts/l1-readiness-domains.txt"; : > "$T4/scripts/l1-readiness-platform.txt"; : > "$T4/scripts/l1-readiness-done.txt"
echo 'fun unmarked() = blockingAtEdge { 1 }' > "$T4/app/src/main/java/net/bible/android/view/x/V.kt"
"$HERE/l1-readiness.sh" --root "$T4" --check >/dev/null 2>&1 \
  || { echo "FAIL: --check failed on an unmarked bridge before @no-unmarked-bridges"; exit 1; }
echo '@no-unmarked-bridges' >> "$T4/scripts/l1-readiness-done.txt"
"$HERE/l1-readiness.sh" --root "$T4" --check >/dev/null 2>&1 \
  && { echo "FAIL: @no-unmarked-bridges missed an unmarked blockingAtEdge"; exit 1; }
echo 'fun marked() = blockingAtEdge { 1 } // L1-edge: framework callback' > "$T4/app/src/main/java/net/bible/android/view/x/V.kt"
"$HERE/l1-readiness.sh" --root "$T4" --check >/dev/null 2>&1 \
  || { echo "FAIL: @no-unmarked-bridges failed on a marked blockingAtEdge"; exit 1; }
echo 'fun <T> blockingAtEdge(block: suspend () -> T): T = TODO()
fun <T> blockingDb(block: suspend () -> T): T = TODO()' > "$T4/app/src/main/java/net/bible/service/db/BlockingDb.kt"
"$HERE/l1-readiness.sh" --root "$T4" --check >/dev/null 2>&1 \
  || { echo "FAIL: @no-unmarked-bridges counted BlockingDb.kt's own definitions"; exit 1; }
echo "l1-readiness self-test OK"
