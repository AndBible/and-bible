# Legacy SwiftData fixtures (I3 legacy migration)

Tooling (run on the remote Mac, CI as fallback) that produces the test inputs and the test oracle for the I3 migration of the native
Swift app's data (SwiftData / Core Data SQLite stores) into the Compose app. Nothing here is shipped.
Spec: `docs/superpowers/specs/2026-10-17-i3-legacy-migration-design.md` (section 8, "Fixtures" and
"Oracle comparison"). Plan: `docs/superpowers/plans/2026-10-17-i3a-legacy-migration-reader-mapper.md`, Task 1.

## What is here

| File | Purpose |
| --- | --- |
| `I3FixtureGeneratorTests.swift` | XCTest. Seeds four scenarios with the Swift app's real models. Compiles against 1.0.1 and the pin. |
| `I3OracleExportTests.swift` | XCTest, pin only. Runs the app's startup repair on each generated store and writes the Swift exporters' outputs. |
| `run-on-mac.sh` | Remote-Mac driver: `generate <app> <ios>`, `oracle`, `package`. |
| `package_fixtures.py` (+ `test_package_fixtures.py`) | Packs generated stores and oracle outputs into deterministic zips plus `manifest.json`. |
| `../../.github/workflows/ios-i3-legacy-fixtures.yml` | Fallback: the same build and run on CI simulators. |

Swift app revisions (repository `AndBible/and-bible-ios`):

- `1.0.1` = `9ca04b8a9a2921fd1fb1b1f7653b377482d6854a` (App Store release).
- `1.0.2` = the pin `5b14d179e4bc6f8b40751d5593e9b73eb21bc211`. The `v1.0.2` tag (`07ab20b2`) sits two commits
  later and only bumps the marketing version (`AndBible/Info.plist`) and the App Store texts; `Sources/` and `Tests/` are byte-identical to the pin.
- `1.0.0` (released 2026-09-26) = build `2026.0918.122018` (App Store Connect), archived from `main` at `056b52be`,
  after the last model change `4d1c9def` (2026-09-15): **1.0.0 has the 1.0.1 model**, so the 1.0.1 fixtures cover it.

## How to regenerate

Primary path, the remote Mac (skill `ios-mac`; each `run` syncs the tree and takes the Mac build lock):

```
for app in 1.0.1 1.0.2; do for ios in 17 18 26; do
  scripts/mac-remote.sh run bash iosApp/legacy-fixtures/run-on-mac.sh generate $app $ios
done; done
scripts/mac-remote.sh run bash iosApp/legacy-fixtures/run-on-mac.sh oracle     # pin, iOS 26, all stores
scripts/mac-remote.sh run bash iosApp/legacy-fixtures/run-on-mac.sh package
scripts/mac-remote.sh fetch build/i3-fixtures
```

Copy `build/i3-fixtures/package/*` (the `<app>/<ios>/<scenario>.zip` files and `manifest.json`; manifest
`path` values are relative to that directory) to `sharedLogic/src/jvmTest/resources/legacymigration/fixtures/`
(total at most 20 MB, check with `du -sh`; currently 2.8 MB, 24 zips). Legacy checkouts and DerivedData live in
`~/work/i3-legacy` on the Mac; logs in `build/i3-fixtures/logs`.

The packaging test must run from its own directory: `cd iosApp/legacy-fixtures && python3 -m unittest test_package_fixtures`.

Fallback, CI: push to `ios` (changes under `iosApp/legacy-fixtures/**` or the workflow file) or start
**iOS I3 legacy fixtures** with `workflow_dispatch`, then
`gh run download <run-id> -n i3-legacy-fixtures -D <dir>` and copy as above. Intermediate artifacts
(`i3-generated-<app>-<ios>`, `*-logs-*`) are only for debugging a failed cell.

## Committed cells

Generated on the remote Mac, Xcode 26.6, simulator runtimes iOS 17.5, 18.6 and 26.5. Apps `1.0.1` and `1.0.2`
x iOS `17`, `18`, `26` x scenarios `fresh`, `rich`, `restored-android`, `preserved-ai` (24 zips). The oracle ran
on the pin under iOS 26 over all stores. Distinct model fingerprints (first 12 hex): 1.0.1/17 `b9c461c2cd73`;
1.0.1/18 and 1.0.1/26 `f350194971fe`; 1.0.2/17 `e5fea2ccf6c5`; 1.0.2/18 and 1.0.2/26 `8cdd5de71d3a`.

`1.0.0` has no cells of its own: it has the 1.0.1 model (see above), so the 1.0.1 cells cover it.

Known quirk, `preserved-ai`: the preserved Android `ai_settings.sqlite3` is schema v23 and the pin supports 22, so
the oracle `.abdb.zip` has no `aiSettings` entry there. The AI oracle for that scenario is
`oracle/ai_settings.sqlite3` written from the Swift rows only; the Kotlin test performs the U11 union itself.

### Zip layout

```
AndBible.store, AndBible.store-wal, AndBible.store-shm    CloudKit-capable store
LocalStore.store, LocalStore.store-wal, LocalStore.store-shm
sidecar.json                          {"userDefaults": {...}, "keychain": [{"service","account","value"}]}
PreservedDatabases/ai_settings.sqlite3   only in preserved-ai
oracle/AndBibleDatabaseBackup.abdb.zip   AndroidDatabaseBackupService.exportArchiveFile
oracle/ai_settings.sqlite3               RemoteSyncAISettingsDatabaseWriter.writeFullDatabase
oracle/report.json                       row counts, NSStoreModelVersionHashes digest, repair counts, errors
```

`report.json` keys: `fixture`, `fingerprint` (SHA-256 over the digests of both stores), `storeModelVersionHashes`
(per store: SHA-256 over the sorted `entity:hex(hash)` lines of `NSStoreModelVersionHashes`), `rowCounts`
(`<Store>.<Entity>`, counted by SQL on the untouched store through `Z_PRIMARYKEY`), `repairs`, `backupCategories`,
`backupEntryCount`, `aiSettingsWriteReport` or `aiSettingsSkipped` (the strict AI snapshot throws on stores
without AI rows, such as `fresh`), `errors`. The oracle zips of one scenario differ per runtime only if Core Data
behaves differently, which is exactly what the matrix is for.

All secrets in `sidecar.json` are fixed test strings. The generator does not write to the real Keychain or
`UserDefaults`; the sidecar is a description the Kotlin tests feed to a fake platform.

## Scenarios

All use the same container as `AndBibleApp.loadModelContainer` with `cloudKitDatabase: .none`:
`Schema(BibleCoreBaseModelRegistration.cloudModels + AIModelRegistration.cloudSyncableModels)` as `AndBible.store`
and `Schema(localModels + localOnlyModels)` as `LocalStore.store`. Only the location differs: an explicit URL
below `I3_FIXTURE_OUT/<scenario>/` instead of Application Support.

- **fresh**: container plus the default and system labels (`BookmarkService.prepareDefaultLabels`,
  `ensureSystemLabels`). No workspaces, no settings, empty sidecar.
- **rich**: default and system labels, then
  - two user labels with every style flag non-default, one with type and custom icon;
  - two workspaces (the second with an RTL name) with `textDisplaySettings`, `workspaceSettings` (including
    `speakSettings`, recent labels, auto-assign labels, StudyPad cursors, hidden compare documents), color,
    weights, maximized window and primary links window; three windows with page managers for bible,
    commentary and dictionary, every page-manager field set, a per-page-manager `textDisplaySettings`, and three
    history items each;
  - three Bible bookmarks (verified Android-import trust, offsets, primary label, type, icon, edit action, two
    labels each) with notes containing emoji, Arabic (RTL) text and a body of about 200 kB; one generic bookmark
    with playback settings;
  - a StudyPad with three entries (indent levels 0, 2, 1), one MyDocument with a page and content, a reading
    plan with three days (two completed);
  - `Setting` rows: `active_workspace_id`, `reading_progress_state_v1`, `memorization_progress_state_v1`
    (through the real stores), `SpeakSettings`, the Nextcloud configuration (`sync_adapter=NEXT_CLOUD`,
    `gdrive_server_url`, `gdrive_username`, `gdrive_folder_path`, `sync_enable_bookmarks`) and `remote_sync.*`
    rows (`remote_sync_device_identifier`, accepted baselines, Android book and playback-settings stores);
  - the AI rows of `RemoteSyncAISettingsTests.insertCompleteAISettingsFixture` (provider, configured model,
    prompt category, agent prompt, GlobalAISettings singleton, usage record, built-in override, raw log);
  - sidecar: `icloud_sync_enabled=true`, `discrete_mode=true`, `show_calculator=true`, `calculator_pin="4321"`,
    `locale_pref="fi"`; Keychain: one AI key (`org.andbible.BibleCore.AICredentials`,
    `llm_api_key_<provider-id>`) and `gdrive_password` (`org.andbible.remote-sync`).
- **restored-android**: `Fixtures/android-bookmark-room-v12.sql` of the Swift repo restored through
  `RemoteSyncBookmarkRestoreService.readSnapshot` + `replaceLocalBookmarks` (as
  `testIOSPatchMatchesAndAppliesToAndroidRoomV12Fixture` does), plus the snapshot's log entries, so the
  fidelity rows exist.
- **preserved-ai**: `rich` plus `PreservedDatabases/ai_settings.sqlite3` (Android schema v23): one provider that
  exists only on the Android side and a `GlobalAiSettings` row with the same fixed id
  (`a1000000-0000-0000-0000-000000000001`) as the Swift row but different values. Input for merge rule U11.

### Suffixed composite columns

Core Data names the columns of flattened composite values after their leaf; equal leaf names get numeric
suffixes (spec section 8). The generator therefore gives every leaf of `TextDisplaySettings`, `WorkspaceSettings`,
`SpeakSettings` and `PlaybackSettings` a distinct sentinel per owner: integer leaves are unique per owner
(`workspace A`, `workspace B`, each page manager use a different seed), Bool leaves alternate with the seed,
strings carry the seed. A column mix-up in the Kotlin schema map therefore yields a wrong value that
`SchemaMapGeneratorTest.suffixedLeavesMapToDistinctProperties` can detect. Bool leaves can only take two values,
so two Bool leaves with equal values under the same seed cannot be told apart by value alone; the alternation
pattern differs per leaf, which separates most pairs.

## API differences between 1.0.1 and the pin

Source files that changed between the two revisions (`git diff 9ca04b8a 5b14d179 -- Sources`): `EpubReader`,
`PageManager+Cloning`, `Window`, `AndroidDatabaseBackupService`, `JSwordBibleBook`, `JSwordVersificationAddress`,
`NavigationService`, `PageManagerBibleBookEncodingMigrationService`, `RemoteSyncBookmarkDatabaseMigrator`,
`RemoteSyncBookmarkRestoreService`, `RemoteSyncInitialBackupRestoreService`, `RemoteSyncWorkspace*`.
None changes a signature the generator calls, so the generator needs no conditional code.

Differences that matter for the data, and how they are handled:

- `PageManager.bibleBookEncoding` (pin only, column `ZBIBLEBOOKENCODING`) and its meaning: in the pin,
  `bibleBibleBook` holds JSword's `BibleBook.ordinal()` and `PageManager.init` marks it `"jswordOrdinal"`; in 1.0.1
  the integer is an index into the installed module's book list and there is no encoding column. The generator
  never touches the property, so 1.0.1 stores have no column and pin stores have `jswordOrdinal`. The oracle
  runs over both: on 1.0.1 stores SwiftData adds the column on load and the pin's
  `PageManagerBibleBookEncodingMigrationService` resets the unmarked rows to Genesis 1:1, which is the repair the
  Kotlin migration must reproduce (spec decision D-a / page-manager repair).
- `RemoteSyncBookmarkRestoreService.readSnapshot` migrates the staged Android database through the Room
  versions first (pin only). The `restored-android` input is already v12, so both revisions behave the same here.
- `Window` and `PageManager+Cloning` changes only affect cloning, which the generator does not use.
- `AndroidDatabaseBackupService` in the pin exports `bibleBookEncoding` and the Android default position for
  unmarked page managers. The oracle backup therefore differs from what 1.0.1 would export; it is only
  generated from the pin, by design.
- `PageManagerBibleBookEncodingMigrationService`, `PersistedOrdinalTrustMigrationService` and
  `DataMigration` are used only by the oracle (pin). The plan text says
  `PageManagerBibleBookEncodingMigrationService().migrate(...)`; the real API is
  `PageManagerBibleBookEncodingMigrationService(modelContext:).migrate()`, which is what the oracle calls.

## Oracle notes

- `SwordSetup.ensureModulesReady()` lives in the app target, not in `BibleCore`, so the oracle does not call it.
  `PersistedOrdinalTrustMigrationService` therefore uses a `SwordManager` without installed modules; fixture
  bookmarks are already verified (`verifiedAndroid`), so the repair is a no-op for them and counts of
  repaired/pending/unresolved rows are in `report.json`.
- `AndBibleApp.prepareContainerForUse` (default workspace, labels) is not run by the oracle; it needs the app
  target's `WindowManager`, and the generator already seeds the labels.
- `DataMigration.migrateIfNeeded()` acts on the simulator's Application Support directory, which is why the
  oracle installs each store there before running it. It is a no-op for healthy `AndBible.store` files.
- The oracle never modifies `I3_FIXTURE_IN`.
