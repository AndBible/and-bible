#!/usr/bin/env bash
# Checks that <sword>/mods.d/*.conf is exactly the set of confs in the test-modules zip
# (.local/testmods.zip). The :app unit suite needs exactly these modules in ~/.sword; extras
# change which books the tests see. Exit 0 = exact match, 1 = mismatch, 2 = zip missing.
# --fix removes each extra conf and the data directory its DataPath= names (relative to <sword>),
# never anything the zip contains, then re-checks.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
ZIP="$ROOT/.local/testmods.zip"; SWORD="$HOME/.sword"; FIX=0
while [ $# -gt 0 ]; do
  case "$1" in
    --zip) ZIP="$2"; shift 2 ;;
    --sword) SWORD="$2"; shift 2 ;;
    --fix) FIX=1; shift ;;
    *) echo "usage: $0 [--zip <path>] [--sword <dir>] [--fix]" >&2; exit 2 ;;
  esac
done
[ -f "$ZIP" ] || { echo "missing test-modules zip: $ZIP" >&2; exit 2; }

export LC_ALL=C  # comm needs identical sort order on both sides; file names are case-sensitive
zip_confs() { unzip -Z1 "$ZIP" | sed -n 's#^mods\.d/\([^/]*\.conf\)$#\1#p' | sort; }
sword_confs() { find "$SWORD/mods.d" -maxdepth 1 -name '*.conf' -printf '%f\n' 2>/dev/null | sort || true; }

# Prints the result and returns 0 on exact match, 1 otherwise.
check() {
  local zc sc missing extra
  zc="$(zip_confs)"; sc="$(sword_confs)"
  missing="$(comm -23 <(printf '%s\n' "$zc") <(printf '%s\n' "$sc") | sed '/^$/d')"
  extra="$(comm -13 <(printf '%s\n' "$zc") <(printf '%s\n' "$sc") | sed '/^$/d')"
  if [ -z "$missing" ] && [ -z "$extra" ]; then
    echo "OK: $SWORD/mods.d matches $ZIP ($(printf '%s\n' "$zc" | wc -l) modules)"
    return 0
  fi
  [ -n "$missing" ] && { echo "missing in $SWORD/mods.d (in zip):"; sed 's/^/  /' <<<"$missing"; }
  [ -n "$extra" ] && { echo "extra in $SWORD/mods.d (not in zip):"; sed 's/^/  /' <<<"$extra"; }
  return 1
}

# Refuses a data path that is, contains, or sits inside the directory of any file the zip ships.
zip_overlaps() { # relative path -> 0 when it overlaps the zip
  local rel="$1" f
  while IFS= read -r f; do
    [[ "$f" == */ ]] && continue
    if [[ "$f" == "$rel" || "$f" == "$rel"/* || "$rel" == "$(dirname "$f")"/* ]]; then return 0; fi
  done < <(unzip -Z1 "$ZIP")
  return 1
}

# Removes each extra conf and its DataPath directory. Anything suspicious is skipped with a message.
remove_extras() {
  local conf dp rel
  while IFS= read -r conf; do
    [ -n "$conf" ] || continue
    dp="$(tr -d '\r' < "$SWORD/mods.d/$conf" | sed -n 's/^DataPath=//p' | head -n1)"
    rel="${dp#./}"; rel="${rel%/}"
    if [ -z "$rel" ] || [[ "$rel" == /* ]] || [[ "$rel" == *..* ]]; then
      echo "fix: $conf has no safe DataPath (${dp:-none}); removing only the conf" >&2
      rel=""
    elif zip_overlaps "$rel"; then
      echo "fix: $conf DataPath $rel overlaps the zip; leaving it" >&2
      continue
    fi
    rm -f "$SWORD/mods.d/$conf"
    [ -n "$rel" ] && rm -rf "$SWORD/$rel"
    echo "removed extra: $conf ${rel:+($rel)}"
  done < <(comm -13 <(zip_confs) <(sword_confs))
}

if check; then exit 0; fi
if [ "$FIX" = 1 ]; then
  remove_extras
  echo "re-check after --fix:"
  check && exit 0
fi
exit 1
