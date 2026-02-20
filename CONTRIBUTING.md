# Contributing to AndBible

Thank you for contributing to AndBible.

This repository contains:
- Android app code in `app/src/main/java` (Kotlin)
- Hybrid Bible rendering frontend in `app/bibleview-js` (Vue 3 + TypeScript)

## Code of Conduct

By participating in this project, you agree to follow the Code of Conduct in `CODE_OF_CONDUCT.md`.

## Before You Start

- For user support, use the support channels/templates instead of opening a code PR directly.
- For bugs, feature requests, and tasks, prefer opening or linking an issue first.
- For larger changes, describe your approach in the issue/PR before implementation.

## Prerequisites

- Java 17
- Node.js 20.x
- npm (CI uses Node 20 and upgrades npm)
- Android SDK (API levels used by this project)
- Recommended Node version is pinned in `.nvmrc` (`20.19.4`)

## Branching and PR Target

- The default branch is `current-stable`.
- Unless maintainers ask otherwise, open PRs against `current-stable`.
- Create your feature/fix branch from `current-stable`.

## Local Setup

```bash
# Check local toolchain and test-data prerequisites
./scripts/dev-env-check.sh

# Install JavaScript dependencies
cd app/bibleview-js
npm ci
cd ../..
```

## Build and Test

Run tests relevant to your change.

### JavaScript / Vue changes

```bash
cd app/bibleview-js
npm run test:ci
npm run lint
npm run type-check
```

Useful development commands:

```bash
cd app/bibleview-js
npm run dev
npm run build-debug
npm run build-production
```

### Kotlin / Android changes

```bash
./gradlew testStandardGoogleplayDebugUnitTest
./gradlew assembleStandardGithubDebug
```

If your environment has restricted write access to home directories (common in sandboxed environments), run Gradle via:

```bash
./scripts/gradle-safe.sh testStandardGoogleplayDebugUnitTest
./scripts/gradle-safe.sh assembleStandardGithubDebug
```

Instrumented tests (requires emulator/device):

```bash
./gradlew connectedStandardGooglePlayDebugAndroidTest
```

Full repository check:

```bash
./gradlew check
```

### Bootstrap SWORD Test Modules (`~/.sword`)

Some unit/integration tests require local SWORD test modules.

CI-like flow (encrypted archive):

```bash
export DOWNLOAD_TEST_MODULES_URL="https://..."
export TEST_MODULE_ENCRYPTION_KEY="..."
./scripts/bootstrap-test-modules.sh
```

Local plain zip:

```bash
./scripts/bootstrap-test-modules.sh --zip /path/to/testmods.zip
```

Local encrypted zip:

```bash
./scripts/bootstrap-test-modules.sh --encrypted-zip /path/to/testmods.zip.enc --key "..."
```

## Style and Conventions

- Follow `.editorconfig`.
- Keep changes focused and avoid unrelated refactors in the same PR.
- Update docs/tests when behavior changes.

Commit message convention for issue fixes:

```text
Fixes #NNN (short description)
```

## Pull Request Checklist

Before opening a PR:

- Ensure relevant tests pass locally.
- Ensure lint/type checks pass for frontend changes.
- Include screenshots when UI behavior changes.
- Fill in `PULL_REQUEST_TEMPLATE.md` (benefits and possible side effects).
- Link related issue(s).

## Where to Ask Questions

- Developer docs/wiki: <https://github.com/AndBible/and-bible/wiki/Developer-documentation>
- FAQ: <https://github.com/AndBible/and-bible/wiki/FAQ>
- Matrix chat: <https://matrix.to/#/#andbible:matrix.org>
