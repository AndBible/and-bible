# XCUITest authoring traps and Mac-side evidence

Read before writing or judging an iOS UI test (`iosApp/iosAppUITests/`). Commands: `SKILL.md`.

## State shared across classes

All classes of one `xcode-test` run use one simulator, so a class that is green alone can be red in
the full suite. These leaks are real, not app bugs; fix the test's launch state.

- **Crash-loop gate.** XCTest ends the app without a background transition. Two such kills within an
  hour show the "An error has occurred" recovery screen on the next launch. Launch with
  `-task6ResetCrashState`.
- **UI language.** `FeatureUITests` switches the app to Finnish (`locale_pref`); `-AppleLanguages (en)`
  does not override it. Launch with `-standardTestFixture` (English, no disguise).
- **Installed modules.** `-resetData` tests delete the Bible; `IncomingFilesUITests` installs a raw-text
  module that a fresh workspace then opens first. Use `-openDocument KJV` with `-openRef`, and provision
  KJV in the class (`KjvProvisioning.swift`) instead of relying on class order.
- **Workspace.** Windows and the switched document persist; `-resetDatabases` gives a fresh workspace
  and keeps the modules. `-resetData` also wipes the Room databases.
- **Opt-in classes** (`SyncAcceptanceUITests`, Discrete unlock) need controller-prepared state: they
  must `throw XCTSkip(...)` without it, never fail.
- A new class launches with `-task6ResetCrashState -standardTestFixture` (or subclasses
  `RealAppUITestCase` in `AppUITests.swift`). Judge a stream's UI tests by a full-suite run, not only
  `-only-testing`.
- UI tests end with an abrupt terminate and the workspace is saved on background: press Home before
  the test ends when it checks persistence or a relaunch.

## Night mode

The reading overflow "Night mode" toggle leaves the menu open by design, and the open popup removes the
pane from the accessibility tree. The stored toggle also survives between runs, so a test can start in
night. Therefore:

- Sample the last known pane frame (left 35 % only; the popup covers the right half); never re-open an
  already open menu.
- Start every round trip from a bright day page (`ensureDayPage`, see `EndToEndUITests.swift`).
- Use absolute thresholds (day luminance > 0.6), never "equal to the first sample".

## Assertions

- WebKit splits text into phrase-level StaticTexts: assert on a phrase (`Nicodemus`), not a whole verse.
- Do not assert exact CLDR/ICU strings (weekday and month names) or `UTType`/MIME answers: they differ
  between the iOS 18.6 and 26.5 simulator runtimes and can be null. Assert stable properties
  (`startsWith`), pin the locale explicitly, and pin exact values only for tables the code owns
  (`IosContentSource.FALLBACK_MIME_TYPES`).
- Read the attached element tree before changing a label: a "missing button" has a precise cause there.

## Discrete mode alerts

The SpringBoard alert "You have changed the icon for AndBible Dev" and our own Compose dialogs both
have an OK/Yes-like button. Dismiss the system alert through its SpringBoard element; tap our dialogs
by testTag `dialog-confirm`. Notices cover `welcome-screen` in the accessibility tree, so wait for
`dialog-confirm` as well.

## First-run flow and real-app tests

A real-app test (fresh install -> Welcome -> "Download and start" -> John 3 -> relaunch) finds glue
defects no unit, native or Roborazzi gate sees. Write it early in a port, and read `app-<device>.log`
(debug builds forward the JS console as `js[level]`) before guessing. Flow: Welcome -> "Download and
start" -> confirmation dialog (tap `Yes`, exact; not "Yes, do not ask again") -> Download screen with an
`OK` gate that enables when a Bible installs -> return to Welcome with a usable Bible opens reading.
A window needs a `WindowView` or `loadText` times out after 5 s; a restored window needs the one-off
`windowSync.reloadAllWindows(true)` or stays blank after a cold start.

## Reading a fetched `.xcresult` on Linux (no Mac, no lock)

`xcode-test` fetches `ios-<device>.xcresult` into `.local/mac/<container>/runs/<run>/xcode/step*/`.
With `--parallel N` xcodebuild prints no failure messages; `failures-<device>.txt` beside it
(`xcresulttool` output made on the Mac) lists them.

- `database.sqlite3`: open with Python `sqlite3` using `file:...?mode=ro` (there is no sqlite3 CLI).
  Tables `TestCases`, `TestCaseRuns`, `Activities`, `Attachments` (`xcResultKitPayloadRefId`),
  `TestIssues`. Join `Attachments.activity_fk` -> `Activities.testCaseRun_fk` ->
  `TestCaseRuns.testCase_fk` -> `TestCases` for one test's attachments.
- Payload: `Data/data.<refId>`. Text and PNG payloads are zstd (`from compression import zstd`, Python
  3.14). The automatic failure UI hierarchy ("App UI hierarchy for ...") shows the screen at failure.
- Screen recordings (mp4) are stored raw; `ffmpeg ... tile=5x2` makes a contact sheet.

## End-to-end sync fixture (https only)

The NextCloud and WebDAV adapters accept only https, so `EndToEndUITests.testFreshInstallToBackupChain`
needs an https server: a local Nextcloud behind `scripts/tls-proxy.py` with a throwaway cert pinned by
SHA-256 (the app's "Untrusted server certificate" dialog is answered by the test's pin path). An http
fixture fails at once with "the url must be https".

- `scripts/e2e-sync-fixture.sh run` brings the fixture up (Nextcloud, TLS relay, reverse tunnel to Mac
  port 18473), runs the chain test via `xcode-test`, and tears it down. `up`, `json`, `reset`, `down`
  are separate subcommands.
- Empty the remote folder before EVERY run: `e2e-sync-fixture.sh reset` (`up` and `run` do it).
- The chain test occasionally flakes at the selection menu ("offers 'Verses'"): rerun before debugging.

## Release "fresh install" on a Mac is a migration

Deleting an app on macOS keeps its sandbox container, so a Mac install of `org.andbible.ios` finds the
old Swift app's stores and runs the legacy migration (`org.andbible.ios.dev` never had that data). Treat a
"Release only, fresh install, on a Mac" report as an old-data bug. Reproduce on the simulator: seed legacy
fixture stores (`sharedLogic/src/jvmTest/resources/legacymigration/fixtures/1.0.2/26/rich.zip`) into the
Release app's `Library/Application Support`, then run
`MAC_RELEASE_KEEP_INSTALL=1 scripts/mac-remote.sh xcode-test iphone --release ...` (without the variable
`--release` uninstalls the app first). See `LegacyMigrationUITests.swift`.
