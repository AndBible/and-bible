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
- The `currentPageChanged` assertion filters to `PageChange.VerseChanged` and requires exactly one event for the target window, while permitting `updateText()`'s existing `BibleVerseChanged` side effects.

## Concerns
- Gradle emitted configuration and deprecation warnings. Their actual messages and origin verification status are recorded in the follow-up below; no test or compilation errors occurred on the final focused run.

## Commit
Implementation commit: `39a5a000c`.

## Review follow-up (base `39a5a000c76b148c561ab8e619aadaa95ae23605`)
- Tightened `currentPageChangedEmitsVerseChanged` to filter `VerseChanged` events and assert exactly one `VerseChanged(window)` value. This catches duplicate/wrong-window verse emissions without asserting that `updateText()` emits no other event types.
- Focused rerun command: `./gradlew -Dorg.gradle.native=false :app:testStandardGoogleplayDebugUnitTest --tests "*.PassageChangeMediatorTest"` (foreground, exit 0, `BUILD SUCCESSFUL`). The command listed all six mediator tests as PASSED: `currentPageChangedEmitsVerseChanged`, `selectingABibleVerseEmitsThroughTheMediator`, `bibleVerseSelectedEmitsBibleVerseChanged`, `currentVerseChangedEmitsVerseChangedForThatWindow`, `deliveryIsSynchronousOnTheEmittersThread`, and `contentChangeFinishedEmitsContentLoaded`. Result XML reported `tests=6`, `failures=0`, `errors=0`; the scoped test-results directory was removed after collection.
- The fresh focused rerun emitted these six `WARNING` messages: `android.disallowKotlinSourceSets=false` is experimental; `sharedCore`'s `commonTest` source directory exists while Android host tests are not enabled; `ErrorReportControl.kt:78` uses a delicate API; `CommonUtils.kt:366` references deprecated `versionCode`; `CommonUtils.kt:694` calls deprecated `getColor`; and `CommonUtils.kt:1971` uses a return in an expression-body function, which becomes an error in Kotlin language version 2.5. The initial broad focused command's retained output also showed Gradle/JDK restricted-method warnings for `System::load` from `net.rubygrapefruit.platform.internal.NativeLibraryLoader`, Vite dynamic+static import warnings for `default.yaml` and `en.yaml`, a Vite >500 kB chunk warning, and Kotlin/Java deprecation warnings (including deprecated overrides, Hamcrest `assertThat`, and `SOFT_INPUT_ADJUST_RESIZE`). Its terminal output was truncated and not retained as a separate log, so the review's earlier total of 26 warning messages cannot be reconstructed exactly here. These messages concern build configuration, unrelated existing source locations, or build tooling; however, their pre-task origin is not independently proven from retained evidence and is explicitly unverified.
- Follow-up commit: see commit hash in the coordinator's final message.
