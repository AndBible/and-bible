# Task 4 Report: Delete legacy event classes and forwarders

Date: 2026-10-07
Base: `9658f10ed` (`ABEventBus phase 9: post sites and witness tests use UserMessages`)

## Changes

- Deleted `app/src/main/java/net/bible/android/control/event/ToastEvent.kt`.
- Removed the transitional ABEventBus forwarding registration and obsolete imports from `BibleApplication.kt`; deleted nested `ErrorNotificationEvent`.
- Removed both legacy event names from the event-bus allowlist.
- Reworded the three shared-module comments to refer to a toast without mentioning app-level API.

## Verification

Command:

```bash
./gradlew -Dorg.gradle.native=false :app:testStandardGoogleplayDebugUnitTest --tests '*.EventBusAllowlistGuardTest' --tests '*.UserMessages*' --tests '*.ReadingHostResumeReconciliationTest' --tests '*.ReadingHostSyncAndRestoreEventsTest'
```

Output: `BUILD SUCCESSFUL in 2m 23s`; 58 actionable tasks (16 executed, 42 up-to-date). XML results under `app/build/test-results/testStandardGoogleplayDebugUnitTest/` confirm these five actual suites ran:

- `EventBusAllowlistGuardTest`: 5 tests, 0 failures/errors/skips.
- `UserMessagesPresenterTest`: 11 tests, 0 failures/errors/skips.
- `UserMessagesTest`: 6 tests, 0 failures/errors/skips.
- `ReadingHostResumeReconciliationTest`: 6 tests, 0 failures/errors/skips.
- `ReadingHostSyncAndRestoreEventsTest`: 10 tests, 0 failures/errors/skips.
- Total: 38 tests, 0 failures, 0 errors, 0 skipped.

The required reference search:

```bash
rg -n -e '\bToastEvent\b' -e 'ErrorNotificationEvent' app/src sharedCore/src sharedUi/src
```

Output: no matches.

`git diff --check` passed.

## Reference Counts and Scope Note

Counts on this checkout, before committing:

- `rg -n 'ABEventBus\s*\.\s*post\s*\(' app/src/main | wc -l`: 101 matched lines in app production sources.
- `rg -n 'ABEventBus\\.(register|safelyRegister)\\s*\\(' app/src/main | wc -l`: 27 matched lines in app production sources.
- `wc -l app/src/test/resources/net/bible/android/control/event/event-bus-allowlist.txt`: 34 entries.
- `rg -n 'ABEventBus\s*\.\s*post\s*\(' app/src/main sharedCore/src sharedUi/src | wc -l`: 116 matched lines across these source trees (includes test sources).
- `rg -n 'UserMessages\.(toast|errorNotification)\s*\(' app/src/main | wc -l`: 42 migrated message emission sites.

The binding constraints list expected phase metrics as 118 posts / 41 registrations / 34 allowlist entries. The measurable totals above do not all use the same scope as those expected metrics; in particular app production-only matched lines are 101/27, while the broader source-tree post search includes tests. I did not infer or claim the expected 118/41 totals from these differently scoped counts. The names and test guards confirm the two obsolete events were removed.

## Self-Review

- Only the requested forwarding block, old event type, allowlist entries and comment wording were changed; no migrated post sites or witnesses were modified.
- Shared-module comments contain no reference to `ToastEvent`, `ErrorNotificationEvent`, or `UserMessages`.
- No legacy event-name references remain in the requested source trees.
- No behavioral changes to the `UserMessages` presenter were made.

## Concerns

- The above source-count scope difference from the binding-constraints expected 118/41 figures is unresolved. The scoped tests, including the allowlist stale-entry guard, pass; final Task 4 allowlist size is 34.
