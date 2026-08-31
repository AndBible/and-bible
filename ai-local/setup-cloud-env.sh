#!/bin/bash
# AndBible Cloud Development Environment Setup Script
# For use as a Claude Code SessionStart hook
#
# What this script sets up:
# 1. Java 17 (required by project toolchain)
# 2. Node.js 20 (required by bibleview-js)
# 3. Android SDK components (platform-36, build-tools-36, platform-tools)
#    NOTE: sdkmanager cannot authenticate with the egress proxy, so components
#    are downloaded manually via curl (which uses the proxy correctly).
# 4. Robolectric android-all-instrumented jar (pre-downloaded to ~/.m2)
#    to avoid runtime download failures during Android unit tests.
# 5. npm dependencies for bibleview-js
# 6. local.properties for Android SDK path
# 7. JAVA_TOOL_OPTIONS fix: removes *.google.com from nonProxyHosts so
#    that Gradle can reach dl.google.com through the authenticated proxy.
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
#
# NOTE: sdkmanager uses java.net.HttpURLConnection for HTTPS tunneling,
# which cannot authenticate with the egress proxy (returns 407).
# We download SDK components manually via curl, which correctly uses
# the system proxy configuration.
# ============================================================
setup_android_sdk() {
    if [ -f "$MARKER_DIR/.android-sdk-done" ] && \
       [ -d "$ANDROID_SDK_ROOT_DIR/platforms/android-36" ] && \
       [ -d "$ANDROID_SDK_ROOT_DIR/build-tools/36.0.0" ]; then
        info "Android SDK already set up"
        export ANDROID_SDK_ROOT="$ANDROID_SDK_ROOT_DIR"
        export ANDROID_HOME="$ANDROID_SDK_ROOT_DIR"
        return 0
    fi

    info "Setting up Android SDK..."
    mkdir -p "$ANDROID_SDK_ROOT_DIR"

    # Download command-line tools (for sdkmanager/avdmanager if needed)
    if [ ! -f "$ANDROID_SDK_ROOT_DIR/cmdline-tools/latest/bin/sdkmanager" ]; then
        info "Downloading Android command-line tools..."
        local cmdline_zip="/tmp/android-cmdline-tools.zip"
        local download_success=false
        for url in \
            "https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip" \
            "https://dl.google.com/android/repository/commandlinetools-linux-10406996_latest.zip"; do
            if curl -fsSL -o "$cmdline_zip" "$url" 2>/dev/null; then
                if file "$cmdline_zip" | grep -q "Zip archive"; then
                    download_success=true
                    break
                fi
            fi
        done

        if $download_success; then
            mkdir -p "$ANDROID_SDK_ROOT_DIR/cmdline-tools"
            unzip -q "$cmdline_zip" -d "$ANDROID_SDK_ROOT_DIR/cmdline-tools"
            mv "$ANDROID_SDK_ROOT_DIR/cmdline-tools/cmdline-tools" \
               "$ANDROID_SDK_ROOT_DIR/cmdline-tools/latest" 2>/dev/null || true
            rm -f "$cmdline_zip"
            info "Android command-line tools installed"
        else
            warn "Cannot download Android command-line tools"
        fi
    fi

    # Install SDK components via direct curl download.
    # sdkmanager cannot authenticate with the egress proxy (407 error on HTTPS CONNECT),
    # but curl works correctly via the proxy.
    local sdk_base="https://dl.google.com/android/repository"

    if [ ! -d "$ANDROID_SDK_ROOT_DIR/platform-tools" ]; then
        info "Downloading platform-tools..."
        local pt_zip="/tmp/platform-tools.zip"
        if curl -fsSL -o "$pt_zip" "$sdk_base/platform-tools_r37.0.0-linux.zip" 2>/dev/null; then
            unzip -q "$pt_zip" -d "$ANDROID_SDK_ROOT_DIR/"
            rm -f "$pt_zip"
            info "platform-tools installed"
        else
            warn "Failed to download platform-tools"
        fi
    fi

    if [ ! -d "$ANDROID_SDK_ROOT_DIR/platforms/android-36" ]; then
        info "Downloading Android platform-36..."
        local platform_zip="/tmp/android-platform-36.zip"
        if curl -fsSL -o "$platform_zip" "$sdk_base/platform-36_r02.zip" 2>/dev/null; then
            local tmp_dir="/tmp/android-platform-36-extract"
            mkdir -p "$tmp_dir"
            unzip -q "$platform_zip" -d "$tmp_dir"
            mkdir -p "$ANDROID_SDK_ROOT_DIR/platforms"
            mv "$tmp_dir/android-36" "$ANDROID_SDK_ROOT_DIR/platforms/android-36"
            rm -rf "$platform_zip" "$tmp_dir"
            info "Android platform-36 installed"
        else
            warn "Failed to download Android platform-36"
        fi
    fi

    if [ ! -d "$ANDROID_SDK_ROOT_DIR/build-tools/36.0.0" ]; then
        info "Downloading build-tools 36.0.0..."
        local bt_zip="/tmp/android-build-tools-36.zip"
        if curl -fsSL -o "$bt_zip" "$sdk_base/build-tools_r36_linux.zip" 2>/dev/null; then
            local tmp_dir="/tmp/android-bt36-extract"
            mkdir -p "$tmp_dir"
            unzip -q "$bt_zip" -d "$tmp_dir"
            mkdir -p "$ANDROID_SDK_ROOT_DIR/build-tools/36.0.0"
            # The zip extracts to android-16/ regardless of version
            mv "$tmp_dir"/android-*/* "$ANDROID_SDK_ROOT_DIR/build-tools/36.0.0/"
            rm -rf "$bt_zip" "$tmp_dir"
            info "build-tools 36.0.0 installed"
        else
            warn "Failed to download build-tools 36.0.0"
        fi
    fi

    # Accept licenses if sdkmanager is available
    if [ -f "$ANDROID_SDK_ROOT_DIR/cmdline-tools/latest/bin/sdkmanager" ]; then
        yes | JAVA_TOOL_OPTIONS="" "$ANDROID_SDK_ROOT_DIR/cmdline-tools/latest/bin/sdkmanager" \
            --licenses > /dev/null 2>&1 || true
    fi

    export ANDROID_SDK_ROOT="$ANDROID_SDK_ROOT_DIR"
    export ANDROID_HOME="$ANDROID_SDK_ROOT_DIR"

    if [ -d "$ANDROID_SDK_ROOT_DIR/platforms/android-36" ] && \
       [ -d "$ANDROID_SDK_ROOT_DIR/build-tools/36.0.0" ]; then
        touch "$MARKER_DIR/.android-sdk-done"
        info "Android SDK installed at $ANDROID_SDK_ROOT_DIR"
    else
        warn "Android SDK partially installed (some components missing)"
    fi
}

# ============================================================
# 4. Robolectric android-all-instrumented pre-download
#
# Robolectric downloads instrumented Android SDK jars at test runtime
# using its own Maven resolver. In this environment, that resolver also
# cannot authenticate with the proxy, causing test failures.
# We pre-download the required jar to ~/.m2/repository so Robolectric
# finds it locally without network access.
#
# The version matches robolectric:4.9 + sdk=33 (TEST_SDK in TestBibleApplication.kt).
# ============================================================
setup_robolectric_artifacts() {
    local ROBOLECTRIC_VERSION="13-robolectric-9030017-i4"
    local M2_DIR="/root/.m2/repository/org/robolectric/android-all-instrumented/$ROBOLECTRIC_VERSION"
    local JAR="$M2_DIR/android-all-instrumented-$ROBOLECTRIC_VERSION.jar"

    if [ -f "$JAR" ]; then
        info "Robolectric android-all-instrumented already cached"
        return 0
    fi

    info "Pre-downloading Robolectric android-all-instrumented (~162 MB)..."
    mkdir -p "$M2_DIR"
    local BASE_URL="https://repo1.maven.org/maven2/org/robolectric/android-all-instrumented/$ROBOLECTRIC_VERSION"

    local download_success=true
    for ext in ".pom" ".pom.sha1" ".jar" ".jar.sha1"; do
        local filename="android-all-instrumented-${ROBOLECTRIC_VERSION}${ext}"
        if ! curl -fsSL -o "$M2_DIR/$filename" "$BASE_URL/$filename" 2>/dev/null; then
            warn "Failed to download $filename"
            download_success=false
        fi
    done

    if $download_success; then
        info "Robolectric artifacts cached at $M2_DIR"
    else
        warn "Some Robolectric artifacts failed to download — tests requiring Android SDK simulation may fail"
    fi
}

# ============================================================
# 5. npm install for bibleview-js
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
# 6. local.properties
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
# 7. Gradle wrapper
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
# 8. Write environment activation script
#
# Also fixes JAVA_TOOL_OPTIONS: the cloud environment sets *.google.com
# in nonProxyHosts, which causes Java to bypass the proxy for dl.google.com.
# Since there is no direct internet access (only via proxy), this breaks
# Gradle downloads from dl.google.com/dl/android/maven2. We remove
# *.google.com and *.googleapis.com from nonProxyHosts so that Java
# uses the authenticated proxy for those hosts too.
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

# Fix JAVA_TOOL_OPTIONS proxy settings.
#
# The cloud environment sets *.google.com and *.googleapis.com in
# nonProxyHosts, causing Java to bypass the proxy for dl.google.com.
# Without the proxy, name resolution fails (no direct internet access).
# Remove these entries so Gradle can reach dl.google.com via the proxy.
if [ -n "${JAVA_TOOL_OPTIONS:-}" ] && echo "$JAVA_TOOL_OPTIONS" | grep -q 'nonProxyHosts'; then
    JAVA_TOOL_OPTIONS=$(python3 -c "
import sys, re
jto = sys.stdin.read().strip()
jto = re.sub(r'-Dhttp\.nonProxyHosts=\S+',
             '-Dhttp.nonProxyHosts=localhost|127.0.0.1|169.254.169.254|metadata.google.internal|*.svc.cluster.local|*.local',
             jto)
print(jto)
" <<< "$JAVA_TOOL_OPTIONS")
    export JAVA_TOOL_OPTIONS
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
    setup_robolectric_artifacts || true  # Don't fail if Robolectric artifacts can't be downloaded
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
    info "IMPORTANT: source activate-env.sh before running Android tests to fix proxy settings"
}

main "$@"
