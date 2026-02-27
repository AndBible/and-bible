#!/bin/bash

# Version increment script for AndBible
# This script increments version, creates changelog, commits, tags, and pushes to GitHub
# Usage: ./scripts/increment-version.sh [--build]
#   --build: Create a test build tag (build-X) instead of production tag (production-X)

set -e  # Exit on any error

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Parse arguments
BUILD_MODE=false
if [[ "$1" == "--build" ]]; then
    BUILD_MODE=true
fi

# Configuration
ANDROID_MANIFEST_PATH="app/src/main/AndroidManifest.xml"
CHANGELOG_DIR="fastlane/metadata/android/en-US/changelogs"
REPO_ROOT=$(git rev-parse --show-toplevel)

if [[ "$BUILD_MODE" == true ]]; then
    echo -e "${GREEN}AndBible Test Build Script${NC}"
    echo "=========================="
else
    echo -e "${GREEN}AndBible Version Increment Script${NC}"
    echo "=================================="
fi

# Check if we're in the right directory
if [[ ! -f "$ANDROID_MANIFEST_PATH" ]]; then
    echo -e "${RED}Error: AndroidManifest.xml not found at $ANDROID_MANIFEST_PATH${NC}"
    echo "Please run this script from the repository root directory."
    exit 1
fi

# Check if we have uncommitted changes
if [[ -n $(git status --porcelain) ]]; then
    echo -e "${RED}Error: You have uncommitted changes. Please commit or stash them first.${NC}"
    git status --short
    exit 1
fi

# Extract current version information
echo "Reading current version from AndroidManifest.xml..."
CURRENT_VERSION_CODE=$(grep -o 'android:versionCode="[0-9]*"' "$ANDROID_MANIFEST_PATH" | grep -o '[0-9]*')
CURRENT_VERSION_NAME=$(grep -o 'android:versionName="[^"]*"' "$ANDROID_MANIFEST_PATH" | grep -o '[^"]*' | tail -1)

if [[ -z "$CURRENT_VERSION_CODE" ]] || [[ -z "$CURRENT_VERSION_NAME" ]]; then
    echo -e "${RED}Error: Could not extract version information from AndroidManifest.xml${NC}"
    exit 1
fi

echo "Current version: $CURRENT_VERSION_NAME (code: $CURRENT_VERSION_CODE)"

# Calculate new version
NEW_VERSION_CODE=$((CURRENT_VERSION_CODE + 1))
# Extract major.minor from current version name and append new version code
VERSION_PREFIX=$(echo "$CURRENT_VERSION_NAME" | sed 's/\.[0-9]*$//')
NEW_VERSION_NAME="${VERSION_PREFIX}.${NEW_VERSION_CODE}"

echo "New version: $NEW_VERSION_NAME (code: $NEW_VERSION_CODE)"

# Check if changelog exists for current version
CURRENT_CHANGELOG="$CHANGELOG_DIR/${CURRENT_VERSION_NAME}.txt"
NEW_CHANGELOG="$CHANGELOG_DIR/${NEW_VERSION_NAME}.txt"

if [[ ! -f "$CURRENT_CHANGELOG" ]]; then
    echo -e "${RED}Error: Current changelog not found at $CURRENT_CHANGELOG${NC}"
    exit 1
fi

# Update AndroidManifest.xml
echo "Updating AndroidManifest.xml..."
sed -i.bak "s/android:versionCode=\"$CURRENT_VERSION_CODE\"/android:versionCode=\"$NEW_VERSION_CODE\"/" "$ANDROID_MANIFEST_PATH"
sed -i.bak2 "s/android:versionName=\"$CURRENT_VERSION_NAME\"/android:versionName=\"$NEW_VERSION_NAME\"/" "$ANDROID_MANIFEST_PATH"

# Remove backup files
rm -f "${ANDROID_MANIFEST_PATH}.bak" "${ANDROID_MANIFEST_PATH}.bak2"

# Verify the changes
echo "Verifying AndroidManifest.xml changes..."
NEW_VERSION_CODE_CHECK=$(grep -o 'android:versionCode="[0-9]*"' "$ANDROID_MANIFEST_PATH" | grep -o '[0-9]*')
NEW_VERSION_NAME_CHECK=$(grep -o 'android:versionName="[^"]*"' "$ANDROID_MANIFEST_PATH" | grep -o '[^"]*' | tail -1)

if [[ "$NEW_VERSION_CODE_CHECK" != "$NEW_VERSION_CODE" ]] || [[ "$NEW_VERSION_NAME_CHECK" != "$NEW_VERSION_NAME" ]]; then
    echo -e "${RED}Error: Version update failed in AndroidManifest.xml${NC}"
    echo "Expected: $NEW_VERSION_NAME (code: $NEW_VERSION_CODE)"
    echo "Found: $NEW_VERSION_NAME_CHECK (code: $NEW_VERSION_CODE_CHECK)"
    exit 1
fi

echo -e "${GREEN}✓ AndroidManifest.xml updated successfully${NC}"

# Generate changelog with auto-summarized release notes
# Find the previous tag to diff against:
#   production mode: always the latest production-* tag
#   build mode: latest build-* tag, falling back to latest production-* tag
if [[ "$BUILD_MODE" == true ]]; then
    PREVIOUS_TAG=$(git describe --tags --match 'production-*' --match 'build-*' --abbrev=0 HEAD 2>/dev/null || echo "")
else
    PREVIOUS_TAG=$(git describe --tags --match 'production-*' --abbrev=0 HEAD 2>/dev/null || echo "")
fi
if [[ -n "$PREVIOUS_TAG" ]]; then
    echo "Previous tag: $PREVIOUS_TAG"
else
    echo -e "${YELLOW}Warning: No previous tag found.${NC}"
fi

# Extract the fixed footer from the current changelog (starts at the line matching major.minor version)
MAJOR_MINOR=$(echo "$CURRENT_VERSION_NAME" | sed 's/\.[0-9]*$//')
CHANGELOG_FOOTER=$(sed -n "/^${MAJOR_MINOR}$/,\$p" "$CURRENT_CHANGELOG")

if [[ -z "$CHANGELOG_FOOTER" ]]; then
    echo -e "${YELLOW}Warning: Could not extract changelog footer from $CURRENT_CHANGELOG${NC}"
    echo "Using full previous changelog as footer."
    CHANGELOG_FOOTER=$(cat "$CURRENT_CHANGELOG")
fi

# Try to auto-generate release notes summary from git history
GENERATED_SUMMARY=""
if [[ -n "$PREVIOUS_TAG" ]] && git rev-parse "$PREVIOUS_TAG" >/dev/null 2>&1; then
    GIT_LOG=$(git log "$PREVIOUS_TAG"..HEAD --oneline --no-merges)
    if [[ -n "$GIT_LOG" ]]; then
        echo "Generating release notes from git history (${PREVIOUS_TAG}..HEAD)..."
        if command -v claude >/dev/null 2>&1; then
            GENERATED_SUMMARY=$(echo "$GIT_LOG" | claude -p --model haiku \
                "Generate a changelog summary from these git commits for AndBible Bible study app.
Output ONLY a bulleted list (- item) of user-facing changes.
Group related commits into single items. Skip version increment commits, dependency bumps, CI/docs-only changes, and CLAUDE.md/README changes.
If a commit references a GitHub issue (#NNN), include it in parentheses at the end of the item.
If there is no issue number, include the short commit hash instead (e.g. (abc1234)).
Keep items concise (one line each). Write in English.
Order: new features first, then improvements, then bug fixes." 2>/dev/null) || true
        fi
    fi
fi

if [[ -n "$GENERATED_SUMMARY" ]]; then
    echo -e "${GREEN}✓ Release notes generated from git history${NC}"
    # Compose new changelog: generated summary + blank line + footer
    printf '%s\n\n%s\n' "$GENERATED_SUMMARY" "$CHANGELOG_FOOTER" > "$NEW_CHANGELOG"
else
    echo -e "${YELLOW}Warning: Could not generate release notes (claude not available or no commits found).${NC}"
    echo "Copying previous changelog as fallback."
    cp "$CURRENT_CHANGELOG" "$NEW_CHANGELOG"
fi

# Show the changelog content and let user edit if needed
echo ""
echo -e "${YELLOW}New changelog content:${NC}"
echo "=========================="
cat "$NEW_CHANGELOG"
echo "=========================="
echo ""

echo -e "${YELLOW}Do you want to proceed with this changelog? (y=yes / e=edit / n=abort)${NC}"
read -r response
if [[ "$response" =~ ^[Ee]$ ]]; then
    echo "Opening changelog in editor..."
    ${EDITOR:-nano} "$NEW_CHANGELOG"
    echo ""
    echo -e "${YELLOW}Updated changelog content:${NC}"
    echo "=========================="
    cat "$NEW_CHANGELOG"
    echo "=========================="
    echo ""
    echo -e "${YELLOW}Proceed with this changelog? (y/n)${NC}"
    read -r response
fi
if [[ ! "$response" =~ ^[Yy]$ ]]; then
    echo "Aborted by user. Reverting changes..."
    git checkout -- "$ANDROID_MANIFEST_PATH"
    rm -f "$NEW_CHANGELOG"
    exit 1
fi

# Add files to git
echo "Adding files to git..."
git add "$ANDROID_MANIFEST_PATH" "$NEW_CHANGELOG"

# Commit changes
COMMIT_MESSAGE="Increment version to $NEW_VERSION_NAME"
echo "Creating commit: $COMMIT_MESSAGE"
if [[ "$BUILD_MODE" == true ]]; then
    git commit -m "$COMMIT_MESSAGE"
else
    git commit -S -m "$COMMIT_MESSAGE"
fi

# Create tag
if [[ "$BUILD_MODE" == true ]]; then
    TAG_NAME="build-$NEW_VERSION_CODE"
    TAG_MESSAGE="Test build $NEW_VERSION_NAME"
else
    TAG_NAME="production-$NEW_VERSION_CODE"
    TAG_MESSAGE="Release $NEW_VERSION_NAME"
fi
echo "Creating tag: $TAG_NAME"
if [[ "$BUILD_MODE" == true ]]; then
    git tag -a "$TAG_NAME" -m "$TAG_MESSAGE"
else
    git tag -s "$TAG_NAME" -m "$TAG_MESSAGE"
fi

echo -e "${GREEN}✓ Commit and tag created successfully${NC}"

# Ask if user wants to push
echo ""
echo -e "${YELLOW}Do you want to push the changes and tag to GitHub? (y/n)${NC}"
read -r push_response
if [[ "$push_response" =~ ^[Yy]$ ]]; then
    echo "Pushing changes to GitHub..."
    git push origin
    echo "Pushing tag to GitHub..."
    git push origin "$TAG_NAME"
    echo -e "${GREEN}✓ Changes and tag pushed to GitHub successfully${NC}"

    # Wait for CI workflow to start and approve deployment
    if command -v gh >/dev/null 2>&1; then
        echo ""
        echo "Waiting for CI workflow to start for tag $TAG_NAME..."
        HEAD_SHA=$(git rev-parse HEAD)
        RUN_ID=""
        for i in $(seq 1 30); do
            RUN_ID=$(gh api "repos/{owner}/{repo}/actions/workflows/build-apk.yml/runs?head_sha=$HEAD_SHA&per_page=1" \
                --jq '.workflow_runs[0].id // empty' 2>/dev/null)
            if [[ -n "$RUN_ID" ]]; then
                break
            fi
            sleep 2
        done

        if [[ -n "$RUN_ID" ]]; then
            echo "Found workflow run: $RUN_ID"
            # Wait for it to reach "waiting" (pending approval) state
            for i in $(seq 1 30); do
                PENDING=$(gh api "repos/{owner}/{repo}/actions/runs/$RUN_ID/pending_deployments" --jq '.[].environment.id' 2>/dev/null)
                if [[ -n "$PENDING" ]]; then
                    break
                fi
                sleep 2
            done

            if [[ -n "$PENDING" ]]; then
                echo -e "${YELLOW}Approving deployment for workflow run $RUN_ID...${NC}"
                # Collect all pending environment IDs as -F array params
                APPROVE_ARGS=()
                while IFS= read -r eid; do
                    APPROVE_ARGS+=(-F "environment_ids[]=$eid")
                done < <(gh api "repos/{owner}/{repo}/actions/runs/$RUN_ID/pending_deployments" --jq '.[].environment.id' 2>/dev/null)
                gh api "repos/{owner}/{repo}/actions/runs/$RUN_ID/pending_deployments" \
                    --method POST \
                    "${APPROVE_ARGS[@]}" \
                    -f state=approved \
                    -f comment="Approved via increment-version script" \
                    >/dev/null 2>&1 && \
                    echo -e "${GREEN}✓ Deployment approved${NC}" || \
                    echo -e "${YELLOW}Warning: Could not approve deployment. Approve manually at: https://github.com/AndBible/and-bible/actions/runs/$RUN_ID${NC}"
            else
                echo -e "${YELLOW}Workflow not waiting for approval yet. Check: https://github.com/AndBible/and-bible/actions/runs/$RUN_ID${NC}"
            fi
        else
            echo -e "${YELLOW}Could not find CI workflow run. Check GitHub Actions manually.${NC}"
        fi
    fi
else
    echo -e "${YELLOW}Changes and tag created locally but not pushed.${NC}"
    echo "To push later, run:"
    echo "  git push origin"
    echo "  git push origin $TAG_NAME"
fi

echo ""
echo -e "${GREEN}Version increment completed successfully!${NC}"
echo "Summary:"
echo "- Version updated from $CURRENT_VERSION_NAME to $NEW_VERSION_NAME"
echo "- Changelog generated at $NEW_CHANGELOG"
echo "- Commit created: $COMMIT_MESSAGE"
echo "- Tag created: $TAG_NAME"
