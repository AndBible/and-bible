#!/usr/bin/env bash
# Shared helpers for the L1 readiness scripts (l1-readiness.sh, l1-move-readiness.sh).
# Sourced, not executed. The caller must set SRC (:app main sources root), MAP (domains file)
# and PLATFORM (platform-file list) before calling these.
owner_of() { # relative path -> owning domain (longest mapped prefix), empty if none
  awk -F'\t' -v r="$1" 'NF>=2 && substr($1,1,1)!="@" {
      p=$2; if (r==p || index(r, p"/")==1) { if (length(p)>best) { best=length(p); d=$1 } } }
      END { print d }' "$MAP"
}
files_of() { # domain -> file list (platform files and files owned by a longer row excluded)
  local d="$1"
  awk -F'\t' -v d="$d" 'NF>=2 && $1==d {print $2}' "$MAP" | while read -r p; do
    if [ -d "$SRC/$p" ]; then find "$SRC/$p" -type f \( -name '*.kt' -o -name '*.java' \); else echo "$SRC/$p"; fi
  done | sort -u | while read -r f; do
    rel="${f#$SRC/}"
    awk -F'\t' 'NF>=1 {print $1}' "$PLATFORM" | grep -qxF "$rel" && continue
    [ "$(owner_of "$rel")" = "$d" ] && echo "$f"
  done
}
count() { # pattern [exclude-pattern] files...
  local pat="$1" excl="$2"; shift 2
  [ $# -eq 0 ] && { echo 0; return; }
  # `|| true`: grep exits 1 on no match, which pipefail would turn into a script abort
  if [ -n "$excl" ]; then { grep -a -h -E "$pat" "$@" 2>/dev/null || true; } | { grep -a -v -E "$excl" || true; } | wc -l
  else { grep -a -h -E "$pat" "$@" 2>/dev/null || true; } | wc -l; fi
}
