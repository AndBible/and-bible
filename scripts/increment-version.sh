#!/bin/bash

# Version increment script for AndBible
# This script increments version, creates changelog, commits, tags, and pushes to GitHub

set -e  # Exit on any error

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Configuration
ANDROID_MANIFEST_PATH="app/src/main/AndroidManifest.xml"
CHANGELOG_DIR="fastlane/metadata/android/en-US/changelogs"
REPO_ROOT=$(git rev-parse --show-toplevel)

echo -e "${GREEN}AndBible Version Increment Script${NC}"
echo "=================================="

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

# Copy changelog
echo "Copying changelog from $CURRENT_CHANGELOG to $NEW_CHANGELOG..."
cp "$CURRENT_CHANGELOG" "$NEW_CHANGELOG"
echo -e "${GREEN}✓ Changelog copied successfully${NC}"

# Show the changelog content
echo ""
echo -e "${YELLOW}Current changelog content:${NC}"
echo "=========================="
cat "$NEW_CHANGELOG"
echo "=========================="
echo ""

# Ask user if they want to proceed
echo -e "${YELLOW}Do you want to proceed with the current changelog? (y/n)${NC}"
read -r response
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
git commit -S -m "$COMMIT_MESSAGE"

# Create tag
TAG_NAME="production-$NEW_VERSION_CODE"
echo "Creating tag: $TAG_NAME"
git tag -s "$TAG_NAME" -m "Release $NEW_VERSION_NAME"

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
echo "- Changelog copied to $NEW_CHANGELOG"
echo "- Commit created: $COMMIT_MESSAGE"
echo "- Tag created: $TAG_NAME"
