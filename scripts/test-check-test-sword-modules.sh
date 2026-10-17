#!/usr/bin/env bash
# Proves check-test-sword-modules.sh sees each kind of mismatch and that --fix removes only extras
# (memory: a-check-that-cannot-see-its-subject).
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
CHECK="$HERE/check-test-sword-modules.sh"
T="$(mktemp -d)"; trap 'rm -rf "$T"' EXIT

# Test zip with two modules: a.conf (ztext/a) and b.conf (ztext/b).
mkdir -p "$T/stage/mods.d" "$T/stage/modules/texts/ztext/a" "$T/stage/modules/texts/ztext/b"
printf '[a]\nDataPath=./modules/texts/ztext/a/\n' > "$T/stage/mods.d/a.conf"
printf '[b]\nDataPath=./modules/texts/ztext/b/\n' > "$T/stage/mods.d/b.conf"
echo a > "$T/stage/modules/texts/ztext/a/data"
echo b > "$T/stage/modules/texts/ztext/b/data"
(cd "$T/stage" && python3 -m zipfile -c "$T/testmods.zip" mods.d modules >/dev/null)

# install <dir>: a sword dir holding exactly the two zip modules.
install() {
  mkdir -p "$1/mods.d" "$1/modules/texts/ztext/a" "$1/modules/texts/ztext/b"
  cp "$T/stage/mods.d/a.conf" "$T/stage/mods.d/b.conf" "$1/mods.d/"
  cp "$T/stage/modules/texts/ztext/a/data" "$1/modules/texts/ztext/a/"
  cp "$T/stage/modules/texts/ztext/b/data" "$1/modules/texts/ztext/b/"
}
fail() { echo "FAIL: $1"; exit 1; }
expect_exit() { # expected-code, description, command...
  local want="$1" what="$2"; shift 2
  local got=0; "$@" >"$T/out" 2>&1 || got=$?
  [ "$got" = "$want" ] || { cat "$T/out"; fail "$what: exit $got, expected $want"; }
}

# 1. Exact set -> 0
install "$T/exact"
expect_exit 0 "exact set" "$CHECK" --zip "$T/testmods.zip" --sword "$T/exact"

# 2. Empty mods.d -> 1, both confs reported missing
mkdir -p "$T/empty/mods.d"
expect_exit 1 "empty mods.d" "$CHECK" --zip "$T/testmods.zip" --sword "$T/empty"
grep -q 'a.conf' "$T/out" && grep -q 'b.conf' "$T/out" || { cat "$T/out"; fail "empty mods.d: missing confs not listed"; }

# 2b. No mods.d at all -> 1 (not a crash)
expect_exit 1 "absent mods.d" "$CHECK" --zip "$T/testmods.zip" --sword "$T/nonexistent"

# 3. Case-sensitive names: A.conf is not a.conf -> 1
install "$T/case"; mv "$T/case/mods.d/a.conf" "$T/case/mods.d/A.conf"
expect_exit 1 "case mismatch" "$CHECK" --zip "$T/testmods.zip" --sword "$T/case"

# 4. Extra conf with its own data dir -> 1; --fix -> 0, extra conf and its dir gone, zip modules untouched
install "$T/extra"
printf '[extra]\nDataPath=./modules/texts/ztext/extra/\n' > "$T/extra/mods.d/extra.conf"
mkdir -p "$T/extra/modules/texts/ztext/extra"; echo x > "$T/extra/modules/texts/ztext/extra/data"
expect_exit 1 "extra conf" "$CHECK" --zip "$T/testmods.zip" --sword "$T/extra"
grep -q 'extra.conf' "$T/out" || { cat "$T/out"; fail "extra conf not listed"; }
expect_exit 0 "--fix" "$CHECK" --zip "$T/testmods.zip" --sword "$T/extra" --fix
[ ! -e "$T/extra/mods.d/extra.conf" ] || fail "--fix left extra.conf"
[ ! -e "$T/extra/modules/texts/ztext/extra" ] || fail "--fix left extra data dir"
[ -f "$T/extra/modules/texts/ztext/a/data" ] && [ -f "$T/extra/modules/texts/ztext/b/data" ] \
  && [ -f "$T/extra/mods.d/a.conf" ] && [ -f "$T/extra/mods.d/b.conf" ] || fail "--fix touched zip modules"

# 5. Extra conf whose DataPath points into a zip module's dir -> --fix must refuse to delete it
install "$T/shared"
printf '[shared]\nDataPath=./modules/texts/ztext/a/\n' > "$T/shared/mods.d/shared.conf"
expect_exit 1 "shared dir extra" "$CHECK" --zip "$T/testmods.zip" --sword "$T/shared" --fix
[ -f "$T/shared/modules/texts/ztext/a/data" ] || fail "--fix deleted a zip module's data"

# 6. Missing zip -> 2
expect_exit 2 "missing zip" "$CHECK" --zip "$T/nope.zip" --sword "$T/exact"

echo "PASS: test-check-test-sword-modules.sh"
