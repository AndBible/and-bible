#!/bin/bash
# GitHub release of an AndBible version that was already bumped (scripts/bump-version.sh) and shipped to the internal
# tracks (make release-internal) and tested there: tags the tested commit, pushes the branch and the tag, approves the
# deployments the tag's CI runs wait for and, for a test build, deletes the older test releases. Run it on the host
# (it pushes; a container cannot).
#
# Usage: scripts/github-release.sh [--beta | --test] [--commit <sha>] [--dry-run]
#   (make github-release | github-release-beta | github-release-test [COMMIT=<sha>] [DRY_RUN=1])
#   (no kind) production release, tag production-<versionCode>; needs an empty versionNameSuffix at the commit
#   --beta    public beta, tag beta-<versionCode>; needs versionNameSuffix=-beta at the commit
#   --test    developer-only test build, tag test-<versionCode> (CI labels it -test); pushed without asking
#   --commit  the commit to tag: the one the internal release built (its summary prints the command). Default: the
#             commit of the local tag internal-<versionCode> that release-internal.sh sets, versionCode read at HEAD.
#   --dry-run run every check and print the plan; no tag, no push
# The versionCode, the suffix and the changelog are read at the tagged commit, never from the working tree, so commits
# made after the internal release do not change what is released. The commit must be an ancestor of HEAD (the pushed
# branch carries it).
# Env (tests): GH (gh), GIT_REMOTE (origin).

set -e

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

# Prints the SignPath approval reminder unless the "And Bible Builds" environment selects the test-signing
# policy (the workflow's default is release-signing): SignPath Foundation requires a person to approve every release signing request.
signpath_approval_reminder() {
    local env="And Bible Builds" policy org url="https://app.signpath.io"
    policy=$(gh variable get SIGNPATH_SIGNING_POLICY_SLUG --env "$env" 2>/dev/null || true)
    [[ "$policy" == "test-signing" ]] && return 0
    policy="${policy:-release-signing}"
    org=$(gh variable get SIGNPATH_ORGANIZATION_ID --env "$env" 2>/dev/null || true)
    [[ -n "$org" ]] && url="https://app.signpath.io/Web/$org/SigningRequests"
    printf '\a'
    echo "==> ACTION NEEDED: approve the Windows signing request ($policy) in SignPath: $url"
}

# Only follow a tag push for the released commit, created no earlier than this push.
# Approval means the gated job ran; Desktop additionally waits for its conclusion.
# Args: workflow, tag, timeout, interval, gated job, commit, push time (UTC), wait for completion.
approve_workflow_deployments() (
    local workflow="$1" tag="$2" timeout="$3" interval="$4" follow_job="$5"
    local commit="$6" pushed_at="$7" wait_for_completion="${8:-false}"
    local deadline=$((SECONDS + timeout)) next_progress=$((SECONDS + 60)) run_id=""
    local url="https://github.com/AndBible/and-bible/actions/workflows/$workflow"
    local run_status="" job_state="" deployments="" started="" denied_warned="" lookup_warned=""
    local error_file error="" eid can_approve args
    error_file=$(mktemp) || return 1
    trap 'rm -f "$error_file"' EXIT

    # Separate successful stdout from diagnostics; one stderr file belongs to this waiter.
    approval_lookup() {
        local target="$1" endpoint="$2" query="$3" response=""
        if response=$(gh api --method GET "$endpoint" --jq "$query" 2>"$error_file"); then
            printf -v "$target" '%s' "$response"
            return 0
        fi
        error=$(< "$error_file")
        if [[ -z "$run_id" && "$error" =~ HTTP[[:space:]](401|404) ]]; then
            echo "WARNING: Deployment approval lookup failed for $workflow ($tag): ${error//$'\n'/ }. Check: $url" >&2
            exit 1
        fi
        if [[ -z "$lookup_warned" ]]; then
            echo "WARNING: Deployment approval lookup failed for $workflow ($tag); retrying until the deadline. Check: $url" >&2
            lookup_warned=1
        fi
        return 1
    }

    echo "Waiting for $workflow run of $tag and gated job $follow_job..."
    while (( SECONDS < deadline )); do
        if (( SECONDS >= next_progress )); then
            echo "Still waiting for $workflow ($tag), job $follow_job. Check: $url"
            next_progress=$((SECONDS + 60))
        fi
        if [[ -z "$run_id" ]]; then
            approval_lookup run_id "repos/{owner}/{repo}/actions/workflows/$workflow/runs?branch=$tag&event=push&per_page=100" \
                "[.workflow_runs[] | select(.head_sha == \"$commit\" and .head_branch == \"$tag\" and .event == \"push\" and .created_at >= \"$pushed_at\")] | max_by(.created_at) | .id // empty" || true
            [[ -z "$run_id" ]] || url="https://github.com/AndBible/and-bible/actions/runs/$run_id"
        fi
        # Read jobs AFTER run status, so a just-completed run has fresh job evidence.
        if [[ -n "$run_id" ]] &&
            approval_lookup run_status "repos/{owner}/{repo}/actions/runs/$run_id" '"\(.status) \(.conclusion)"' &&
            approval_lookup job_state "repos/{owner}/{repo}/actions/runs/$run_id/jobs" ".jobs[] | select(.name == \"$follow_job\") | \"\\(.status) \\(.conclusion)\""; then
            if [[ "$job_state" == in_progress* || "$job_state" == 'completed success' ]]; then
                if [[ -z "$started" ]]; then
                    started=1
                    echo "Deployment approved for $workflow run $run_id ($follow_job ran)"
                    [[ "$wait_for_completion" == true ]] || return 0
                    [[ "$job_state" != in_progress* ]] || signpath_approval_reminder
                fi
            fi
            if [[ "$job_state" == completed* || "$run_status" == completed* ]]; then
                if [[ -n "$started" ]]; then
                    echo "$follow_job: ${job_state#completed } ($url)"
                    [[ "$job_state" == 'completed success' ]]
                    return
                fi
                local terminal_state="$run_status"
                [[ "$job_state" != completed* ]] || terminal_state="$job_state"
                echo "Run/job completed (${terminal_state#completed }) without the gated job running: $workflow ($tag), $follow_job. Check: $url" >&2
                return 1
            fi
            if approval_lookup deployments "repos/{owner}/{repo}/actions/runs/$run_id/pending_deployments" '.[] | "\(.environment.id) \(.current_user_can_approve == true)"'; then
                args=()
                while read -r eid can_approve; do
                    [[ -n "$eid" ]] || continue
                    if [[ "$can_approve" == true ]]; then
                        args+=(-F "environment_ids[]=$eid")
                    elif [[ -z "$denied_warned" ]]; then
                        echo "WARNING: Current user cannot approve a pending deployment for $workflow ($tag). Check: $url" >&2
                        denied_warned=1
                    fi
                done <<< "$deployments"
                if (( ${#args[@]} )); then
                    if ! error=$(gh api "repos/{owner}/{repo}/actions/runs/$run_id/pending_deployments" --method POST "${args[@]}" \
                        -f state=approved -f comment="Approved via github-release script" 2>&1 >/dev/null); then
                        echo "Deployment approval POST failed; retrying: ${error//$'\n'/ }. Check: $url" >&2
                    fi
                fi
            fi
        fi
        sleep "$interval"
    done
    echo "WARNING: Deployment approval/job wait for $workflow ($tag) exceeded $timeout seconds. ACTION NEEDED: check manually: $url" >&2
    return 1
)

# Retry wrapper for GPG/network operations that may fail due to YubiKey timeout
# Usage: retry_command <description> <command...>
retry_command() {
    local desc="$1"
    shift
    local max_retries=3
    local attempt=1
    while true; do
        if "$@"; then
            return 0
        fi
        if [[ $attempt -ge $max_retries ]]; then
            echo -e "${RED}$desc failed after $max_retries attempts.${NC}"
            return 1
        fi
        echo -e "${YELLOW}$desc failed (attempt $attempt/$max_retries). Touch YubiKey and press Enter to retry...${NC}"
        read -r
        attempt=$((attempt + 1))
    done
}


KIND=production
COMMIT_ARG=""
DRY=false
while [[ $# -gt 0 ]]; do
    case "$1" in
        --beta) [[ "$KIND" == production ]] || { echo -e "${RED}Error: --beta and --test cannot be combined${NC}"; exit 1; }; KIND=beta; shift ;;
        --test) [[ "$KIND" == production ]] || { echo -e "${RED}Error: --beta and --test cannot be combined${NC}"; exit 1; }; KIND=test; shift ;;
        --commit) [[ $# -ge 2 && -n "$2" ]] || { echo -e "${RED}Error: --commit needs a value${NC}"; exit 1; }; COMMIT_ARG="$2"; shift 2 ;;
        --dry-run) DRY=true; shift ;;
        *) echo -e "${RED}Error: Unknown argument: $1${NC}"; exit 1 ;;
    esac
done
GH="${GH:-gh}"
REMOTE="${GIT_REMOTE:-origin}"
die() { echo -e "${RED}Error: $*${NC}" >&2; exit 1; }

REPO_ROOT=$(git rev-parse --show-toplevel)
cd "$REPO_ROOT"
CHANGELOG_DIR="fastlane/metadata/android/en-US/changelogs"

# Reads key $2 of version.properties at commit $1 (trimmed like lib-version.sh).
prop_at() {
    local line val
    line=$(git show "$1:version.properties" | grep -m1 "^$2=") || return 1
    val="${line#*=}"
    val="${val#"${val%%[![:space:]]*}"}"
    printf '%s\n' "${val%"${val##*[![:space:]]}"}"
}

# 1. The commit.
if [[ -n "$COMMIT_ARG" ]]; then
    COMMIT=$(git rev-parse --verify --quiet "$COMMIT_ARG^{commit}") || die "no commit '$COMMIT_ARG'"
else
    HEAD_CODE=$(prop_at HEAD versionCode) || die "no versionCode in version.properties at HEAD"
    COMMIT=$(git rev-parse --verify --quiet "refs/tags/internal-$HEAD_CODE^{commit}") \
        || die "no local tag internal-$HEAD_CODE; pass the commit the internal release built: COMMIT=<sha> (its summary prints the command)"
fi
git merge-base --is-ancestor "$COMMIT" HEAD || die "commit ${COMMIT:0:10} is not an ancestor of HEAD; check out the branch that carries it"

# 2. Version, suffix and changelog at that commit.
CODE=$(prop_at "$COMMIT" versionCode) || die "no versionCode at ${COMMIT:0:10}"
[[ "$CODE" =~ ^[1-9][0-9]*$ ]] || die "versionCode '$CODE' at ${COMMIT:0:10} is not a canonical positive integer"
MM=$(prop_at "$COMMIT" versionMajorMinor) || die "no versionMajorMinor at ${COMMIT:0:10}"
SUFFIX=$(prop_at "$COMMIT" versionNameSuffix || true)
case "$KIND" in
    beta) [[ "$SUFFIX" == -beta ]] || die "a beta release needs versionNameSuffix=-beta at ${COMMIT:0:10} (found '$SUFFIX')" ;;
    production) [[ -z "$SUFFIX" ]] || die "a production release needs an empty versionNameSuffix at ${COMMIT:0:10} (found '$SUFFIX'); use --beta or --test" ;;
esac
git cat-file -e "$COMMIT:$CHANGELOG_DIR/$CODE.txt" 2>/dev/null || die "no changelog $CHANGELOG_DIR/$CODE.txt at ${COMMIT:0:10}"
if [[ "$KIND" == test ]]; then
    RELEASE_NAME="$MM.$CODE-test"   # CI builds a test-X tag with -PversionNameSuffix=-test (build-apk.yml)
    TAG_NAME="test-$CODE"; TAG_MESSAGE="Test release $RELEASE_NAME"
elif [[ "$KIND" == beta ]]; then
    RELEASE_NAME="$MM.$CODE$SUFFIX"
    TAG_NAME="beta-$CODE"; TAG_MESSAGE="Beta release $RELEASE_NAME"
else
    RELEASE_NAME="$MM.$CODE"
    TAG_NAME="production-$CODE"; TAG_MESSAGE="Release $RELEASE_NAME"
fi

# 3. The tag must be new, locally and on the remote.
git rev-parse --verify --quiet "refs/tags/$TAG_NAME" >/dev/null && die "tag $TAG_NAME already exists locally"
if REMOTE_TAGS=$(git ls-remote --tags "$REMOTE" "refs/tags/$TAG_NAME" 2>/dev/null); then
    [[ -z "$REMOTE_TAGS" ]] || die "tag $TAG_NAME already exists on $REMOTE"
else
    echo -e "${YELLOW}Warning: could not list the tags of $REMOTE; the push will tell.${NC}"
fi

echo -e "${GREEN}GitHub $KIND release $RELEASE_NAME${NC}"
echo "  commit:    $(git log -1 --format='%h %s' "$COMMIT")"
echo "  tag:       $TAG_NAME"
AHEAD=$(git rev-list --count "$COMMIT"..HEAD)
[[ "$AHEAD" == 0 ]] || echo "  note:      HEAD is $AHEAD commit(s) ahead; they are pushed with the branch but not released"
echo "  changelog: $CHANGELOG_DIR/$CODE.txt"
echo "=========================="
git show "$COMMIT:$CHANGELOG_DIR/$CODE.txt"
echo "=========================="
if [[ "$DRY" == true ]]; then
    echo "[dry-run] git tag -a $TAG_NAME ${COMMIT:0:10}; git push $REMOTE; git push $REMOTE $TAG_NAME"
    exit 0
fi

# 4. Tag and push.
if [[ "$KIND" == test ]]; then
    push_response=y
else
    echo -e "${YELLOW}Tag ${COMMIT:0:10} as $TAG_NAME and push the branch and the tag to GitHub? (y/n)${NC}"
    read -r push_response
fi
[[ "$push_response" =~ ^[Yy]$ ]] || { echo "Aborted; nothing was tagged or pushed."; exit 1; }
git tag -a "$TAG_NAME" -m "$TAG_MESSAGE" "$COMMIT"
echo -e "${GREEN}✓ Tag $TAG_NAME created${NC}"
retry_command "Push to GitHub" git push "$REMOTE"
# Capture before pushing: the workflow may be registered before git push returns.
TAG_PUSHED_AT=$(date -u +%Y-%m-%dT%H:%M:%SZ)
retry_command "Push tag to GitHub" git push "$REMOTE" "$TAG_NAME"
echo -e "${GREEN}✓ Branch and tag pushed${NC}"

# 5. Approve the deployments the tag's CI runs wait for (environment "And Bible Builds" needs approval). The Desktop
# workflow's windows-sign job (SignPath) asks only after the ~20 minute Windows build, so its waiter runs in the
# background while the APK approval and the release cleanup go on; the script waits for it at the end.
DESKTOP_WAITER_PID=""
if command -v "$GH" >/dev/null 2>&1; then
    gh() { command "$GH" "$@"; }   # `command`: never this function itself when GH=gh
    approve_workflow_deployments desktop.yml "$TAG_NAME" 14400 30 windows-sign "$COMMIT" "$TAG_PUSHED_AT" true \
        > >(sed -u 's/^/[Windows signing] /') 2>&1 &
    DESKTOP_WAITER_PID=$!
    echo "Windows signing approval runs in the background; the script waits for it at the end."
    if ! approve_workflow_deployments build-apk.yml "$TAG_NAME" 900 5 build "$COMMIT" "$TAG_PUSHED_AT"; then
        echo -e "${YELLOW}WARNING: APK deployment approval failed; the release needs manual attention at the URL above.${NC}" >&2
    fi

    # Keep only the most recent test release.
    if [[ "$KIND" == test ]]; then
        echo "Cleaning up old test releases (keeping only the most recent)..."
        OLD_RELEASES=$(gh release list --limit 200 --json tagName \
            --jq '[.[] | select(.tagName | startswith("test-"))] | .[1:] | .[].tagName' 2>/dev/null || echo "")
        while IFS= read -r tag; do
            [[ -n "$tag" ]] || continue
            echo "  Deleting: $tag"
            gh release delete "$tag" --yes --cleanup-tag 2>/dev/null || echo "  Warning: Could not delete $tag"
        done <<< "$OLD_RELEASES"
    fi
else
    echo -e "${YELLOW}Warning: $GH not found; approve the deployments of the tag's CI runs by hand.${NC}"
fi

if [[ -n "$DESKTOP_WAITER_PID" ]]; then
    echo "Waiting for the Desktop workflow's Windows signing (up to 4 hours, including the SignPath approval; Ctrl-C only stops waiting)..."
    if wait "$DESKTOP_WAITER_PID"; then
        echo -e "${GREEN}✓ Windows packages signed${NC}"
    else
        echo -e "${YELLOW}Warning: Windows signing did not finish successfully; see the [Windows signing] lines above.${NC}"
    fi
fi
echo -e "${GREEN}GitHub release $RELEASE_NAME ($TAG_NAME) started.${NC}"
