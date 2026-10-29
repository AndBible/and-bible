#!/usr/bin/env bash
# Tests scripts/check-pure-moves.sh against a throwaway git repo. Prints "<n>/<n> passed".
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
CHECK="$ROOT/scripts/check-pure-moves.sh"
TMP="$(mktemp -d)"; trap 'rm -rf "$TMP"' EXIT
cd "$TMP"
git init -q .
git config user.name test; git config user.email test@example.com
mkdir -p src/iosMain src/hostMain src/app
for n in A B C D; do
  printf 'package ios\n\nimport x.Y\n\nfun f%s() {\n    println("one")\n    println("two")\n    println("three")\n}\n' "$n" > "src/iosMain/$n.kt"
done
printf 'package app\n\nimport ios.A\n\nfun use() = fA()\n' > src/app/Use.kt
git add -A; git commit -qm base

PASS=0; TOTAL=0
expect() { # name expected-exit rev [needle]
  TOTAL=$((TOTAL+1))
  local out rc=0
  out="$("$CHECK" "$3" 2>&1)" || rc=$?
  if [ "$rc" = "$2" ] && { [ -z "${4:-}" ] || grep -q -- "$4" <<<"$out"; }; then
    PASS=$((PASS+1)); echo "ok   $1"
  else
    echo "FAIL $1 (exit $rc, want $2)"; echo "$out" | sed 's/^/     /'
  fi
}

# (a) move + package line change -> 0
git mv src/iosMain/A.kt src/hostMain/A.kt
sed -i 's/^package ios/package host/' src/hostMain/A.kt
git commit -qam a; expect "a: move + package change" 0 HEAD

# (b) move + body change -> 1, names file
git mv src/iosMain/B.kt src/hostMain/B.kt
sed -i 's/println("two")/println("TWO")/' src/hostMain/B.kt
git commit -qam b; expect "b: move + body change" 1 HEAD "hostMain/B.kt"

# (c) move + added import -> 0
git mv src/iosMain/C.kt src/hostMain/C.kt
sed -i 's/^import x.Y/import x.Y\nimport z.W as V/' src/hostMain/C.kt
git commit -qam c; expect "c: move + added import" 0 HEAD

# (d) edit without move -> 1
sed -i 's/println("one")/println("ONE")/' src/iosMain/D.kt
git commit -qam d; expect "d: edit without move" 1 HEAD

# (e) move + importer that only changes an import line -> 0
git mv src/iosMain/D.kt src/hostMain/D.kt
sed -i 's/^import ios.A/import host.A/' src/app/Use.kt
git commit -qam e; expect "e: move + importer import change" 0 HEAD

echo "$PASS/$TOTAL passed"
[ "$PASS" = "$TOTAL" ]
