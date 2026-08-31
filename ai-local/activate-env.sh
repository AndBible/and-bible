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
