#!/usr/bin/env bash
# Tests for scripts/bump-version.sh and scripts/github-release.sh in a throwaway git repository with a bare "origin".
# claude and gh are stubs (CLAUDE, GH); no network.
# Run: bash scripts/tests/version-release-scripts-test.sh [name-filter]
set -uo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
REAL_REPO="$(cd "$HERE/../.." && pwd)"
T="$(mktemp -d)"
FILTER="${1:-}"
trap 'rm -rf "$T"' EXIT
pass=0; failn=0; cur=
fail() { echo "FAIL [$cur]: $*" >&2; : > "$T/failflag"; echo 1; }

cat > "$T/claude" <<'EOS'
#!/usr/bin/env bash
cat > "$STUB_DIR/claude-stdin"
echo "- Stub item (#1)"
EOS
chmod +x "$T/claude"
export STUB_DIR="$T"

# A fresh repository R at version 6.0.100-beta with a changelog, the metainfo file and a bare origin.
setup() {
    R="$T/repo"; rm -rf "$R" "$T/origin.git" "$T/claude-stdin"
    git init -q --bare "$T/origin.git"
    git init -q -b main "$R"
    mkdir -p "$R/scripts" "$R/fastlane/metadata/android/en-US/changelogs" "$R/desktopApp/packaging/linux"
    cp "$REAL_REPO/scripts/bump-version.sh" "$REAL_REPO/scripts/github-release.sh" "$REAL_REPO/scripts/lib-version.sh" "$R/scripts/"
    printf 'versionMajorMinor=6.0\nversionCode=100\nversionNameSuffix=-beta\n' > "$R/version.properties"
    printf -- '- Old item\n\nAndBible 6.0 public beta!\n\nFooter line\n' > "$R/fastlane/metadata/android/en-US/changelogs/100.txt"
    printf '<releases>\n  <release version="6.0.100" date="2026-01-01"/>\n</releases>\n' \
        > "$R/desktopApp/packaging/linux/org.andbible.AndBible.metainfo.xml"
    (cd "$R" && git add -A && git -c user.name=t -c user.email=t@t commit -qm "Increment version to 6.0.100-beta" \
        && git remote add origin "$T/origin.git" && git push -q -u origin main 2>/dev/null)
    commit_change "feature.txt" "Fixes #42 (a user-visible fix)"
}
commit_change() { (cd "$R" && echo "$RANDOM" >> "$1" && git add "$1" && git -c user.name=t -c user.email=t@t commit -qm "$2"); }
bump() { (cd "$R" && GIT_AUTHOR_NAME=t GIT_AUTHOR_EMAIL=t@t GIT_COMMITTER_NAME=t GIT_COMMITTER_EMAIL=t@t \
    CLAUDE="${CLAUDE_STUB-$T/claude}" scripts/bump-version.sh "$@" < /dev/null); }
ghr() { (cd "$R" && GIT_AUTHOR_NAME=t GIT_AUTHOR_EMAIL=t@t GIT_COMMITTER_NAME=t GIT_COMMITTER_EMAIL=t@t \
    GH="$T/no-gh" scripts/github-release.sh "$@"); }
CL() { echo "$R/fastlane/metadata/android/en-US/changelogs/$1.txt"; }

test_bump_increments_commits_and_does_not_tag() {
    setup; bump > "$T/out" 2>&1 || return "$(fail "rc $? $(cat "$T/out")")"
    grep -q '^versionCode=101$' "$R/version.properties" || return "$(fail "version: $(cat "$R/version.properties")")"
    grep -q '^versionNameSuffix=-beta$' "$R/version.properties" || return "$(fail "suffix lost")"
    grep -q 'release version="6.0.101" date="'"$(date +%F)"'"' "$R/desktopApp/packaging/linux/org.andbible.AndBible.metainfo.xml" \
        || return "$(fail "metainfo not updated")"
    [ "$(cd "$R" && git log -1 --format=%s)" = "Increment version to 6.0.101-beta" ] || return "$(fail "commit subject")"
    [ -z "$(cd "$R" && git status --porcelain)" ] || return "$(fail "tree not clean")"
    [ -z "$(cd "$R" && git tag)" ] || return "$(fail "tagged: $(cd "$R" && git tag)")"
    [ "$(cd "$R" && git rev-list --count origin/main..HEAD)" = 2 ] || return "$(fail "pushed or extra commits")"
}
test_bump_changelog_summary_above_footer_from_previous_bump() {
    setup; bump > "$T/out" 2>&1 || return "$(fail "rc $?")"
    [ "$(head -n 1 "$(CL 101)")" = "- Stub item (#1)" ] || return "$(fail "summary: $(cat "$(CL 101)")")"
    grep -q '^AndBible 6.0 public beta!$' "$(CL 101)" && grep -q '^Footer line$' "$(CL 101)" || return "$(fail "footer lost")"
    grep -q 'Old item' "$(CL 101)" && return "$(fail "previous summary carried over")"
    grep -q 'Fixes #42' "$T/claude-stdin" || return "$(fail "claude did not get the commit since the bump")"
    grep -q 'Increment version to 6.0.100' "$T/claude-stdin" && return "$(fail "range includes the previous bump commit")"
    return 0
}
test_bump_range_starts_at_previous_bump_not_root() {
    setup; bump > /dev/null 2>&1; commit_change other.txt "Fixes #43 (second)"
    bump > "$T/out" 2>&1 || return "$(fail "rc $?")"
    grep -q 'Fixes #43' "$T/claude-stdin" || return "$(fail "missing new commit")"
    grep -q 'Fixes #42' "$T/claude-stdin" && return "$(fail "range reaches before the previous bump")"
    return 0
}
test_bump_without_claude_copies_previous_changelog() {
    setup; CLAUDE_STUB="$T/none" bump > "$T/out" 2>&1 || return "$(fail "rc $?")"
    cmp -s "$(CL 100)" "$(CL 101)" || return "$(fail "not copied")"
}
test_bump_step() {
    setup; bump --step 10 > /dev/null 2>&1 || return "$(fail "rc $?")"
    grep -q '^versionCode=110$' "$R/version.properties" && [ -f "$(CL 110)" ] || return "$(fail "step not applied")"
}
test_bump_refuses_dirty_tree() {
    setup; echo x >> "$R/feature.txt"
    bump > "$T/out" 2>&1 && return "$(fail "accepted a dirty tree")"
    grep -q '^versionCode=100$' "$R/version.properties" || return "$(fail "changed version")"
}
test_bump_refuses_bad_step() {
    setup; bump --step 0 > /dev/null 2>&1 && return "$(fail "accepted step 0")"; return 0
}

# github-release: bump, then commits after the internal release.
released_setup() {
    setup; bump > /dev/null 2>&1; commit_change fix.txt "Fix after bump"
    TESTED=$(cd "$R" && git rev-parse HEAD)
    commit_change later.txt "Untested later work"
}
test_ghr_tags_given_commit_and_pushes() {
    released_setup
    echo y | ghr --beta --commit "$TESTED" > "$T/out" 2>&1 || return "$(fail "rc $? $(cat "$T/out")")"
    [ "$(cd "$R" && git rev-parse 'beta-101^{commit}')" = "$TESTED" ] || return "$(fail "tag not at the tested commit")"
    [ "$(git -C "$T/origin.git" rev-parse 'beta-101^{commit}' 2>/dev/null)" = "$TESTED" ] || return "$(fail "tag not pushed")"
    [ "$(git -C "$T/origin.git" rev-parse main)" = "$(cd "$R" && git rev-parse HEAD)" ] || return "$(fail "branch not pushed")"
    grep -q 'Stub item' "$T/out" || return "$(fail "changelog not shown")"
    grep -q '1 commit(s) ahead' "$T/out" || return "$(fail "no ahead note")"
}
test_ghr_default_commit_from_internal_tag() {
    released_setup; (cd "$R" && git tag internal-101 "$TESTED")
    echo y | ghr --beta > "$T/out" 2>&1 || return "$(fail "rc $? $(cat "$T/out")")"
    [ "$(cd "$R" && git rev-parse 'beta-101^{commit}')" = "$TESTED" ] || return "$(fail "tag not at internal-101")"
}
test_ghr_without_commit_or_internal_tag_refuses() {
    released_setup
    echo y | ghr --beta > "$T/out" 2>&1 && return "$(fail "tagged without a commit")"
    grep -q 'COMMIT=' "$T/out" || return "$(fail "no hint: $(cat "$T/out")")"
}
test_ghr_reads_version_at_commit_not_worktree() {
    released_setup
    (cd "$R" && sed -i 's/^versionCode=.*/versionCode=200/' version.properties && git -c user.name=t -c user.email=t@t commit -qam "later bump")
    echo y | ghr --beta --commit "$TESTED" > "$T/out" 2>&1 || return "$(fail "rc $? $(cat "$T/out")")"
    (cd "$R" && git rev-parse -q --verify beta-101 >/dev/null) || return "$(fail "wrong tag: $(cd "$R" && git tag)")"
}
test_ghr_refuses_non_ancestor() {
    released_setup
    (cd "$R" && git checkout -q -b side HEAD~2 && echo s > s.txt && git add s.txt && git -c user.name=t -c user.email=t@t commit -qm side)
    SIDE=$(cd "$R" && git rev-parse HEAD); (cd "$R" && git checkout -q main)
    echo y | ghr --beta --commit "$SIDE" > "$T/out" 2>&1 && return "$(fail "tagged a non-ancestor")"
    grep -q 'not an ancestor' "$T/out" || return "$(fail "$(cat "$T/out")")"
}
test_ghr_suffix_rules() {
    released_setup
    echo y | ghr --commit "$TESTED" > "$T/out" 2>&1 && return "$(fail "production with -beta suffix")"
    grep -q 'empty versionNameSuffix' "$T/out" || return "$(fail "$(cat "$T/out")")"
    echo y | ghr --test --commit "$TESTED" > "$T/out" 2>&1 || return "$(fail "test build refused: $(cat "$T/out")")"
    (cd "$R" && git rev-parse -q --verify test-101 >/dev/null) || return "$(fail "no test tag")"
    grep -q 'Test release 6.0.101-test' <(cd "$R" && git tag -n1 test-101) || return "$(fail "test tag message")"
}
test_ghr_refuses_existing_tag_on_remote() {
    released_setup
    (cd "$R" && git tag beta-101 HEAD~2 && git push -q origin beta-101 2>/dev/null && git tag -d beta-101 >/dev/null)
    echo y | ghr --beta --commit "$TESTED" > "$T/out" 2>&1 && return "$(fail "re-tagged")"
    grep -q 'already exists on origin' "$T/out" || return "$(fail "$(cat "$T/out")")"
}
test_ghr_answer_no_tags_nothing() {
    released_setup
    echo n | ghr --beta --commit "$TESTED" > "$T/out" 2>&1 && return "$(fail "rc 0 on no")"
    [ -z "$(cd "$R" && git tag)" ] || return "$(fail "tagged")"
}
test_ghr_dry_run_tags_nothing() {
    released_setup
    ghr --beta --commit "$TESTED" --dry-run < /dev/null > "$T/out" 2>&1 || return "$(fail "rc $? $(cat "$T/out")")"
    [ -z "$(cd "$R" && git tag)" ] || return "$(fail "tagged")"
    grep -q 'dry-run' "$T/out" || return "$(fail "no plan")"
}
test_ghr_missing_changelog_refuses() {
    released_setup
    (cd "$R" && git rm -q fastlane/metadata/android/en-US/changelogs/101.txt && git -c user.name=t -c user.email=t@t commit -qm rm)
    NOCL=$(cd "$R" && git rev-parse HEAD)
    echo y | ghr --beta --commit "$NOCL" > "$T/out" 2>&1 && return "$(fail "accepted")"
    grep -q 'no changelog' "$T/out" || return "$(fail "$(cat "$T/out")")"
}
test_make_targets_map_to_scripts() {
    local out
    out=$(make -n -C "$REAL_REPO" github-release-beta COMMIT=abc DRY_RUN=1 2>&1)
    [[ "$out" == *"scripts/github-release.sh --beta --commit abc --dry-run"* ]] || return "$(fail "beta: $out")"
    out=$(make -n -C "$REAL_REPO" github-release-test 2>&1)
    [[ "$out" == *"scripts/github-release.sh --test"* ]] || return "$(fail "test: $out")"
    out=$(make -n -C "$REAL_REPO" github-release 2>&1)
    [[ "$out" == *"scripts/github-release.sh"* && "$out" != *"--beta"* && "$out" != *"--test"* ]] || return "$(fail "prod: $out")"
    out=$(make -n -C "$REAL_REPO" bump-version STEP=5 2>&1)
    [[ "$out" == *"scripts/bump-version.sh --step 5"* ]] || return "$(fail "bump: $out")"
}

for t in $(declare -F | awk '{print $3}' | grep '^test_'); do
    [ -z "$FILTER" ] || [[ "$t" == *"$FILTER"* ]] || continue
    cur=$t; rm -f "$T/failflag"
    "$t"; rc=$?
    if [ $rc -ne 0 ] || [ -e "$T/failflag" ]; then failn=$((failn + 1)); else pass=$((pass + 1)); echo "ok $t"; fi
done
echo "passed $pass, failed $failn"
[ "$failn" = 0 ]
