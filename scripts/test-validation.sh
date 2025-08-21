#!/bin/bash

# AndBible Test Validation Script
# This script helps Copilot and developers quickly validate tests

set -e

echo "🔍 AndBible Test Validation"
echo "=========================="

# Check environment prerequisites
echo "📋 Checking environment..."
echo "Java version: $(java --version | head -1)"
echo "Node.js version: $(node --version)"
echo "npm version: $(npm --version)"

# Vue.js tests (fast, no internet required after npm install)
echo ""
echo "🚀 Running Vue.js tests (fast)..."
cd app/bibleview-js

# Check if node_modules exists
if [ ! -d "node_modules" ]; then
    echo "📦 Installing npm dependencies (requires internet)..."
    npm install
fi

echo "🧪 Running Vue.js test suite..."
time npm run test:ci

echo "🔍 Running Vue.js linting..."
time npm run lint

echo "📝 Running Vue.js type checking..."
time npm run type-check

echo ""
echo "✅ Vue.js validation complete!"
echo "📊 Test summary:"
echo "   - Vue.js tests: ✅ Fast execution (~5-6 seconds)"
echo "   - Linting: ✅ Code quality checks passed"
echo "   - Type checking: ✅ TypeScript validation passed"

echo ""
echo "⚠️  Android tests require internet connectivity and take 10-45 minutes"
echo "    Use: ./gradlew testStandardGoogleplayDebugUnitTest"
echo "    For specific test: ./gradlew testStandardGoogleplayDebugUnitTest --tests \"*.TestClassName\""

echo ""
echo "🎯 Recommendation: Focus on Vue.js tests for rapid development cycles"
echo "    Run Android tests only when changes affect Android integration"