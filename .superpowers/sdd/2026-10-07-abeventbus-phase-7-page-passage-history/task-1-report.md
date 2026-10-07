# Task 1 Report: PageChange and mediator stream

## Status
DONE

## Changes
- Added `PageChange` variants `VerseChanged`, `BibleVerseChanged`, and `ContentLoaded`.
- Added synchronous `PassageChangeMediator.changes` stream and dual-posts beside the existing EventBus notifications.
- Routed page-content verse changes, JavaScript commentary/general-book scroll changes, and selected Bible verses through the mediator.
- Added the requested test subscriber reset at the end of `TestBibleApplication.onTerminate` and six Robolectric behavior tests.
- Did not edit `NavHostComposeActivity.kt` or any files outside the task scope, except this requested report.

## TDD and verification evidence
- RED: `./gradlew -Dorg.gradle.native=false :app:testStandardGoogleplayDebugUnitTest --tests "*.PassageChangeMediatorTest"` exited 1 in `:app:compileStandardGoogleplayDebugUnitTestKotlin`. Expected unresolved references included `PageChange`, `PassageChangeMediator.changes`, and `onBibleVerseSelected`.
- First focused implementation run exposed a test assumption, not a production defect: `onCurrentPageChanged` calls `window.updateText()`, which can synchronously emit two `BibleVerseChanged` events before `VerseChanged`. Updated the assertion to require the expected `VerseChanged` event without rejecting those existing update side effects.
- GREEN: `./gradlew -Dorg.gradle.native=false :app:testStandardGoogleplayDebugUnitTest --tests "*.PassageChangeMediatorTest" --tests "*.ToolbarStateServiceImplTest" --tests "*.EventBusAllowlistGuardTest"` exited 0 (`BUILD SUCCESSFUL`). It listed all three requested test classes. JUnit XML confirmed `PassageChangeMediatorTest=6`, `ToolbarStateServiceImplTest=14`, and `EventBusAllowlistGuardTest=5`: 25 tests total, 0 failures/errors.
- `git diff --check` exited 0. Removed only `app/build/test-results/testStandardGoogleplayDebugUnitTest` after collecting the scoped results.

## Self-review
- Confirmed synchronous delivery uses `EventSource.emit`; the explicit thread test checks the handler runs on the emitter thread.
- Confirmed legacy posts remain alongside stream emissions and the allowlist guard passes.
- Confirmed test lifecycle reset precedes subscription setup, avoiding cross-test mediator subscribers.
- The `currentPageChanged` test checks for the contract event rather than enforcing an exact full event list because `updateText()` has existing, unrelated side effects.

## Concerns
- No outstanding concerns. Gradle emitted existing configuration/deprecation warnings; no test or compilation errors on the final focused run.

## Commit
Pending at report creation; see controller's final message for commit hash.
