# Device scrollbar test hardening report

Base: `7bfd3c59c` (`dev-2`). Scope: test-only follow-up resolving non-blocking review items 1 and 2 from `device-scrollbar-fix-review.md`.

## Changes

- The constructor-MONO test now samples all four native `ScrollBarDrawable` parts and fading state immediately after constructing the view, before any explicit `updateBackgroundColor()` call. API 23/28 still assert the legacy hidden-scrollbar behavior.
- Preference setup, view construction, and cleanup in `withView` are guarded so failures during setup still restore the original mode/night preference; window destruction cannot skip preference restoration.
- The constructor test and attached native-fade test put view/activity/provider setup under cleanup guards. Fade test cleanup restores the original provider, detaches the view, and finishes the activity even if an earlier cleanup action fails.
- No production source changed. Existing native paint, queued-fade and drawable restoration tests remain in place; timed axes/theme coverage was not expanded.

## Verification

- Mutation RED: temporarily removed the constructor's `updateNativeScrollbars()` call from `BibleView.kt`, ran only `constructorMonoAndWithinMonoNightChangePreserveCustomFlagsOnExit`, then restored the production file. Result: expected assertion failure on immediate constructor thumb pixels (`expected black`, got the legacy translucent drawable pixel), 1 test, 1 failure, 0 errors. XML and log archived in `device-scrollbar-test-hardening/constructor-mutation-red-xml/` and `device-scrollbar-test-hardening/constructor-mutation-red.log`.
- Focused GREEN: `./gradlew -Dorg.gradle.native=false :app:testStandardGoogleplayDebugUnitTest --tests '*BibleViewScrollbarTest'`. Result: BUILD SUCCESSFUL; 12 tests, 0 failures, 0 errors, 0 skipped. Latest log/XML are archived as `device-scrollbar-test-hardening/focused-green.log` and `device-scrollbar-test-hardening/focused-green-xml/`.
- The scoped Gradle XML output directory was removed after archiving. `git diff --check` passed and production `BibleView.kt` is unchanged.

## Limits

No full Android suite/build, device/Chromium run, golden verification, broader theme/axis expansion, or independent controller review was performed in this scoped task. The two other non-blocking review notes remain outside scope.

## Commit

Pending at report creation.
