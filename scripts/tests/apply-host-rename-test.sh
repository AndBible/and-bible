#!/usr/bin/env bash
# Tests scripts/apply-host-rename.sh against throwaway git repos. Prints "<n>/<n> passed".
set -uo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
APPLY="$ROOT/scripts/apply-host-rename.sh"
BASE="$(mktemp -d)"; trap 'rm -rf "$BASE"' EXIT
PASS=0; TOTAL=0; FAILED=0

new_repo() { # sets REPO and MAP (map content from stdin, tabs via printf in callers)
  REPO="$(mktemp -d -p "$BASE")"
  ( cd "$REPO" && git init -q . && git config user.name test && git config user.email test@example.com )
  MAP="$REPO.map.tsv"
  cat > "$MAP"
}
run() { ( cd "$REPO" && HOST_RENAME_MAP="$MAP" "$APPLY" "$@" ); }
commit_all() { ( cd "$REPO" && git add -A && git commit -qm "$1" ); }
check() { # name, ok(0/1), detail
  TOTAL=$((TOTAL+1))
  if [ "$2" = 0 ]; then PASS=$((PASS+1)); echo "PASS $1"; else FAILED=1; echo "FAIL $1"; [ -n "${3:-}" ] && echo "$3" | sed 's/^/     /'; fi
}
same() { # name, file, expected
  local got; got="$(cat "$REPO/$2"; echo x)"
  if [ "$got" = "$3"$'\n'x ]; then check "$1" 0; else check "$1" 1 "--- expected
$3
--- got
${got%x}"; fi
}

longestNameFirst() {
  new_repo < <(printf 'IosReadingHostPlatform\tHostReadingHostPlatform\nIosReadingHost\tHostReadingHost\n')
  printf 'class IosReadingHost(p: IosReadingHostPlatform)\nval a = IosReadingHost\nval b = IosReadingHostPlatform\n' > "$REPO/a.kt"
  commit_all base; run >/dev/null
  same longestNameFirst a.kt 'class HostReadingHost(p: HostReadingHostPlatform)
val a = HostReadingHost
val b = HostReadingHostPlatform'
}

wholeWordOnly() {
  new_repo < <(printf 'IosReadingHost\tHostReadingHost\n')
  cat > "$REPO/a.kt" <<'K'
val a = IosReadingHostPlatform
val b = MyIosReadingHost
val c = IosReadingHost2
val d = IosReadingHost
val e = IosReadingHost.foo
val f = IosReadingHost::class
val g = IosReadingHost(1)
val h: List<IosReadingHost> = x
/** See [IosReadingHost] and nested /* IosReadingHost */ end */
// IosReadingHost comment
K
  commit_all base; run >/dev/null
  same wholeWordOnly a.kt 'val a = IosReadingHostPlatform
val b = MyIosReadingHost
val c = IosReadingHost2
val d = HostReadingHost
val e = HostReadingHost.foo
val f = HostReadingHost::class
val g = HostReadingHost(1)
val h: List<HostReadingHost> = x
/** See [HostReadingHost] and nested /* HostReadingHost */ end */
// HostReadingHost comment'
}

stringLiteralsAndLogTagsUntouched() {
  new_repo < <(printf 'IosX\tHostX\nIosReadingHost\tHostReadingHost\niosStringTable\thostStringTable\n')
  cat > "$REPO/a.kt" <<'K'
val a = "IosReadingKeyActions: failed"
const val TAG = "IosBackupHost"
val r = """IosX"""
val e = "a\"IosX"
val t = "${IosX.y} IosX"
val s = "$iosStringTable"
val u = "$iosStringTableX"
val n = "${"IosX" + IosX.z}"
val f = "IosX.kt"
val c = 'x' + '"' + IosX
val q = "x" + IosX
K
  commit_all base; run >/dev/null
  same stringLiteralsAndLogTagsUntouched a.kt 'val a = "IosReadingKeyActions: failed"
const val TAG = "IosBackupHost"
val r = """IosX"""
val e = "a\"IosX"
val t = "${HostX.y} IosX"
val s = "$hostStringTable"
val u = "$iosStringTableX"
val n = "${"IosX" + HostX.z}"
val f = "HostX.kt"
val c = '"'"'x'"'"' + '"'"'"'"'"' + HostX
val q = "x" + HostX'
}

fileRenamedWithGitMv() {
  new_repo < <(printf 'IosX\tHostX\n')
  mkdir -p "$REPO/a" "$REPO/b"
  printf 'class IosX\n' > "$REPO/a/IosX.kt"
  printf 'class IosXTest\n// IosX\n' > "$REPO/a/IosXTest.kt"
  printf 'IosX notes\n' > "$REPO/b/IosX.md"
  printf 'val x = IosX\n' > "$REPO/b/build.gradle.kts"
  commit_all base; run >/dev/null
  local st rc=0; st="$(cd "$REPO" && git status --porcelain)"
  [ -f "$REPO/a/HostX.kt" ] && [ ! -e "$REPO/a/IosX.kt" ] && grep -q '^R' <<<"$st" || rc=1
  [ -f "$REPO/a/IosXTest.kt" ] || rc=1
  [ -f "$REPO/b/IosX.md" ] && grep -q 'IosX notes' "$REPO/b/IosX.md" || rc=1
  grep -q 'val x = HostX' "$REPO/b/build.gradle.kts" || rc=1
  grep -q 'class IosXTest' "$REPO/a/IosXTest.kt" && grep -q '// HostX' "$REPO/a/IosXTest.kt" || rc=1
  check fileRenamedWithGitMv $rc "$st"
}

reapplyOnMixedTreeIsIdempotent() {
  new_repo < <(printf 'IosX\tHostX\nIosY\tHostY\n')
  printf 'class HostX { val s = "HostX.kt" }\n' > "$REPO/HostX.kt"
  commit_all base
  cp "$REPO/HostX.kt" "$BASE/before.kt"
  printf 'val a = IosX()\nval b = IosY()\n' > "$REPO/use.kt"
  printf 'class IosY\n' > "$REPO/IosY.kt"
  ( cd "$REPO" && git add -A )
  run >/dev/null
  local rc=0
  cmp -s "$REPO/HostX.kt" "$BASE/before.kt" || rc=1
  [ "$(cat "$REPO/use.kt")" = $'val a = HostX()\nval b = HostY()' ] || rc=1
  [ -f "$REPO/HostY.kt" ] && [ ! -e "$REPO/IosY.kt" ] || rc=1
  check reapplyOnMixedTreeIsIdempotent $rc
}

secondRunIsNoop() {
  new_repo < <(printf 'IosX\tHostX\n')
  printf 'class IosX\n' > "$REPO/IosX.kt"; printf 'val a = IosX()\n' > "$REPO/u.kt"
  commit_all base; run >/dev/null; commit_all renamed; run >/dev/null
  local st; st="$(cd "$REPO" && git status --porcelain)"
  if [ -z "$st" ]; then check secondRunIsNoop 0; else check secondRunIsNoop 1 "$st"; fi
}

refusesClash() {
  new_repo < <(printf 'IosX\tHostX\n')
  printf 'class IosX\n' > "$REPO/IosX.kt"; printf 'class Other\n' > "$REPO/HostX.kt"
  printf 'val a = IosX()\n' > "$REPO/u.kt"
  commit_all base
  local out rc=0; out="$(run 2>&1)" || rc=$?
  local st; st="$(cd "$REPO" && git status --porcelain)"
  if [ "$rc" != 0 ] && [ -n "$out" ] && [ -z "$st" ] && [ "$(cat "$REPO/HostX.kt")" = 'class Other' ]; then check refusesClash 0
  else check refusesClash 1 "rc=$rc out=$out st=$st"; fi
}

pathsArgument() {
  new_repo < <(printf 'IosX\tHostX\n')
  mkdir -p "$REPO/in" "$REPO/out" "$REPO/in/build"
  printf 'val a = IosX()\n' > "$REPO/in/a.kt"; printf 'val a = IosX()\n' > "$REPO/out/b.kt"
  printf 'class IosX\n' > "$REPO/in/IosX.kt"; printf 'class IosX\n' > "$REPO/out/IosX.kt"
  printf 'val a = IosX()\n' > "$REPO/in/build/gen.kt"
  commit_all base
  printf 'val z = IosX()\n' > "$REPO/in/untracked.kt"
  run in >/dev/null
  local rc=0
  grep -q HostX "$REPO/in/a.kt" && grep -q HostX "$REPO/in/untracked.kt" || rc=1
  [ -f "$REPO/in/HostX.kt" ] || rc=1
  grep -q IosX "$REPO/out/b.kt" && [ -f "$REPO/out/IosX.kt" ] || rc=1
  grep -q IosX "$REPO/in/build/gen.kt" || rc=1
  check pathsArgument $rc
}

for t in longestNameFirst wholeWordOnly stringLiteralsAndLogTagsUntouched fileRenamedWithGitMv \
         reapplyOnMixedTreeIsIdempotent secondRunIsNoop refusesClash pathsArgument; do $t; done
echo "$PASS/$TOTAL passed"
[ "$FAILED" = 0 ]
