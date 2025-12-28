#!/bin/bash
# Build Accrescent APK set with GPG-encrypted signing credentials
#
# Usage: ./scripts/build-accrescent.sh [variant]
#   variant: Build variant (default: standardAccrescentRelease)
#
# This script:
# 1. Decrypts accrescent-signing.env.gpg (asks for GPG passphrase)
# 2. Loads environment variables from it
# 3. Runs Gradle buildApks task
# 4. Clears sensitive data from memory

set -e  # Exit on error

# Get script directory
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$PROJECT_ROOT"

# Configuration
ENCRYPTED_FILE="accrescent-signing.env.gpg"
VARIANT="${1:-standardAccrescentRelease}"
GRADLE_TASK="buildApks${VARIANT^}"  # Capitalize first letter

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo -e "${GREEN}=== Accrescent APK Set Builder ===${NC}"
echo ""

# Check if encrypted file exists
if [ ! -f "$ENCRYPTED_FILE" ]; then
    echo -e "${RED}Error: $ENCRYPTED_FILE not found${NC}"
    echo ""
    echo "To create it:"
    echo "  1. Create accrescent-signing.env with your credentials (see accrescent-signing.env.example)"
    echo "  2. Encrypt it: gpg -c accrescent-signing.env"
    echo "  3. Delete the plaintext file: rm accrescent-signing.env"
    echo "  4. Add accrescent-signing.env to .gitignore"
    exit 1
fi

echo -e "${YELLOW}Decrypting signing credentials...${NC}"
echo "Enter GPG passphrase when prompted:"

# Decrypt and source environment variables
# Use a temporary file descriptor to avoid writing to disk
if ! eval "$(gpg --quiet --decrypt "$ENCRYPTED_FILE" 2>/dev/null)"; then
    echo -e "${RED}Error: Failed to decrypt $ENCRYPTED_FILE${NC}"
    echo "Check your GPG passphrase and try again"
    exit 1
fi

echo -e "${GREEN}✓ Credentials loaded${NC}"
echo ""

# Verify required environment variables are set
if [ -z "$ACCRESCENT_STORE_PASSWORD" ] || [ -z "$ACCRESCENT_KEY_PASSWORD" ]; then
    echo -e "${RED}Error: Environment variables not properly set${NC}"
    echo "Check that your accrescent-signing.env.gpg contains:"
    echo "  export ACCRESCENT_STORE_PASSWORD=\"...\""
    echo "  export ACCRESCENT_KEY_PASSWORD=\"...\""
    exit 1
fi

echo -e "${GREEN}Building APK set for variant: ${VARIANT}${NC}"
echo "Task: $GRADLE_TASK"
echo ""

# Clean up any existing apks file from previous builds
SOURCE_APKS="app/build/outputs/apkset/${VARIANT}/app-${VARIANT}.apks"
if [ -f "$SOURCE_APKS" ]; then
    echo "Removing existing APK set file: $SOURCE_APKS"
    rm -f "$SOURCE_APKS"
fi
echo ""

# Run Gradle build
if ./gradlew "$GRADLE_TASK" --no-daemon; then
    echo ""
    echo -e "${GREEN}✓ Build successful!${NC}"
    echo ""

    # Copy APK set to release directory
    SOURCE_APKS="app/build/outputs/apkset/${VARIANT}/app-${VARIANT}.apks"

    # Determine release directory based on variant (standardAccrescent -> app/standardAccrescent/release)
    if [[ "$VARIANT" == standard* ]]; then
        RELEASE_DIR="app/standardAccrescent/release"
    elif [[ "$VARIANT" == discrete* ]]; then
        RELEASE_DIR="app/discreteAccrescent/release"
    else
        RELEASE_DIR="app/accrescent/release"
    fi

    TARGET_APKS="${RELEASE_DIR}/app-${VARIANT}.apks"

    echo -e "${GREEN}Copying APK set to release directory...${NC}"
    mkdir -p "$RELEASE_DIR"
    cp "$SOURCE_APKS" "$TARGET_APKS"

    # Get file size
    SIZE=$(du -h "$TARGET_APKS" | cut -f1)

    echo -e "${GREEN}✓ APK set ready for distribution${NC}"
    echo ""
    echo "Location: ${TARGET_APKS}"
    echo "Size: ${SIZE}"
    echo ""
    echo "Next steps:"
    echo "  1. Test: bundletool install-apks --apks=${TARGET_APKS}"
    echo "  2. Upload to Accrescent: https://accrescent.app/developers"
else
    echo ""
    echo -e "${RED}✗ Build failed${NC}"
    exit 1
fi

# Clear sensitive environment variables
unset ACCRESCENT_STORE_PASSWORD
unset ACCRESCENT_KEY_PASSWORD

echo ""
echo -e "${GREEN}Done! Credentials cleared from memory.${NC}"
