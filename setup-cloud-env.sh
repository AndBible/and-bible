#!/bin/bash
# AndBible Cloud Development Environment Setup Script
# For use as a Claude Code SessionStart hook
#
# What this script sets up:
# 1. Java 17 (required by project toolchain)
# 2. Node.js 20 (required by bibleview-js)
# 3. Android SDK command-line tools + platform SDK 36
# 4. npm dependencies for bibleview-js
# 5. local.properties for Android SDK path
# 6. Gradle wrapper download
#
# Usage: bash ai-local/setup-cloud-env.sh
# The script is idempotent - safe to run multiple times.

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ANDROID_SDK_ROOT_DIR="/opt/android-sdk"
MARKER_DIR="$REPO_ROOT/.setup-markers"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

info()  { echo -e "${GREEN}[SETUP]${NC} $*"; }
warn()  { echo -e "${YELLOW}[WARN]${NC} $*"; }
error() { echo -e "${RED}[ERROR]${NC} $*"; }

mkdir -p "$MARKER_DIR"

# ============================================================
# 1. Java 17 (project requires jvmToolchain(17))
# ============================================================
setup_java17() {
    if java -version 2>&1 | grep -q '"17\.'; then
        info "Java 17 already active"
        return 0
    fi

    if [ -d "/usr/lib/jvm/java-17-openjdk-amd64" ]; then
        info "Java 17 already installed, setting as default"
    else
        info "Installing OpenJDK 17..."
        apt-get update -qq
        DEBIAN_FRONTEND=noninteractive apt-get install -y -qq openjdk-17-jdk-headless > /dev/null 2>&1
    fi

    # Set Java 17 as default
    update-alternatives --set java /usr/lib/jvm/java-17-openjdk-amd64/bin/java 2>/dev/null || true
    update-alternatives --set javac /usr/lib/jvm/java-17-openjdk-amd64/bin/javac 2>/dev/null || true

    export JAVA_HOME="/usr/lib/jvm/java-17-openjdk-amd64"
    info "Java 17 configured: $JAVA_HOME"
}

# ============================================================
# 2. Node.js 20 (project requires Node 20.x)
# ============================================================
setup_node20() {
    local current_node_version
    current_node_version="$(node --version 2>/dev/null || echo 'none')"

    if [[ "$current_node_version" == v20.* ]]; then
        info "Node.js 20 already active: $current_node_version"
        return 0
    fi

    if [ -x "/opt/node20/bin/node" ]; then
        info "Node.js 20 found at /opt/node20, configuring PATH"
        export PATH="/opt/node20/bin:$PATH"
    elif command -v nvm &>/dev/null || [ -s "/opt/nvm/nvm.sh" ]; then
        info "Installing Node.js 20 via nvm..."
        export NVM_DIR="/opt/nvm"
        source "$NVM_DIR/nvm.sh"
        nvm install 20 --default
        nvm use 20
    else
        warn "Cannot find Node.js 20. Please install it manually."
        return 1
    fi

    info "Node.js active: $(node --version), npm: $(npm --version)"
}

# ============================================================
# 3. Android SDK
# ============================================================
setup_android_sdk() {
    if [ -f "$MARKER_DIR/.android-sdk-done" ] && [ -d "$ANDROID_SDK_ROOT_DIR/platforms/android-36" ]; then
        info "Android SDK already set up"
        export ANDROID_SDK_ROOT="$ANDROID_SDK_ROOT_DIR"
        export ANDROID_HOME="$ANDROID_SDK_ROOT_DIR"
        return 0
    fi

    info "Setting up Android SDK..."
    mkdir -p "$ANDROID_SDK_ROOT_DIR"

    # Download command-line tools
    local cmdline_zip="/tmp/android-cmdline-tools.zip"
    if [ ! -f "$ANDROID_SDK_ROOT_DIR/cmdline-tools/latest/bin/sdkmanager" ]; then
        info "Downloading Android command-line tools..."
        # Try multiple download sources
        local download_success=false
        for url in \
            "https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip" \
            "https://dl.google.com/android/repository/commandlinetools-linux-10406996_latest.zip"; do
            if curl -fsSL -o "$cmdline_zip" "$url" 2>/dev/null; then
                # Verify it's actually a zip file
                if file "$cmdline_zip" | grep -q "Zip archive"; then
                    download_success=true
                    break
                fi
            fi
        done

        if ! $download_success; then
            warn "Cannot download Android SDK (dl.google.com may be blocked by egress proxy)"
            warn "Android builds will NOT work. Vue.js tests will still work."
            warn "To fix: add dl.google.com to the egress proxy allowlist"
            return 1
        fi

        mkdir -p "$ANDROID_SDK_ROOT_DIR/cmdline-tools"
        unzip -q "$cmdline_zip" -d "$ANDROID_SDK_ROOT_DIR/cmdline-tools"
        mv "$ANDROID_SDK_ROOT_DIR/cmdline-tools/cmdline-tools" "$ANDROID_SDK_ROOT_DIR/cmdline-tools/latest" 2>/dev/null || true
        rm -f "$cmdline_zip"
    fi

    export ANDROID_SDK_ROOT="$ANDROID_SDK_ROOT_DIR"
    export ANDROID_HOME="$ANDROID_SDK_ROOT_DIR"
    local sdkmanager="$ANDROID_SDK_ROOT_DIR/cmdline-tools/latest/bin/sdkmanager"

    # Accept licenses
    yes | "$sdkmanager" --licenses > /dev/null 2>&1 || true

    # Install required SDK components
    info "Installing Android SDK components (this may take a few minutes)..."
    "$sdkmanager" \
        "platform-tools" \
        "platforms;android-36" \
        "build-tools;36.0.0" \
        > /dev/null 2>&1

    touch "$MARKER_DIR/.android-sdk-done"
    info "Android SDK installed at $ANDROID_SDK_ROOT_DIR"
}

# ============================================================
# 4. npm install for bibleview-js
# ============================================================
setup_npm_deps() {
    local bibleview_dir="$REPO_ROOT/app/bibleview-js"

    if [ -f "$bibleview_dir/node_modules/.package-lock.json" ]; then
        info "npm dependencies already installed"
        return 0
    fi

    info "Installing npm dependencies for bibleview-js..."
    cd "$bibleview_dir"
    npm install --prefer-offline 2>&1 | tail -3
    cd "$REPO_ROOT"
    info "npm dependencies installed"
}

# ============================================================
# 5. local.properties
# ============================================================
setup_local_properties() {
    local props_file="$REPO_ROOT/local.properties"

    if [ -f "$props_file" ] && grep -q "sdk.dir" "$props_file"; then
        info "local.properties already configured"
        return 0
    fi

    info "Creating local.properties..."
    cat > "$props_file" << EOF
# Auto-generated by setup-cloud-env.sh
sdk.dir=$ANDROID_SDK_ROOT_DIR
EOF
    info "local.properties created"
}

# ============================================================
# 6. Gradle wrapper
# ============================================================
setup_gradle_wrapper() {
    if [ -f "$REPO_ROOT/gradlew" ] && [ -x "$REPO_ROOT/gradlew" ]; then
        info "Gradle wrapper exists"
        return 0
    fi

    chmod +x "$REPO_ROOT/gradlew"
    info "Gradle wrapper made executable"
}

# ============================================================
# 7. Write environment activation script
# ============================================================
write_env_script() {
    local env_script="$REPO_ROOT/ai-local/activate-env.sh"
    cat > "$env_script" << 'ENVEOF'
#!/bin/bash
# Source this file to activate the AndBible development environment
# Usage: source ai-local/activate-env.sh

# Java 17
if [ -d "/usr/lib/jvm/java-17-openjdk-amd64" ]; then
    export JAVA_HOME="/usr/lib/jvm/java-17-openjdk-amd64"
    export PATH="$JAVA_HOME/bin:$PATH"
fi

# Node.js 20
if [ -d "/opt/node20/bin" ]; then
    export PATH="/opt/node20/bin:$PATH"
fi

# Android SDK
if [ -d "/opt/android-sdk" ]; then
    export ANDROID_SDK_ROOT="/opt/android-sdk"
    export ANDROID_HOME="/opt/android-sdk"
    export PATH="$ANDROID_SDK_ROOT/cmdline-tools/latest/bin:$ANDROID_SDK_ROOT/platform-tools:$PATH"
fi

echo "AndBible dev environment activated"
echo "  Java:    $(java -version 2>&1 | head -1)"
echo "  Node:    $(node --version 2>/dev/null || echo 'not found')"
echo "  npm:     $(npm --version 2>/dev/null || echo 'not found')"
echo "  Android: ${ANDROID_SDK_ROOT:-not set}"
ENVEOF
    chmod +x "$env_script"
    info "Environment activation script written to $env_script"
}

# ============================================================
# Main
# ============================================================
main() {
    info "=== AndBible Cloud Dev Environment Setup ==="
    info "Repo root: $REPO_ROOT"
    echo

    setup_java17
    setup_node20
    setup_android_sdk || true  # Don't fail if Android SDK can't be downloaded
    setup_npm_deps
    setup_local_properties
    setup_gradle_wrapper
    write_env_script

    echo
    info "=== Setup Complete ==="
    echo
    info "Quick validation commands:"
    info "  Vue.js tests:  cd app/bibleview-js && npm run test:ci"
    info "  Vue.js lint:   cd app/bibleview-js && npm run lint"
    info "  Type check:    cd app/bibleview-js && npm run type-check"
    if [ -d "$ANDROID_SDK_ROOT_DIR/platforms/android-36" ]; then
        info "  Android build: ./gradlew assembleStandardGithubDebug"
        info "  Android test:  ./gradlew testStandardGoogleplayDebugUnitTest"
    else
        warn "  Android builds NOT available (SDK missing)"
    fi
    echo
    info "To activate env in a new shell: source ai-local/activate-env.sh"
}

main "$@"
