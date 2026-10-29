#!/usr/bin/env bash
# Proves that a single commit only moves files: usage scripts/check-pure-moves.sh <rev>
# Compares <rev> with its parent. Exit 0 when every changed path is either a rename or a
# modified file, and every changed line is a package/import/blank line. Exit 1 otherwise,
# printing each offending file with its first non-conforming line; a commit with no renames
# exits 1 with "no moves in <rev>".
set -euo pipefail
[ $# -eq 1 ] || { echo "usage: $0 <rev>" >&2; exit 2; }
REV="$1"
BASE="$REV^"
bad=0; renames=0

# first changed line (excluding ---/+++ headers) that is not package/import/blank
first_bad() {
  git diff -M50% -U0 "$BASE" "$REV" -- "$@" \
    | grep -E '^[+-]' | grep -vE '^(\+\+\+|---)' \
    | grep -vE '^[+-](import |package )' | grep -vE '^[+-]\s*$' | head -1 || true
}

while IFS=$'\t' read -r status p1 p2; do
  case "$status" in
    R*)
      renames=$((renames+1))
      line="$(first_bad "$p1" "$p2")"
      [ -z "$line" ] || { echo "$p2: $line"; bad=1; }
      ;;
    M)
      line="$(first_bad "$p1")"
      [ -z "$line" ] || { echo "$p1: $line"; bad=1; }
      ;;
    *) echo "${p2:-$p1}: not a rename (status $status)"; bad=1 ;;
  esac
done < <(git diff --find-renames -M50% --name-status "$BASE" "$REV")

if [ "$renames" -eq 0 ]; then echo "no moves in $REV"; exit 1; fi
exit "$bad"
