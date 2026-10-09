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
echo "l1-readiness self-test OK"
