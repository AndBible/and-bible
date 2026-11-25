#!/usr/bin/env bash
# Offline deployment approval regression tests. Only gh is stubbed; jq evaluates the real queries.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../../.." && pwd)"
T="$(mktemp -d)"
trap 'rm -rf "$T"' EXIT

# Load the real functions without running the tag/push entry point.
source <(awk '/^KIND=production$/ {exit} {print}' "$ROOT/scripts/github-release.sh")

cat > "$T/gh" <<'STUB'
#!/usr/bin/env bash
set -euo pipefail
printf '%s\n' "$*" >> "$STATE/calls"
[[ "$1" == api ]] || { printf 'Unexpected command\n' >&2; exit 1; }
shift
method=GET; query=.
if [[ "$1" == --method ]]; then method="$2"; shift 2; fi
endpoint="$1"; shift
while (($#)); do
    case "$1" in
        --method) method="$2"; shift 2 ;;
        --jq) query="$2"; shift 2 ;;
        -F|-f) shift 2 ;;
        *) printf 'Unexpected argument: %s\n' "$1" >&2; exit 1 ;;
    esac
done
count() {
    local n=0
    [[ ! -f "$STATE/$1" ]] || read -r n < "$STATE/$1"
    n=$((n + 1))
    printf '%s\n' "$n" > "$STATE/$1"
    printf '%s\n' "$n"
}
case "$endpoint" in
    */workflows/*/runs\?*)
        n=$(count lookups)
        if [[ "$endpoint" == *head_sha=* ]]; then
            json='{"workflow_runs":[{"id":101,"status":"completed","head_branch":"older-tag","event":"push"}]}'
        elif [[ "$endpoint" != *'branch=beta-1284&event=push'* ]]; then
            printf 'Missing release tag/event filter\n' >&2; exit 1
        elif [[ "$SCENARIO" == missing || ( "$SCENARIO" == delayed && "$n" -lt 3 ) ]]; then
            json='{"workflow_runs":[]}'
        elif [[ "$SCENARIO" == lookup-error && "$n" == 1 ]]; then
            printf 'temporary lookup error\n' >&2; exit 1
        elif [[ "$SCENARIO" == replaced && "$n" == 1 ]]; then
            json='{"workflow_runs":[{"id":303,"status":"in_progress","head_branch":"beta-1284","event":"push"}]}'
        else
            json='{"workflow_runs":[{"id":202,"status":"waiting","head_branch":"beta-1284","event":"push"}]}'
        fi ;;
    */pending_deployments)
        if [[ "$method" == POST ]]; then
            n=$(count posts)
            if [[ "$SCENARIO" == lost-response || "$SCENARIO" == partial-approval ]]; then
                printf '%s\n' "$endpoint" > "$STATE/approved"
                printf 'approval response lost\n' >&2; exit 1
            fi
            if [[ "$SCENARIO" == post-error || ( "$SCENARIO" == retry && "$n" == 1 ) ]]; then
                printf 'temporary approval error\n' >&2; exit 1
            fi
            printf '%s\n' "$endpoint" > "$STATE/approved"
            exit 0
        fi
        n=$(count pending)
        if [[ "$SCENARIO" == pending-error && "$n" == 1 ]]; then
            printf 'temporary pending error\n' >&2; exit 1
        fi
        if [[ "$endpoint" == */101/* || "$endpoint" == */303/* || "$SCENARIO" == waiting || "$SCENARIO" == completed-no-approval || "$SCENARIO" == skipped-signing || "$SCENARIO" == manual-* || -f "$STATE/approved" ]]; then
            json='[]'
        else
            json='[{"environment":{"id":42},"current_user_can_approve":true},{"environment":{"id":43},"current_user_can_approve":true}]'
        fi ;;
    */approvals)
        if [[ "$SCENARIO" == partial-approval ]]; then
            json='[{"state":"approved","environments":[{"id":42}]}]'
        elif [[ -f "$STATE/approved" || "$SCENARIO" == manual-apk || "$SCENARIO" == manual-desktop ]]; then
            json='[{"state":"approved","environments":[{"id":42},{"id":43}]}]'
        else
            json='[]'
        fi ;;
    */jobs)
        n=$(count jobs)
        if [[ "$SCENARIO" == completed-no-approval ]]; then
            json='{"jobs":[]}'
        elif [[ "$SCENARIO" == skipped-signing ]]; then
            json='{"jobs":[{"name":"windows-sign","status":"completed","conclusion":"skipped"}]}'
        elif [[ "$SCENARIO" == follow-failed ]]; then
            json='{"jobs":[{"name":"windows-sign","status":"completed","conclusion":"failure"}]}'
        elif [[ "$n" == 1 ]]; then
            json='{"jobs":[{"name":"windows-sign","status":"in_progress","conclusion":null}]}'
        else
            json='{"jobs":[{"name":"windows-sign","status":"completed","conclusion":"success"}]}'
        fi ;;
    */actions/runs/*)
        if [[ "$SCENARIO" == completed-no-approval || "$SCENARIO" == manual-apk ]]; then
            json='{"status":"completed","conclusion":"failure"}'
        else
            json='{"status":"in_progress","conclusion":null}'
        fi ;;
    *) printf 'Unexpected endpoint: %s\n' "$endpoint" >&2; exit 1 ;;
esac
printf '%s\n' "$json" | jq -r "$query"
STUB
chmod +x "$T/gh"
export PATH="$T:$PATH"

# Advance the deadline deterministically without waiting on wall-clock time.
sleep() { SECONDS=$((SECONDS + $1)); }
signpath_approval_reminder() { printf 'SignPath reminder\n'; }
fail() { printf 'FAIL: %s\n' "$*" >&2; }
run_case() {
    export SCENARIO="$1" STATE="$T/$1"
    mkdir -p "$STATE"
    SECONDS=0
    rc=0
    approve_workflow_deployments build-apk.yml beta-1284 8 2 "${2:-}" > "$STATE/output" 2>&1 || rc=$?
}
assert_approved() {
    [[ "$rc" == 0 && -f "$STATE/approved" ]] || { fail "$SCENARIO: expected approval, exit=$rc"; return 1; }
    [[ "$(< "$STATE/approved")" == */202/pending_deployments ]] || { fail "$SCENARIO: approved the wrong run"; return 1; }
    grep -q 'environment_ids\[\]=42 -F environment_ids\[\]=43' "$STATE/calls" || { fail "$SCENARIO: missing environments"; return 1; }
    printf 'ok %s\n' "$SCENARIO"
}
assert_warning() {
    [[ "$rc" != 0 ]] || { fail "$SCENARIO: expected failure"; return 1; }
    grep -qi 'WARNING.*approval' "$STATE/output" || { fail "$SCENARIO: missing loud approval warning"; return 1; }
    grep -q "$1" "$STATE/output" || { fail "$SCENARIO: missing manual approval URL"; return 1; }
    printf 'ok %s\n' "$SCENARIO"
}

# A missing retry/refresh, SHA-only selection, or swallowed error breaks these cases.
failed=0
for scenario in delayed tag-only replaced retry lookup-error pending-error; do
    run_case "$scenario"
    assert_approved || failed=$((failed + 1))
done
for scenario in waiting post-error partial-approval; do
    run_case "$scenario"
    assert_warning 'https://github.com/AndBible/and-bible/actions/runs/202' || failed=$((failed + 1))
done
run_case missing
assert_warning 'https://github.com/AndBible/and-bible/actions/workflows/build-apk.yml' || failed=$((failed + 1))
run_case follow windows-sign
assert_approved || failed=$((failed + 1))
grep -q 'SignPath reminder' "$STATE/output" || { fail 'follow: missing signing reminder'; failed=$((failed + 1)); }
run_case lost-response windows-sign
assert_approved || failed=$((failed + 1))
grep -q 'SignPath reminder' "$STATE/output" || { fail 'lost response: missing signing reminder'; failed=$((failed + 1)); }
run_case follow-failed windows-sign
[[ "$rc" != 0 ]] || { fail 'failed signing job reported success'; failed=$((failed + 1)); }
for scenario in completed-no-approval skipped-signing; do
    run_case "$scenario" windows-sign
    assert_warning 'https://github.com/AndBible/and-bible/actions/runs/202' || failed=$((failed + 1))
    [[ "$SECONDS" -lt 2 ]] || { fail "$scenario: terminal run/job waited until the deadline"; failed=$((failed + 1)); }
done
run_case manual-apk
[[ "$rc" == 0 ]] || { fail 'manual-apk: approval history was not detected'; failed=$((failed + 1)); }
[[ ! -f "$STATE/posts" && "$SECONDS" -lt 2 ]] || { fail 'manual-apk: posted or kept polling'; failed=$((failed + 1)); }
for scenario in manual-desktop manual-job-state; do
    run_case "$scenario" windows-sign
    [[ "$rc" == 0 ]] || { fail "$scenario: manual approval was not detected"; failed=$((failed + 1)); }
    [[ ! -f "$STATE/posts" ]] || { fail "$scenario: unexpected approval POST"; failed=$((failed + 1)); }
    grep -q 'windows-sign: success' "$STATE/output" || { fail "$scenario: signing was not followed"; failed=$((failed + 1)); }
    grep -q 'SignPath reminder' "$STATE/output" || { fail "$scenario: missing signing reminder"; failed=$((failed + 1)); }
done
printf 'failed %s\n' "$failed"
[[ "$failed" == 0 ]]
