# AndBible Test Scripts

This directory contains helper scripts for validating tests and builds in the AndBible repository.

## Scripts Overview

### `test-validation.sh`
**Purpose**: Quick Vue.js test validation (recommended for development)
**Execution time**: ~20 seconds
**Internet required**: No (after initial npm install)

**What it does**:
- ✅ Validates environment setup (Java 17, Node.js 20.x, npm 10.x)
- ✅ Runs complete Vue.js test suite (140+ tests)
- ✅ Performs ESLint code quality checks
- ✅ Executes TypeScript type validation

**Usage**:
```bash
./scripts/test-validation.sh
```

### `run-tests.sh`
**Purpose**: Comprehensive test runner with multiple modes
**Execution time**: Varies by mode (20 seconds to 45+ minutes)
**Internet required**: Depends on mode

**Modes**:
- `--vue-only`: Vue.js tests only (~20 seconds, no internet)
- `--quick`: Vue.js tests + Android environment check (~25 seconds, no internet)
- `--android-only`: Full Android test suite (10-45 minutes, requires internet)
- `--full`: Complete validation suite (45+ minutes, requires internet)

**Usage**:
```bash
./scripts/run-tests.sh --help           # Show all options
./scripts/run-tests.sh --quick          # Recommended for development
./scripts/run-tests.sh --vue-only       # Fastest option
./scripts/run-tests.sh --android-only   # When Android changes need validation
./scripts/run-tests.sh --full           # CI-style complete validation
```

### `android-test-check.sh`
**Purpose**: Minimal Android environment validation
**Execution time**: ~5 minutes (with timeout)
**Internet required**: Yes

**What it does**:
- Verifies Android SDK setup
- Attempts minimal Android test execution
- Provides guidance if environment is not suitable for Android testing
- Gracefully handles CI environments without full Android SDK

**Usage**:
```bash
./scripts/android-test-check.sh
```

## Recommended Workflow for Copilot

### Daily Development
```bash
./scripts/test-validation.sh     # Quick Vue.js validation (20s)
```

### Pre-Commit Validation
```bash
./scripts/run-tests.sh --quick   # Vue.js + Android environment check (25s)
```

### Android-Specific Changes
```bash
./scripts/run-tests.sh --android-only   # Full Android testing (10-45min)
```

### Release Preparation
```bash
./scripts/run-tests.sh --full    # Complete test suite (45+ min)
```

## Environment Requirements

### For Vue.js Tests (Always Available)
- Node.js 20.x
- npm 10.x
- No internet required after initial `npm install`

### For Android Tests (Limited Availability)
- Java 17
- Android SDK properly configured
- Internet connectivity required
- Significant execution time (10-45 minutes)

## Troubleshooting

### Script Permission Issues
```bash
chmod +x scripts/*.sh
```

### Node.js Dependencies Missing
```bash
cd app/bibleview-js && npm install
```

### Android SDK Not Found
```bash
export ANDROID_SDK_ROOT=/path/to/android/sdk
```

### Internet Connectivity Issues
- Vue.js tests work offline after initial setup
- Android tests always require internet connectivity
- Use `--vue-only` mode when internet is unavailable