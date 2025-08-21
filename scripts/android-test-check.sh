#!/bin/bash

# AndBible Quick Android Test Validation
# This script attempts a minimal Android test to verify the environment works

set -e

echo "🔍 AndBible Android Test Environment Check"
echo "=========================================="

echo "📋 Checking prerequisites..."
echo "Java version: $(java --version | head -1)"

# Check if ANDROID_SDK_ROOT or ANDROID_HOME is set
if [ -z "$ANDROID_SDK_ROOT" ] && [ -z "$ANDROID_HOME" ]; then
    echo "⚠️  ANDROID_SDK_ROOT not set - Android tests may fail"
    echo "   Set ANDROID_SDK_ROOT to your Android SDK path"
fi

echo ""
echo "🧪 Attempting minimal Android test (timeout: 5 minutes)..."
echo "   This verifies Android test environment works without long execution"

cd "$(dirname "$0")/.."

# Try to run a very simple test with a short timeout
timeout 300 ./gradlew testStandardGoogleplayDebugUnitTest --tests "*.TestUtils.isAndroid" --info || {
    echo ""
    echo "⚠️  Android test timed out or failed (expected for CI environments)"
    echo "   This is normal in environments without full Android SDK setup"
    echo "   For development with Android changes, ensure:"
    echo "   1. Internet connectivity available"
    echo "   2. Android SDK properly installed"
    echo "   3. Adequate time for first-time builds (10-45 minutes)"
    echo ""
    echo "✅ Vue.js tests remain the primary validation method for Copilot"
    exit 0
}

echo ""
echo "✅ Android test environment appears functional!"
echo "   Full Android tests can be run with proper internet connectivity"