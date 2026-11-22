#!/bin/bash
# Version bump for AndBible: the first step of a release, before the internal release (make release-internal).
# Increments versionCode in version.properties, updates the desktop AppStream release, writes the changelog of the
# new versionCode and commits. No tag, no push: it runs in a container. After testing the internal build, the
# GitHub release (tag + push + CI approvals) is scripts/github-release.sh, run on the host.
#
# Usage: scripts/bump-version.sh [--step N] [--yes]
#   (make bump-version [STEP=N] [YES=1])
#   --step N  increase versionCode by N instead of 1, e.g. to leave a gap of version codes free for
#             current-stable bug-fix releases
#   --yes     accept the generated changelog without asking (also when stdin is not a terminal)
#
# The changelog lists the user-facing changes since the previous version bump (the last commit that changed
# versionCode), summarized by `claude -p` when available, above the footer carried over from the previous
# changelog (from the line that introduces the major.minor version). Without claude the previous changelog is
# copied; edit the file and commit the fix before the internal release.
# Env (tests): CLAUDE (claude), EDITOR.

set -e

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

STEP=1
YES=false
while [[ $# -gt 0 ]]; do
    case "$1" in
        --step) [[ $# -ge 2 ]] || { echo -e "${RED}Error: --step needs a value${NC}"; exit 1; }; STEP="$2"; shift 2 ;;
        --yes) YES=true; shift ;;
        *) echo -e "${RED}Error: Unknown argument: $1${NC}"; exit 1 ;;
    esac
done
if ! [[ "$STEP" =~ ^[1-9][0-9]*$ ]]; then
    echo -e "${RED}Error: --step must be a positive integer, got '$STEP'${NC}"
    exit 1
fi
[[ -t 0 ]] || YES=true
CLAUDE="${CLAUDE:-claude}"

REPO_ROOT=$(git rev-parse --show-toplevel)
cd "$REPO_ROOT"
# shellcheck source=lib-version.sh
. "$REPO_ROOT/scripts/lib-version.sh"
VERSION_FILE_REL="version.properties"
CHANGELOG_DIR="fastlane/metadata/android/en-US/changelogs"
# The desktop AppStream file carries the current release (LinuxPackagingMetadataTest checks it).
METAINFO_REL="desktopApp/packaging/linux/org.andbible.AndBible.metainfo.xml"

if [[ -n $(git status --porcelain) ]]; then
    echo -e "${RED}Error: You have uncommitted changes. Please commit or stash them first.${NC}"
    git status --short
    exit 1
fi

CURRENT_VERSION_CODE=$(version_code)
CURRENT_VERSION_NAME=$(version_name)
echo "Current version: $CURRENT_VERSION_NAME (code: $CURRENT_VERSION_CODE)"

# versionNameSuffix (e.g. -beta) stays in the file and is not touched by a bump.
NEW_VERSION_CODE=$((CURRENT_VERSION_CODE + STEP))
NEW_VERSION_NAME="$(version_major_minor).${NEW_VERSION_CODE}$(_version_get versionNameSuffix)"
# The numeric version (no suffix): the desktop AppStream release and the .deb need it.
NEW_VERSION_NUMERIC="$(version_major_minor).${NEW_VERSION_CODE}"
echo "New version: $NEW_VERSION_NAME (code: $NEW_VERSION_CODE)"

# Changelog filenames use the versionCode (fastlane/F-Droid convention), e.g. 1099.txt.
CURRENT_CHANGELOG="$CHANGELOG_DIR/${CURRENT_VERSION_CODE}.txt"
NEW_CHANGELOG="$CHANGELOG_DIR/${NEW_VERSION_CODE}.txt"
if [[ ! -f "$CURRENT_CHANGELOG" ]]; then
    echo -e "${RED}Error: Current changelog not found at $CURRENT_CHANGELOG${NC}"
    exit 1
fi

# The previous bump: the last commit that changed versionCode (looked up before this bump changes it).
PREVIOUS_BUMP=$(git log -1 --format=%H -G'^versionCode=' -- "$VERSION_FILE_REL")

version_set_code "$NEW_VERSION_CODE"
sed -i -E "s|<release version=\"[^\"]*\" date=\"[^\"]*\"|<release version=\"$NEW_VERSION_NUMERIC\" date=\"$(date +%F)\"|" "$METAINFO_REL"
grep -q "<release version=\"$NEW_VERSION_NUMERIC\"" "$METAINFO_REL" || { echo -e "${RED}Error: release update failed in $METAINFO_REL${NC}"; git checkout -- "$VERSION_FILE_REL" "$METAINFO_REL"; exit 1; }

# The footer is the stable boilerplate carried forward each release; the summary is prepended above it. It starts at
# the first line introducing the major.minor version: a line that is exactly "<major.minor>" or one beginning with
# "AndBible <major.minor>".
MAJOR_MINOR_RE=$(version_major_minor | sed 's/\./\\./g')
CHANGELOG_FOOTER=$(sed -n "/^\(AndBible \)\?${MAJOR_MINOR_RE}\([^0-9]\|$\)/,\$p" "$CURRENT_CHANGELOG")
if [[ -z "$CHANGELOG_FOOTER" ]]; then
    echo -e "${YELLOW}Warning: Could not extract changelog footer from $CURRENT_CHANGELOG; using it whole.${NC}"
    CHANGELOG_FOOTER=$(cat "$CURRENT_CHANGELOG")
fi

GENERATED_SUMMARY=""
if [[ -n "$PREVIOUS_BUMP" ]]; then
    GIT_LOG=$(git log "$PREVIOUS_BUMP"..HEAD --no-merges --format="%h %s%w(0,4,4)%+b")
    if [[ -n "$GIT_LOG" ]] && command -v "$CLAUDE" >/dev/null 2>&1; then
        echo "Generating release notes from git history (${PREVIOUS_BUMP:0:10}..HEAD)..."
        GENERATED_SUMMARY=$(echo "$GIT_LOG" | "$CLAUDE" -p --model haiku --tools "" -- \
            "Generate a changelog summary from these git commits for AndBible Bible study app.
Output ONLY a bulleted list (- item) of user-facing changes.
Group related commits into single items. Skip version increment commits, dependency bumps, CI/docs-only changes,
submodule bumps, review fixes, test-only changes and CLAUDE.md/README/skill changes.
If a commit references a GitHub issue (#NNN), include it in parentheses at the end of the item.
If there is no issue number, include the short commit hash instead (e.g. (abc1234)).
Keep items concise (one line each). Write in English.
Order: new features first, then improvements, then bug fixes." 2>/dev/null) || true
    fi
fi

if [[ -n "$GENERATED_SUMMARY" ]]; then
    printf '%s\n\n%s\n' "$GENERATED_SUMMARY" "$CHANGELOG_FOOTER" > "$NEW_CHANGELOG"
else
    echo -e "${YELLOW}Warning: Could not generate release notes (claude not available or no commits); copying the previous changelog.${NC}"
    cp "$CURRENT_CHANGELOG" "$NEW_CHANGELOG"
fi

echo ""
echo -e "${YELLOW}New changelog ($NEW_CHANGELOG):${NC}"
echo "=========================="
cat "$NEW_CHANGELOG"
echo "=========================="

revert() {
    git checkout -- "$VERSION_FILE_REL" "$METAINFO_REL"
    rm -f "$NEW_CHANGELOG"
}
if [[ "$YES" != true ]]; then
    echo -e "${YELLOW}Proceed with this changelog? (y=yes / e=edit / n=abort)${NC}"
    read -r response
    if [[ "$response" =~ ^[Ee]$ ]]; then
        ${EDITOR:-nano} "$NEW_CHANGELOG"
        cat "$NEW_CHANGELOG"
        echo -e "${YELLOW}Proceed with this changelog? (y/n)${NC}"
        read -r response
    fi
    if [[ ! "$response" =~ ^[Yy]$ ]]; then
        echo "Aborted. Reverting changes..."
        revert
        exit 1
    fi
fi

git add "$VERSION_FILE_REL" "$METAINFO_REL" "$NEW_CHANGELOG"
git commit -q -m "Increment version to $NEW_VERSION_NAME"
echo -e "${GREEN}✓ Version bumped to $NEW_VERSION_NAME, commit $(git rev-parse --short=10 HEAD)${NC}"
echo "Next: make release-internal (Play internal + TestFlight). After testing that build, on the host:"
echo "  make github-release-beta|github-release|github-release-test COMMIT=<the commit the internal release built>"
