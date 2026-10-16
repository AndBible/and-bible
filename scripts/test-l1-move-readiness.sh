#!/usr/bin/env bash
# Proves l1-move-readiness.sh sees each kind of violation (memory: a-check-that-cannot-see-its-subject).
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
T="$(mktemp -d)"; trap 'rm -rf "$T"' EXIT
S="$T/app/src/main/java/net/bible"
mkdir -p "$T/scripts" "$S/android/control/demo" "$S/service/common" "$S/service/db"
printf 'demo\tandroid/control/demo\n' > "$T/scripts/l1-readiness-domains.txt"
printf 'android/control/demo/Host.kt\tandroid host, must be ignored\n' > "$T/scripts/l1-readiness-platform.txt"
echo demo > "$T/scripts/l1-move-done.txt"
printf 'service/db/DatabaseContainer.kt\tmoves with D2\n' > "$T/scripts/l1-move-allow.txt"
cat > "$S/service/common/Helpers.kt" <<'EOS'
package net.bible.service.common
fun String.displayName(): String = this
object Tools
EOS
cat > "$S/service/db/DatabaseContainer.kt" <<'EOS'
package net.bible.service.db
class DatabaseContainer
EOS
# platform file: full of violations, must not be counted
cat > "$S/android/control/demo/Host.kt" <<'EOS'
package net.bible.android.control.demo
import java.io.File
import android.app.Activity
import net.bible.service.common.Tools
fun h() = System.nanoTime()
EOS
cat > "$S/android/control/demo/Demo.kt" <<'EOS'
package net.bible.android.control.demo
import java.io.File
import org.apache.commons.lang3.StringUtils
import android.content.Context
import androidx.room3.Dao
import net.bible.service.common.displayName
import net.bible.service.common.Tools
import net.bible.service.db.DatabaseContainer
fun a() { synchronized(this) {} }
fun b() = System.currentTimeMillis()
fun c() = Foo::class.java
fun d() = System.nanoTime() // L1-common-ok: reason
fun e() = R.string.x
EOS
out=$("$HERE/l1-move-readiness.sh" --root "$T" --domain demo --check 2>/dev/null) && { echo "FAIL: --check passed on a dirty done domain"; exit 1; }
row=$(echo "$out" | awk -F'\t' '$1=="demo"')
expect=$'demo\t1\t3\t1\t2\t2'
[ "$row" = "$expect" ] || { echo "FAIL: got [$row] expected [$expect]"; exit 1; }
hdr=$(echo "$out" | head -1)
[ "$hdr" = $'domain\tjvm\tjvmapi\tjvm3p\tandroid\tapp' ] || { echo "FAIL: header [$hdr]"; exit 1; }

# Clean case: all zeros, --check passes.
cat > "$S/android/control/demo/Demo.kt" <<'EOS'
package net.bible.android.control.demo
import androidx.room3.Dao
import net.bible.service.db.DatabaseContainer
fun a() = 1
EOS
out=$("$HERE/l1-move-readiness.sh" --root "$T" --domain demo --check 2>/dev/null) \
  || { echo "FAIL: --check failed on a clean done domain"; exit 1; }
row=$(echo "$out" | awk -F'\t' '$1=="demo"')
expect=$'demo\t0\t0\t0\t0\t0'
[ "$row" = "$expect" ] || { echo "FAIL: clean got [$row] expected [$expect]"; exit 1; }

# A dirty domain that is NOT listed in l1-move-done.txt must not fail --check.
cat > "$S/android/control/demo/Demo.kt" <<'EOS'
package net.bible.android.control.demo
import java.io.File
EOS
: > "$T/scripts/l1-move-done.txt"
"$HERE/l1-move-readiness.sh" --root "$T" --check >/dev/null 2>&1 \
  || { echo "FAIL: --check failed on a dirty domain that is not done"; exit 1; }

# Declaration variants: a non-domain `fun interface`, annotated declarations and import lines with a
# trailing `;` or comment must all be indexed/resolved (each is counted once).
cat > "$S/service/common/Variants.kt" <<'EOS'
package net.bible.service.common
fun interface Callback { fun run() }
@JvmInline value class VC(val v: Int)
@Serializable class Ser
@Suppress("x") object Annotated
class Semi
class Commented
class Block
EOS
cat > "$S/android/control/demo/Demo.kt" <<'EOS'
package net.bible.android.control.demo
import net.bible.service.common.Callback
import net.bible.service.common.VC
import net.bible.service.common.Ser
import net.bible.service.common.Annotated
import net.bible.service.common.Semi;
import net.bible.service.common.Commented // trailing
import net.bible.service.common.Block /* trailing */
import net.bible.service.common.interface
EOS
out=$("$HERE/l1-move-readiness.sh" --root "$T" --domain demo 2>/dev/null)
row=$(echo "$out" | awk -F'\t' '$1=="demo"')
expect=$'demo\t0\t0\t0\t0\t7'
[ "$row" = "$expect" ] || { echo "FAIL: variants got [$row] expected [$expect]"; exit 1; }
echo "l1-move-readiness self-test OK"
