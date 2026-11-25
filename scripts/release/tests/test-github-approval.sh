#!/usr/bin/env bash
# Offline approval contracts: real waiter and jq, external gh and clock only are stubbed.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../../.." && pwd)"
T="$(mktemp -d)"
trap 'rm -rf "$T"' EXIT
source <(awk '/^KIND=production$/ {exit} {print}' "$ROOT/scripts/github-release.sh")

cat > "$T/gh" <<'STUB'
#!/usr/bin/env bash
set -euo pipefail
printf '%s\n' "$*" >> "$STATE/calls"
[[ "$1" == api ]] || exit 1
shift
method=GET; query=.
if [[ "$1" == --method ]]; then method="$2"; shift 2; fi
endpoint="$1"; shift
while (($#)); do
    case "$1" in
        --method) method="$2"; shift 2 ;;
        --jq) query="$2"; shift 2 ;;
        -F|-f) shift 2 ;;
        *) exit 1 ;;
    esac
done
count() {
    local n=0
    [[ ! -f "$STATE/$1" ]] || read -r n < "$STATE/$1"
    n=$((n + 1)); printf '%s\n' "$n" > "$STATE/$1"; printf '%s\n' "$n"
}
case "$endpoint" in
    */workflows/*/runs\?*) boundary=lookup ;;
    */pending_deployments) boundary=pending ;;
    */jobs) boundary=jobs ;;
    */actions/runs/202) boundary=status ;;
    *) printf 'Unexpected endpoint: %s\n' "$endpoint" >&2; exit 1 ;;
esac
n=$(count "$method-$boundary")
if [[ "$method" == GET ]]; then
    [[ "$SCENARIO" != success-stderr ]] || printf 'diagnostic only (HTTP 403)\n' >&2
    if [[ "$SCENARIO" == "get-$boundary-"* && "$n" == 1 ]]; then
        printf 'gh: lookup error (HTTP %s)\n' "${SCENARIO##*-}" >&2; exit 1
    fi
    if [[ "$SCENARIO" == "quiet-$boundary" ]]; then
        printf 'temporary GET error\n' >&2; exit 1
    fi
fi
case "$boundary:$method" in
    lookup:GET)
        # Same tag + same SHA is insufficient: the old run must also fail the push-time boundary.
        runs='[{"id":101,"head_sha":"release-sha","head_branch":"beta-1284","event":"push","created_at":"2026-11-25T09:59:59Z"},{"id":303,"head_sha":"other-sha","head_branch":"beta-1284","event":"push","created_at":"2026-11-25T10:00:01Z"}]'
        if [[ "$SCENARIO" != missing && "$SCENARIO" != old-only && !( "$SCENARIO" == delayed && "$n" -lt 3 ) ]]; then
            runs=$(jq '. + [{id:202,head_sha:"release-sha",head_branch:"beta-1284",event:"push",created_at:"2026-11-25T10:00:00Z"}]' <<< "$runs")
        fi
        [[ "$SCENARIO" != missing ]] || runs='[]'
        json="{\"workflow_runs\":$runs}" ;;
    pending:POST)
        if [[ "$SCENARIO" == post-* && ( "$SCENARIO" != post-race-422 || "$n" == 1 ) ]]; then
            [[ "$SCENARIO" != post-race-422 ]] || touch "$STATE/approved"
            printf 'gh: POST error\nwith details (HTTP %s)\n' "${SCENARIO##*-}" >&2; exit 1
        fi
        touch "$STATE/approved"; exit 0 ;;
    pending:GET)
        if [[ "$SCENARIO" == denied-* ]]; then
            json='[{"environment":{"id":42},"current_user_can_approve":false},{"environment":{"id":43}}]'
        elif [[ "$SCENARIO" == mixed ]]; then
            json='[{"environment":{"id":42},"current_user_can_approve":true},{"environment":{"id":43},"current_user_can_approve":false}]'
        elif [[ "$SCENARIO" == manual-* || "$SCENARIO" == terminal-* || "$SCENARIO" == waiting || -f "$STATE/approved" ]]; then
            json='[]'
        else
            json='[{"environment":{"id":42},"current_user_can_approve":true},{"environment":{"id":43},"current_user_can_approve":true}]'
        fi ;;
    jobs:GET)
        status=queued; conclusion=null
        if [[ "$SCENARIO" == terminal-job-* ]]; then
            status=completed; conclusion="\"${SCENARIO##*-}\""
        elif [[ "$SCENARIO" == terminal-run-* ]]; then
            json='{"jobs":[]}'; printf '%s\n' "$json" | jq -r "$query"; exit
        elif [[ "$SCENARIO" == manual-success ]]; then
            status=completed; conclusion='"success"'
        elif [[ "$SCENARIO" != posted-waiting && ( "$SCENARIO" == manual-running || "$SCENARIO" == manual-desktop || -f "$STATE/approved" || ( "$SCENARIO" == denied-other && "$n" -ge 3 ) ) ]]; then
            status=in_progress
            if [[ -f "$STATE/ran" ]]; then
                status=completed; conclusion='"success"'
                [[ "$SCENARIO" != follow-failure ]] || conclusion='"failure"'
            fi
            touch "$STATE/ran"
        fi
        # Older runs were already approved; accepting one is a false success.
        json="{\"jobs\":[{\"name\":\"$JOB\",\"status\":\"$status\",\"conclusion\":$conclusion}]}" ;;
    status:GET)
        if [[ "$SCENARIO" == terminal-run-* ]]; then
            json="{\"status\":\"completed\",\"conclusion\":\"${SCENARIO##*-}\"}"
        else
            json='{"status":"in_progress","conclusion":null}'
        fi ;;
esac
printf '%s\n' "$json" | jq -r "$query"
STUB
chmod +x "$T/gh"
export PATH="$T:$PATH"
sleep() { printf '%s\n' "$SECONDS" >> "$STATE/sleeps"; SECONDS=$((SECONDS + $1)); }
signpath_approval_reminder() { printf 'SignPath reminder\n'; }
cases=0; failed=0
check() { if ! eval "$1"; then printf 'FAIL %s: %s\n' "$SCENARIO" "$2"; failed=$((failed + 1)); fi; }
run_case() {
    cases=$((cases + 1))
    export SCENARIO="$1" STATE="$T/$cases-$1" JOB="${2:-build}"
    mkdir -p "$STATE"; SECONDS=0; rc=0
    approve_workflow_deployments "${3:-build-apk.yml}" beta-1284 180 30 "$JOB" release-sha 2026-11-25T10:00:00Z "${4:-false}" > "$STATE/output" 2>&1 || rc=$?
}
for scenario in ordinary delayed reused-tag post-race-422 manual-running manual-success denied-other mixed success-stderr; do
    run_case "$scenario"
    check '[[ "$rc" == 0 ]]' 'gated job running/success was not recognized'
    check '! grep -Eq "runs/(101|303)/" "$STATE/calls"' 'selected an old or wrong-commit run'
done
run_case ordinary
check 'grep -q "environment_ids\[\]=42 -F environment_ids\[\]=43" "$STATE/calls"' 'omitted eligible environments'
run_case mixed
check 'grep -q "environment_ids\[\]=42" "$STATE/calls" && ! grep -q "environment_ids\[\]=43" "$STATE/calls"' 'posted an unauthorized environment'
run_case success-stderr
check '! grep -q "diagnostic only" "$STATE/output"' 'successful stderr contaminated the result'
for scenario in manual-running manual-success denied-other; do
    run_case "$scenario"
    check '[[ ! -f "$STATE/POST-pending" ]]' 'posted when no eligible deployment existed'
done
run_case denied-other
check '[[ $(grep -c "WARNING.*cannot approve" "$STATE/output") == 1 ]]' 'permission warning not emitted exactly once'
check 'grep -q "https://github.com/AndBible/and-bible/actions/runs/202" "$STATE/output"' 'permission warning omitted run URL'
for scenario in missing old-only denied-forever waiting posted-waiting post-400 post-401 post-403 post-404 post-422 post-429 post-503; do
    run_case "$scenario"
    check '[[ "$rc" != 0 && $(wc -l < "$STATE/sleeps") -eq 6 ]]' 'did not wait until the bounded deadline'
    check 'grep -q "WARNING.*approval" "$STATE/output"' 'missing timeout warning'
    if [[ "$scenario" == post-* ]]; then
        check '[[ $(< "$STATE/POST-pending") == 6 ]]' 'POST error was not retried every poll'
        check '[[ $(grep -c "POST.*error.*with details" "$STATE/output") == 6 ]]' 'POST failures were not logged on one line each'
    fi
    if [[ "$scenario" == missing || "$scenario" == old-only ]]; then
        check 'grep -q "https://github.com/AndBible/and-bible/actions/workflows/build-apk.yml" "$STATE/output"' 'no-run warning omitted workflow URL'
    fi
done
for scenario in terminal-run-cancelled terminal-run-failure terminal-run-success terminal-job-skipped terminal-job-failure terminal-job-cancelled; do
    run_case "$scenario"
    check '[[ "$rc" != 0 && ! -f "$STATE/sleeps" ]]' 'terminal state did not stop immediately'
    check 'grep -qi "without the gated job running" "$STATE/output"' 'missing neutral terminal explanation'
    check '! grep -qi "build failed" "$STATE/output"' 'claimed a build failure without evidence'
    check 'grep -q "https://github.com/AndBible/and-bible/actions/runs/202" "$STATE/output"' 'terminal message omitted run URL'
done
for boundary in lookup status jobs pending; do
    for status in 403 429 503; do
        run_case "get-$boundary-$status"
        check '[[ "$rc" == 0 ]]' 'transient GET error was not retried'
    done
done
for status in 401 404; do
    run_case "get-lookup-$status"
    check '[[ "$rc" != 0 && ! -f "$STATE/sleeps" ]]' 'run lookup authorization/not-found did not fail fast'
    check 'grep -q "https://github.com/AndBible/and-bible/actions/workflows/build-apk.yml" "$STATE/output"' 'lookup warning omitted workflow URL'
    for boundary in status jobs pending; do
        run_case "get-$boundary-$status"
        check '[[ "$rc" == 0 ]]' 'non-lookup GET failed fast'
    done
done
for boundary in lookup status jobs pending; do
    run_case "quiet-$boundary"
    check '[[ "$rc" != 0 ]]' 'persistent GET errors produced success'
    check '[[ $(grep -c "lookup failed" "$STATE/output") == 1 ]]' 'GET summary was not bounded'
    check '! grep -q "temporary GET error" "$STATE/output"' 'GET stderr leaked on every poll'
    check '[[ $(grep -c "Still waiting" "$STATE/output") == 2 ]]' 'missing minute-scale foreground progress'
done
run_case manual-desktop windows-sign desktop.yml true
check '[[ "$rc" == 0 ]] && grep -q "windows-sign: success" "$STATE/output"' 'Desktop did not follow signing to completion'
check '[[ $(grep -c "SignPath reminder" "$STATE/output") == 1 ]]' 'missing/duplicated signing reminder'
run_case follow-failure windows-sign desktop.yml true
check '[[ "$rc" != 0 ]] && grep -q "windows-sign: failure" "$STATE/output"' 'failure after running was not reported'
printf 'scenarios %s, failed %s\n' "$cases" "$failed"
[[ "$failed" == 0 ]]
