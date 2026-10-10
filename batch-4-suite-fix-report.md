# Batch 4 suite fixture fixes

## Proven causes at 9cb64c0b0

- `.local/l1a4-unit-final.log:723`: NavigationControlSortOrderTest used ApplicationProvider-backed testCoreStrings without Robolectric registration.
- `.local/l1a4-unit-final.log:2641`: withRecordingToolbar restored singleOf(ToolbarStateServiceImpl), but c7833df5f added Events<Boolean>. CoreModule supplies CloudSync.runningChanged explicitly; the stale test binding persisted in global Koin and broke later reading hosts.
- Original binary results-generic.bin suppressed trace: AgentPermissionHostTest UncaughtExceptionsBeforeTest originated from WindowControl.chooseSettingsToCopy:299, pumped by DatabaseResetter:90 during pane-menu teardown. The fixture queued activity lifecycle work then deactivated its context without cancelling/joining it.
- `.local/l1a4-unit-final-thread-dump.log:195`: SDK33 Main slept in saveLogcat under hundreds of previous BibleApplication handlers. Test applications repeatedly installed a JVM-global handler without restoration. This amplified crashes; the final initiating Throwable is not identifiable from that dump.

## Changes (test-only)

- Restore ToolbarStateService explicitly with CloudSync.runningChanged; no generic Events binding.
- Cancel queued activity lifecycle children synchronously before the reset pump; join them and assert completion before deactivation.
- Register the resource-backed sort-order fixture with Robolectric/TestBibleApplication.
- TestBibleApplication restores the exact previous JVM crash handler in finally immediately after production onCreate, including exceptional startup; no production behavior changed.
- Add repeated fake/restore/real-host and application-handler lifecycle regressions; strengthen pending-picker cleanup assertion.

## Actual RED/GREEN

All runs used foreground `./gradlew testStandardGoogleplayDebugUnitTest -Dorg.gradle.native=false` with sandbox disabled and exclusive ownership. First run used the prescribed daemon/lock prelude. No full-suite run.

RED filters: `*.MainBibleActivityHandleWindowPaneMenuItemTest.restoringTheToolbarAllowsARealHostAfterRepeatedOverrides`, `*.MainBibleActivityHandleWindowPaneMenuItemTest.copySettingsToWindowDispatchesTheParsedOrderWithoutCrashing`, `*.NavigationControlSortOrderTest`, `*.TestBibleApplicationCrashHandlerTest`.

- `.local/batch4-fixture-red.log`: 5 executed, 5 failed for expected missing Events, unfinished lifecycle child, instrumentation absence (2), and handler identity mismatch.
- XML preserved in `.local/batch4-fixture-red-xml/` (3 classes).

GREEN filters: full MainBibleActivityHandleWindowPaneMenuItemTest, ReadingDialogHostTest, NavigationControlSortOrderTest, TestBibleApplicationCrashHandlerTest, AgentPermissionHostTest, CalculatorRotationTest (each `*.` prefix).

- An intermediate compile failed on incorrect bind DSL import; corrected typed single before running tests.
- `.local/batch4-fixture-green.log`: 39 executed, zero failures/errors; actual XML preserved in `.local/batch4-fixture-green-xml/` (6 classes).

Final expanded run used the GREEN filters plus HistoryRouteBackTest, ReadingChooserInGraphResultTest, ReadingDestinationInGraphTest, ReadingSearchBackTest, after strengthening handler startup assertion.

- `.local/batch4-fixture-verify.log`: 76 executed, 8 failed; all 39 original scoped cases pass. No missing Events or UncaughtExceptionsBeforeTest shown. XML preserved in `.local/batch4-fixture-verify-xml/` (10 classes).
- HistoryRouteBackTest: aHistoryRouteDestinationWithEmptyHistoryPassesThroughToThePop; aHistoryRouteDestinationStepsHistoryBeforePopping (null NavHostController).
- ReadingChooserInGraphResultTest: aPassageChosenInGraphMovesTheActiveWindow; aWorkspacePickedInGraphIsSwitchedToWithoutWaitingForOnResume (route stays reading); anAwaitedKeyChooserSelfLaunchNavigatesInsteadOfReachingThePlatform (unexpected platform intent).
- ReadingDestinationInGraphTest: theComposedReadingViewsWindowsGetTheirInitialContentLoad; theHostComposesTheRealReadingViewOnTheReadingRoute; theHostsDrawerCommandsReachTheComposedReadingViewsDrawer (null initial content/reading host).

`git diff --check` passed. Broader suite is NOT green; remaining readiness/navigation failures need separate root investigation. Controller owns full gate and independent review. Preserved XML must be used for these counts: scoped standard results are removed after preservation as repository instructions require.
