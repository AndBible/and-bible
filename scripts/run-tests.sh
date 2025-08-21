#!/bin/bash

# AndBible Comprehensive Test Runner
# Handles both Vue.js and Android tests with appropriate timeouts and guidance

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(dirname "$SCRIPT_DIR")"

echo "🚀 AndBible Comprehensive Test Runner"
echo "====================================="

show_help() {
    echo "Usage: $0 [OPTIONS]"
    echo ""
    echo "Options:"
    echo "  --vue-only      Run only Vue.js tests (fast, ~20 seconds)"
    echo "  --android-only  Run only Android tests (slow, 10-45 minutes, requires internet)"
    echo "  --quick         Run Vue.js tests + Android environment check"
    echo "  --full          Run both Vue.js and Android tests (long execution)"
    echo "  --help          Show this help message"
    echo ""
    echo "Default: --quick (recommended for development)"
}

run_vue_tests() {
    echo "🚀 Running Vue.js tests..."
    cd "$REPO_ROOT/app/bibleview-js"
    
    if [ ! -d "node_modules" ]; then
        echo "📦 Installing npm dependencies..."
        npm install
    fi
    
    echo "🧪 Vue.js test suite..."
    npm run test:ci
    
    echo "🔍 Vue.js linting..."
    npm run lint
    
    echo "📝 Vue.js type checking..."
    npm run type-check
    
    echo "✅ Vue.js tests completed successfully!"
}

run_android_tests() {
    echo "🔧 Running Android tests (this will take 10-45 minutes)..."
    cd "$REPO_ROOT"
    
    echo "⚠️  Starting Android tests - DO NOT CANCEL"
    echo "   This requires internet connectivity and significant time"
    
    ./gradlew testStandardGoogleplayDebugUnitTest
    
    echo "✅ Android tests completed successfully!"
}

run_android_check() {
    echo "🔍 Quick Android environment check..."
    "$SCRIPT_DIR/android-test-check.sh"
}

# Parse command line arguments
MODE="quick"
if [ $# -gt 0 ]; then
    case "$1" in
        --vue-only)
            MODE="vue"
            ;;
        --android-only)
            MODE="android"
            ;;
        --quick)
            MODE="quick"
            ;;
        --full)
            MODE="full"
            ;;
        --help)
            show_help
            exit 0
            ;;
        *)
            echo "Unknown option: $1"
            show_help
            exit 1
            ;;
    esac
fi

echo "Mode: $MODE"
echo ""

case "$MODE" in
    vue)
        run_vue_tests
        ;;
    android)
        run_android_tests
        ;;
    quick)
        run_vue_tests
        echo ""
        run_android_check
        echo ""
        echo "🎯 Quick validation complete! Vue.js tests passed, Android environment checked."
        echo "   For full Android testing, use: $0 --android-only"
        ;;
    full)
        run_vue_tests
        echo ""
        run_android_tests
        echo ""
        echo "🎉 Full test suite completed successfully!"
        ;;
esac