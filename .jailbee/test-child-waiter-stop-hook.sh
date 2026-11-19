#!/usr/bin/env bash
# Tests for child-waiter-stop-hook.sh. Run: bash .jailbee/test-child-waiter-stop-hook.sh
# A stub `ps` on PATH supplies the process table each case needs.
set -u
here=$(cd "$(dirname "$0")" && pwd)
hook="$here/child-waiter-stop-hook.sh"
tmp=$(mktemp -d); trap 'rm -rf "$tmp"' EXIT
fail=0

run_case() { # name role stop_active ps_output expect(block|pass)
    local name=$1 role=$2 active=$3 table=$4 expect=$5 out
    printf '#!/bin/sh\nprintf "%%s\\n" "%s"\n' "$table" > "$tmp/ps"; chmod +x "$tmp/ps"
    out=$(printf '{"stop_hook_active": %s}' "$active" | PATH="$tmp:$PATH" JAILBEE_ROLE="$role" bash "$hook")
    local got=pass
    printf '%s' "$out" | grep -q '"decision": "block"' && got=block
    if [ "$got" = "$expect" ]; then echo "ok   $name"; else echo "FAIL $name (got $got)"; fail=1; fi
}

waiter='python /opt/jailbee/venv/bin/python -P -m jailbee.container_main inbox wait --timeout none'
wrapper="bash /bin/bash -c eval 'jailbee inbox wait --timeout none'"
grep_line="grep grep inbox wait"

run_case "not a child: never blocks"           ""      false "$wrapper"  pass
run_case "child with a live waiter"            child   false "$waiter"   pass
run_case "child without a waiter blocks"       child   false "sleep sleep 5" block
run_case "shell wrapper text is not a waiter"  child   false "$wrapper"  block
run_case "grep for the waiter is not a waiter" child   false "$grep_line" block
run_case "second stop in a chain passes"       child   true  "sleep sleep 5" pass
exit $fail
