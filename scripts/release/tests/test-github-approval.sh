#!/usr/bin/env bash
# Offline deployment approval regression tests. Only gh is stubbed; jq evaluates the real queries.
# Run: bash scripts/release/tests/test-github-approval.sh
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
if [[ "$method" == GET && "$SCENARIO" == success-stderr ]]; then
    printf 'gh: diagnostic only (HTTP 403)\n' >&2
fi
if [[ "$method" == GET && "$SCENARIO" == get-client-* ]]; then
    case "$SCENARIO:$endpoint" in
        get-client-lookup:*/workflows/*|get-client-pending:*/pending_deployments|get-client-approvals:*/approvals|get-client-jobs:*/jobs|get-client-status:*/actions/runs/202)
            printf 'gh: forbidden (HTTP 403)\n' >&2; exit 1 ;;
    esac
fi
count() {
    local n=0
    [[ ! -f "$STATE/$1" ]] || read -r n < "$STATE/$1"
    n=$((n + 1))
    printf '%s\n' "$n" > "$STATE/$1"
    printf '%s\n' "$n"
}
if [[ "$SCENARIO" == rate-* && ! -f "$STATE/rate-failed" ]]; then
    case "$SCENARIO:$method:$endpoint" in
        rate-post-*:POST:*|rate-lookup-*:GET:*/workflows/*|rate-pending-*:GET:*/pending_deployments|rate-approvals-*:GET:*/approvals|rate-status-*:GET:*/actions/runs/202|rate-jobs-*:GET:*/jobs)
            touch "$STATE/rate-failed"
            case "$SCENARIO" in
                *-429) printf 'gh: Too Many Requests (HTTP 429)\n' >&2 ;;
                *-secondary) printf 'gh: secondary rate limit (HTTP 403)\n' >&2 ;;
                *-primary) printf 'gh: API rate limit exceeded (HTTP 403)\n' >&2 ;;
            esac
            exit 1 ;;
    esac
fi
case "$endpoint" in
    */workflows/*/runs\?*)
        n=$(count lookups)
        if [[ "$endpoint" == *head_sha=* ]]; then
            json='{"workflow_runs":[{"id":101,"status":"completed","head_branch":"older-tag","event":"push"}]}'
        elif [[ "$endpoint" != *'branch=beta-1284&event=push'* ]]; then
            printf 'Missing release tag/event filter\n' >&2; exit 1
        elif [[ "$SCENARIO" == missing || ( "$SCENARIO" == delayed && "$n" -lt 3 ) ]]; then
            json='{"workflow_runs":[]}'
        elif [[ "$SCENARIO" == quiet-lookup || ( "$SCENARIO" == lookup-error && "$n" == 1 ) ]]; then
            printf 'temporary lookup error\n' >&2; exit 1
        elif [[ "$SCENARIO" == replaced && "$n" == 1 ]]; then
            json='{"workflow_runs":[{"id":303,"status":"in_progress","head_branch":"beta-1284","event":"push"}]}'
        else
            json='{"workflow_runs":[{"id":202,"status":"waiting","head_branch":"beta-1284","event":"push"}]}'
        fi ;;
    */pending_deployments)
        if [[ "$method" == POST ]]; then
            n=$(count posts)
            if [[ "$SCENARIO" == lost-response || "$SCENARIO" == partial-approval || "$SCENARIO" == history-retry || ( "$SCENARIO" == partial-retry && "$n" == 1 ) ]]; then
                printf '%s\n' "$endpoint" > "$STATE/approved"
                printf 'approval response lost\n' >&2; exit 1
            fi
            if [[ "$SCENARIO" == post-race-* && ( "$SCENARIO" != post-race-new-eligible || "$n" == 1 ) ]]; then
                printf '%s\n' "$endpoint" > "$STATE/approved"
                status="${SCENARIO#post-race-}"
                [[ "$SCENARIO" != post-race-new-* ]] || status=422
                printf 'gh: No pending deployment requests (HTTP %s)\n' "$status" >&2; exit 1
            fi
            if [[ "$SCENARIO" == client-* ]]; then
                printf 'gh: approval forbidden (HTTP %s)\n' "${SCENARIO#client-}" >&2; exit 1
            fi
            if [[ "$SCENARIO" == server-error && "$n" == 1 ]]; then
                printf 'gh: unavailable (HTTP 503)\n' >&2; exit 1
            fi
            if [[ "$SCENARIO" == post-error || ( "$SCENARIO" == retry && "$n" == 1 ) ]]; then
                printf 'temporary approval error\n' >&2; exit 1
            fi
            printf '%s\n' "$endpoint" > "$STATE/approved"
            exit 0
        fi
        n=$(count pending)
        if [[ "$SCENARIO" == quiet-pending || ( "$SCENARIO" == pending-error && "$n" == 1 ) ]]; then
            printf 'temporary pending error\n' >&2; exit 1
        fi
        if [[ "$SCENARIO" == post-race-new-denied && -f "$STATE/approved" ]]; then
            json='[{"environment":{"id":44},"current_user_can_approve":false}]'
        elif [[ "$SCENARIO" == post-race-new-eligible && -f "$STATE/approved" && "$(< "$STATE/posts")" == 1 ]]; then
            json='[{"environment":{"id":44},"current_user_can_approve":true}]'
        elif [[ "$SCENARIO" == partial-retry && "$n" == 2 ]]; then
            json='[{"environment":{"id":43},"current_user_can_approve":true}]'
        elif [[ "$endpoint" == */101/* || "$endpoint" == */303/* || "$SCENARIO" == waiting || "$SCENARIO" == completed-no-approval || "$SCENARIO" == skipped-signing || "$SCENARIO" == completed-success-no-approval || "$SCENARIO" == skipped-approved || "$SCENARIO" == manual-* || "$SCENARIO" == race-* || "$SCENARIO" == quiet-* || "$SCENARIO" == get-client-* || "$SCENARIO" == rate-approvals-* || "$SCENARIO" == rate-status-* || "$SCENARIO" == rate-jobs-* || -f "$STATE/approved" ]]; then
            json='[]'
        elif [[ "$SCENARIO" == denied-false ]]; then
            json='[{"environment":{"id":42},"current_user_can_approve":false}]'
        elif [[ "$SCENARIO" == denied-missing ]]; then
            json='[{"environment":{"id":42}}]'
        elif [[ "$SCENARIO" == non-approvable ]]; then
            json='[{"environment":{"id":42},"current_user_can_approve":true},{"environment":{"id":43},"current_user_can_approve":false},{"environment":{"id":44}}]'
        else
            json='[{"environment":{"id":42},"current_user_can_approve":true},{"environment":{"id":43},"current_user_can_approve":true}]'
        fi ;;
    */approvals)
        if [[ "$SCENARIO" == quiet-approvals ]]; then printf 'temporary approvals error\n' >&2; exit 1; fi
        n=$(count history)
        if [[ "$SCENARIO" == history-retry && "$n" == 1 ]]; then printf 'temporary approvals error\n' >&2; exit 1; fi
        if [[ "$SCENARIO" == race-* && "$n" -gt 1 ]]; then
            json='[{"state":"approved","environments":[{"id":42},{"id":43}]}]'
        elif [[ "$SCENARIO" == post-race-new-eligible && -f "$STATE/posts" && "$(< "$STATE/posts")" -gt 1 ]]; then
            json='[{"state":"approved","environments":[{"id":42},{"id":43},{"id":44}]}]'
        elif [[ "$SCENARIO" == partial-retry ]]; then
            json='[{"state":"approved","environments":[{"id":43}]}]'
        elif [[ "$SCENARIO" == partial-approval ]]; then
            json='[{"state":"approved","environments":[{"id":42}]}]'
        elif [[ -f "$STATE/approved" || "$SCENARIO" == manual-apk || "$SCENARIO" == manual-desktop || "$SCENARIO" == skipped-approved ]]; then
            json='[{"state":"approved","environments":[{"id":42},{"id":43}]}]'
        else
            json='[]'
        fi ;;
    */jobs)
        if [[ "$SCENARIO" == quiet-jobs ]]; then printf 'temporary jobs error\n' >&2; exit 1; fi
        n=$(count jobs)
        if [[ "$SCENARIO" == quiet-* || "$SCENARIO" == get-client-* ]]; then
            json='{"jobs":[{"name":"windows-sign","status":"queued","conclusion":null}]}'
        elif [[ "$SCENARIO" == race-desktop ]]; then
            json='{"jobs":[{"name":"windows-sign","status":"completed","conclusion":"failure"}]}'
        elif [[ "$SCENARIO" == completed-no-approval ]]; then
            json='{"jobs":[]}'
        elif [[ "$SCENARIO" == skipped-signing || "$SCENARIO" == skipped-approved ]]; then
            json='{"jobs":[{"name":"windows-sign","status":"completed","conclusion":"skipped"}]}'
        elif [[ "$SCENARIO" == follow-failed ]]; then
            json='{"jobs":[{"name":"windows-sign","status":"completed","conclusion":"failure"}]}'
        elif [[ "$n" == 1 ]]; then
            json='{"jobs":[{"name":"windows-sign","status":"in_progress","conclusion":null}]}'
        else
            json='{"jobs":[{"name":"windows-sign","status":"completed","conclusion":"success"}]}'
        fi ;;
    */actions/runs/*)
        if [[ "$SCENARIO" == quiet-status ]]; then printf 'temporary status error\n' >&2; exit 1; fi
        if [[ "$SCENARIO" == completed-success-no-approval ]]; then
            json='{"status":"completed","conclusion":"success"}'
        elif [[ "$SCENARIO" == completed-no-approval || "$SCENARIO" == manual-apk || "$SCENARIO" == history-retry || "$SCENARIO" == race-* ]]; then
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
cases=0
run_case() {
    export SCENARIO="$1" STATE="$T/$1"
    local workflow=build-apk.yml
    [[ -z "${2:-}" ]] || workflow=desktop.yml
    cases=$((cases + 1))
    mkdir -p "$STATE"
    SECONDS=0
    rc=0
    approve_workflow_deployments "$workflow" beta-1284 8 2 "${2:-}" > "$STATE/output" 2>&1 || rc=$?
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
for scenario in delayed tag-only replaced retry server-error lookup-error pending-error; do
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
for scenario in completed-no-approval skipped-signing skipped-approved completed-success-no-approval; do
    follow_job=windows-sign
    [[ "$scenario" != completed-success-no-approval ]] || follow_job=""
    run_case "$scenario" "$follow_job"
    [[ "$rc" != 0 && "$SECONDS" -lt 2 ]] || { fail "$scenario: terminal run/job did not fail immediately"; failed=$((failed + 1)); }
    grep -q 'https://github.com/AndBible/and-bible/actions/runs/202' "$STATE/output" || { fail "$scenario: missing run URL"; failed=$((failed + 1)); }
    grep -Eqi 'did not run|build.*failed|completed.*without.*approval' "$STATE/output" || { fail "$scenario: missing actionable run/job explanation"; failed=$((failed + 1)); }
    if grep -qi 'WARNING.*Deployment approval' "$STATE/output"; then
        fail "$scenario: terminal state misreported as approval failure"; failed=$((failed + 1))
    fi
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
for scenario in client-400 client-401 client-403 client-404 client-422; do
    run_case "$scenario"
    assert_warning 'https://github.com/AndBible/and-bible/actions/runs/202' || failed=$((failed + 1))
    [[ "$(< "$STATE/posts")" == 1 && "$SECONDS" -lt 2 ]] || { fail "$scenario: client error retried"; failed=$((failed + 1)); }
done
run_case non-approvable
[[ "$rc" == 0 && -f "$STATE/approved" ]] || { fail 'non-approvable: eligible deployment not approved'; failed=$((failed + 1)); }
grep -q 'environment_ids\[\]=42' "$STATE/calls" || { fail 'non-approvable: missing eligible environment'; failed=$((failed + 1)); }
if grep -Eq 'environment_ids\[\]=(43|44)' "$STATE/calls"; then
    fail 'non-approvable: posted unauthorized environments'; failed=$((failed + 1))
fi
for scenario in quiet-lookup quiet-pending quiet-approvals quiet-status quiet-jobs; do
    run_case "$scenario" windows-sign
    [[ "$rc" != 0 ]] || { fail "$scenario: expected bounded failure"; failed=$((failed + 1)); }
    if grep -q 'temporary .* error' "$STATE/output"; then
        fail "$scenario: polling GET leaked stderr"; failed=$((failed + 1))
    fi
    [[ "$(grep -c 'lookup failed' "$STATE/output" || true)" == 1 ]] || { fail "$scenario: expected one lookup failure summary"; failed=$((failed + 1)); }
done
run_case partial-retry
assert_warning 'https://github.com/AndBible/and-bible/actions/runs/202' || failed=$((failed + 1))
run_case history-retry
[[ "$rc" == 0 ]] || { fail 'history-retry: transient history failure caused terminal failure'; failed=$((failed + 1)); }
for scenario in get-client-lookup get-client-pending get-client-approvals get-client-status get-client-jobs; do
    run_case "$scenario" windows-sign
    [[ "$rc" != 0 && "$SECONDS" -lt 2 ]] || { fail "$scenario: permanent GET error retried"; failed=$((failed + 1)); }
    grep -q 'https://github.com/AndBible/and-bible/actions/' "$STATE/output" || { fail "$scenario: missing action URL"; failed=$((failed + 1)); }
done
run_case race-apk
[[ "$rc" == 0 ]] || { fail 'race-apk: approval between history and status was missed'; failed=$((failed + 1)); }
run_case race-desktop windows-sign
[[ "$rc" != 0 ]] || { fail 'race-desktop: failed signing reported success'; failed=$((failed + 1)); }
grep -q 'windows-sign: failure' "$STATE/output" || { fail 'race-desktop: signing conclusion was not followed'; failed=$((failed + 1)); }
run_case success-stderr
assert_approved || failed=$((failed + 1))
if grep -q 'diagnostic only' "$STATE/output"; then
    fail 'success-stderr: successful lookup stderr contaminated data/output'; failed=$((failed + 1))
fi
# Rate limits are transient on every lookup boundary and on the approval POST.
for endpoint in post lookup pending approvals status jobs; do
    for limit in 429 secondary primary; do
        run_case "rate-$endpoint-$limit" windows-sign
        [[ "$rc" == 0 && -f "$STATE/rate-failed" ]] || { fail "$SCENARIO: rate limit was not retried to success"; failed=$((failed + 1)); }
        grep -q 'windows-sign: success' "$STATE/output" || { fail "$SCENARIO: signing was not followed after rate limit"; failed=$((failed + 1)); }
    done
done
for scenario in denied-false denied-missing; do
    run_case "$scenario"
    assert_warning 'https://github.com/AndBible/and-bible/actions/runs/202' || failed=$((failed + 1))
    [[ "$SECONDS" -lt 2 && ! -f "$STATE/posts" ]] || { fail "$SCENARIO: unauthorized environment waited or was posted"; failed=$((failed + 1)); }
done
# An approved history entry cannot hide a new unapprovable pending environment.
run_case post-race-new-denied
assert_warning 'https://github.com/AndBible/and-bible/actions/runs/202' || failed=$((failed + 1))
[[ "$SECONDS" -lt 2 ]] || { fail "$SCENARIO: new permission blocker was not immediate"; failed=$((failed + 1)); }
run_case post-race-new-eligible
[[ "$rc" == 0 && "$(< "$STATE/posts")" == 2 ]] || { fail "$SCENARIO: new eligible environment was not approved"; failed=$((failed + 1)); }
grep -q 'environment_ids\[\]=44' "$STATE/calls" || { fail "$SCENARIO: new environment omitted from approval"; failed=$((failed + 1)); }
# A rejected POST can race with a manual or concurrent approval.
for status in 400 401 403 404 422; do
    run_case "post-race-$status"
    [[ "$rc" == 0 ]] || { fail "$SCENARIO: concurrent approval was not recovered"; failed=$((failed + 1)); }
    [[ "$(< "$STATE/posts")" == 1 ]] || { fail "$SCENARIO: repeated approval POST"; failed=$((failed + 1)); }
done
printf 'scenarios %s, failed %s\n' "$cases" "$failed"
[[ "$failed" == 0 ]]
