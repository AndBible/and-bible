# `:sharedLogic` migration: conventions, ownership and phase-1 hand-over

Developer documentation for the L1b + D2 work (moving the service/controller logic and
`DatabaseContainer` out of `:app` into the new `:sharedLogic` module so Android and iOS share it).
The decisions are numbered S1-S13 in the design spec `2026-10-17-l1b-d2-shared-logic-design.md`
(private process repo); this page is the working reference for the four phase-1 plans (D2, G1, G2, G3).
Every count below sits next to the command that produced it, run at the phase-0 HEAD (branch `ios-4`).
Re-run the command when you rely on a count: numbers move as groups merge.

## 1. Module layering (S1, S2)

```
jsword -> sharedCore -> sharedData -> sharedLogic -> app
```

- `:sharedLogic` exists: `sharedLogic/src/commonMain/.../SharedLogicModule.kt` holds an empty
  `sharedLogicModule` (Koin) and `commonTest` loads it in an isolated Koin application. `BibleApplication`
  does not load it yet; the shared module is wired when the first bindings land (D2 / phase 2).
- `:sharedCore` may use JSword types (S2). `:sharedCore` never depends on `:sharedData` (Room boundary).
- No existing `:sharedCore` code moves. FQNs stay unchanged on every move (S6).
- Phase-1 groups only prepare: domain files stay in `:app` (except D2, which moves its files).

## 2. Ports (S8)

Rule: a platform-specific piece found in a domain becomes an interface. Put it in `:sharedCore`
(`net.bible.sharedcore.platform`) only if `:sharedUi` needs the type, otherwise in `:sharedLogic`. The
Android implementation lives in `:app`; the binding goes in the `:app` platform part of `CoreModule`
(`app/src/main/java/net/bible/android/control/CoreModule.kt`; D2 splits that file into platform and shared
parts, until then the bindings sit next to the others). Phase 0 made two exceptions to that rule by
choice (per the spec): `AppDirectories` and `MarkdownRenderer` sit in `:sharedCore` although `:sharedUi`
does not need them.

Ports that exist after phase 0 (`ls sharedCore/src/commonMain/kotlin/net/bible/sharedcore/platform/`):

| Port | Declared in | Android implementation / binding |
|---|---|---|
| `AppDirectories` | `platform/AppDirectories.kt` | `net.bible.android.AndroidAppDirectories` (delegates to `SharedConstants`); `CoreModule.kt` line 214 |
| `CoreStrings` | `platform/CoreStrings.kt` | `AndroidCoreStrings` |
| `AppEnvironment` | `platform/AppEnvironment.kt` | `AndroidAppEnvironment(androidContext())`; `CoreModule.kt` line 215 |
| `FilePorts` (`FilePicker`, `FileDelivery`) | `platform/files/FilePorts.kt` | Android pickers/share sheet |
| `MarkdownRenderer` | `platform/MarkdownRenderer.kt` | `net.bible.service.sword.mydocument.MarkdownToXhtml`; `CoreModule.kt` line 213 |
| `DatabaseHost` (D2) | `:sharedLogic` `service/db/DatabaseHost.kt` | `net.bible.service.db.AndroidDatabaseHost`, installed by `DatabaseContainer.installHost(...)` first in `BibleApplication.onCreate` (not a Koin binding: `DatabaseContainer` is needed before Koin starts) |
| `PlatformLocales` (G1) | `:sharedLogic` `platform/PlatformLocales.kt` | `net.bible.android.platform.AndroidPlatformLocales`; keeps legacy `iw`/`in`/`ji` codes (never `KLocale.default()`) |
| `AddonCatalog` (G1) | `:sharedLogic` `platform/AddonCatalog.kt` | `AndroidAddonCatalog` (delegates to `AndBibleAddons`) |
| `LocalCalendar` (G1 + G3) | `:sharedLogic` `platform/LocalCalendar.kt` | `AndroidLocalCalendar` (`java.util.Calendar`/`SimpleDateFormat`, DST-aware; G1's reading-plan members and G3's progress members in ONE interface and class) |
| `SearchIndexScheduler` (G1) | `:sharedLogic` `platform/SearchIndexScheduler.kt` | `net.bible.android.platform.AndroidSearchIndexScheduler` (starts the foreground `SearchIndexingService`); the queue itself (`IndexingQueue`) is common |
| `ReadOnlyModuleDbOpener` (G1) | `:sharedLogic` `platform/ReadOnlyModuleDbOpener.kt` | `AndroidReadOnlyModuleDbOpener` (calls `openReadOnlyModuleDb`, the platform file `service/db/ReadOnlyModuleOpen.kt`); resolved through `KoinPlatform` at first read, never during `initialiseJSwordFolders` |
| `ContentSource` (G1; `openSource(uri): Source?`) | `service/sword/SwordPorts.kt` (domain file, moves with `sword`) | `AndroidContentSource` (content resolver stream as an okio `Source`) |
| `OverridableLocaleProvider` (G1) | `service/sword/OverridableLocaleProvider.kt` (domain file) | `MyLocaleProvider` in `BibleApplication.kt` (`override: KLocale?`) |
| `LocaleSupport` (+ `LocaleValue`) | `:sharedLogic` `platform/LocaleSupport.kt` | `net.bible.android.platform.AndroidLocaleSupport` (`java.util.Locale`); `// G3 ports` block in `CoreModule.kt` |
| `SentenceBreaker` | `:sharedLogic` `platform/SentenceBreaker.kt` | `AndroidSentenceBreaker` (`java.text.BreakIterator`; -1 = DONE) |
| `NightModeState` | `:sharedLogic` `platform/NightModeState.kt` | `AndroidNightModeState` |
| `LabelNameStrings` | `:sharedLogic` `platform/LabelNameStrings.kt` | `AndroidLabelNameStrings(androidContext())`, reads resources on each access |
| `FirstLoadEdge` | `:sharedLogic` `platform/FirstLoadEdge.kt` | `AndroidFirstLoadEdge` (window domain's synchronous first-load edge) |
| `PromptPackSource` (+ `PromptPackRef`) | `:sharedLogic` `platform/PromptPackSource.kt` | `AndroidPromptPackSource` (add-on prompt packs; llm) |

Common primitives from G1 (`:sharedLogic`, package `net.bible.sharedlogic`): `sync.withMonitor` /
`monitorWait` / `monitorNotifyAll` / `bookNameMonitor` (on JVM/Android exactly the JVM monitor, so they exclude
against plain `synchronized`; `bookNameMonitor` is `org.crosswire.jsword.versification.BookName::class.java`),
`util.decodeUrl` (= `URLDecoder.decode(s, "UTF-8")`), `util.currentTimeMillis`.

G3 also added pure helpers (not ports) in `:sharedLogic`: `text/IsoUtcTimestamps`, `text/HtmlEntities`,
`text/NameBasedUuid` and `util/MemoryHints` (`expect fun requestGarbageCollection()`).

Ports still to be created by the groups (named in S8): certificate pinning (`webdav/PinnedTrust`), WebDAV sign-in UI and `SharedPreferences` in `WebDavConfig`
(G2), (the Room database builder, formerly listed here for D2, needs no port: `buildAppDatabase` uses Room 3's context-less `Room.databaseBuilder(name = absolutePath)`, characterized in `AppDatabaseBuilderTest`). Text segmentation landed in G3 as `SentenceBreaker`.

## 3. Koin (S7)

- A class that Koin builds: take its dependencies by constructor injection.
- Any other site: `org.koin.mp.KoinPlatform.getKoin().get<T>()` (or `.getOrNull<T>()`) instead of
  `org.koin.java.KoinJavaComponent.get/inject`.
- No `androidContext()` outside the `:app` platform module. Koin start order is unchanged (after JSword
  folder init; Koin starts after Sword init).
- Census, `grep -rn "KoinJavaComponent" app/src/main | wc -l` = 85 lines in
  `grep -rln "KoinJavaComponent" app/src/main | wc -l` = 31 files, whole `:app`. Within domain files
  (files owned by the domain map, platform files excluded; `scripts/l1-lib.sh` `files_of` then
  `grep -a "KoinJavaComponent"`):

| Domain | Files | Lines | Plan |
|---|---|---|---|
| download | 1 | 2 | G1 |
| installzip | 1 | 1 | G2 |
| cloudsync | 1 | 2 | G2 |
| sword | 1 | 2 | G1 |
| llm | 2 | 5 | G3 |
| page | 4 | 7 | G3 |
| core | 1 | 1 | G2 |
| all other domains | 0 | 0 | - |

  Same command over the domain files gives `androidContext()` = 0 in every domain.
- JVM-only third-party import `org.koin.java` is part of the `jvm3p` column of the baseline (section 7).

## 4. Test visibility (S11)

Convention (chosen here; the spec left it to the builder): a Kotlin `internal` member of a moved class
that `:app` tests use becomes `public` and is annotated `@VisibleForTesting`
(`androidx.annotation.VisibleForTesting`; `androidx.annotation` is Kotlin Multiplatform since 1.8 and is on
the move-readiness KMP allowlist, `KMPALLOW` in `scripts/l1-move-readiness.sh`). Rules:

- Apply it in phase 1 in place, so phase 2 is renames only. `:app` already uses the annotation in
  `grep -rl "import androidx.annotation.VisibleForTesting" app/src/main | wc -l` = 26 files.
- Do not widen an `internal` member that no `:app` test touches; it stays `internal`.
- `androidx.annotation` is not declared in any `build.gradle.kts` (`grep -rn "androidx.annotation"
  --include=*.kts --include=*.toml .` finds nothing) but is expected to reach `:sharedLogic` transitively:
  `:sharedData` exposes `api(libs.androidx.room3.runtime)`, and room3-runtime lists
  `androidx.annotation:annotation`; the KMP variant `annotation-iosarm64` 1.9.1 is in the Gradle cache. D2
  declares the dependency explicitly only if wanted, and must verify the `:sharedLogic` compile (all
  targets) with `@VisibleForTesting` in use.
- `:app` keeps its Robolectric tests; tests are not moved.

Census (command: `scripts/l1-lib.sh` `files_of` per domain, then
`grep -a -nE '^\s*(@\w+(\([^)]*\))?\s+)*internal\s+'`): 127 top-of-line `internal` declarations in domain
files; per domain: backup 17, bookmark 8, cloudsync 12, core 1, download 2, history 1, installzip 5, link 1,
llm 24, page 6, readingplan 2, search 2, speak 5, sword 27, versification 2, window 12 (others 0). They carry
126 distinct names; 93 of those names occur as a whole word in `app/src/test` or `app/src/androidTest`
(`grep -rqw <name>`). That 93 is an upper bound (name match only: generic names such as `emit`, `settings`,
`builder` match unrelated code). Each group re-derives the exact list for its own domains and widens only the
names a test really uses.

## 5. `CommonUtils.kt` rule

`service/common/CommonUtils.kt` (`wc -l` = 2070) is touched by every group. A group:

- moves its own helpers into a new file in the same package (`net.bible.service.common`), named after the
  helper family, so callers keep compiling and FQNs do not change;
- never edits another group's helpers;
- cleans the moved helper for common code and adds the new file to the domain map
  `scripts/l1-readiness-domains.txt` under the domain it belongs to, in the group's own comment block
  (`# G1`, `# G2`, `# G3`), so the move-readiness check counts the new file's own blockers and its
  importers stop counting it in the `app` column; it moves with its domain in phase 2.
  Revised 2026-10-17 (section 10): `scripts/l1-move-allow.txt` holds only the paths D2 moves.

Merge order for `CommonUtils.kt` (decision made here): the extraction hunks of G1, G2 and G3 sit a few lines
apart (declaration lines 1853, 1868, 1870, 1874, 1879, 1881, 1888, 1893, 1923 at HEAD, from
`grep -nE '^(const val CALC|val firstBibleDoc|suspend fun <T, V> Collection|private val builders|fun <R> useKdomBuilder|suspend fun Book.getBookAndKey|suspend fun BookAndKey)' service/common/CommonUtils.kt`)
and will conflict textually if merged independently. So the controller merges the `CommonUtils.kt` edits one
group at a time in the order D2, G1, G2, G3. Each group, after the previous group's merge, rebases and
re-checks its line anchors at the new HEAD before its own merge. The new helper files themselves never
conflict; only the deletions from `CommonUtils.kt` do.

Helper families per group, checked against `CommonUtils.kt` at HEAD
(`grep -n '^fun \|^val \|^suspend fun \|^const val \|^enum \|^data class' service/common/CommonUtils.kt`,
declaration lines in parentheses):

| Group | Family -> new file | Helpers |
|---|---|---|
| G1 | `KdomBuilderPool.kt` | `useKdomBuilder` (1881) and its `builders` queue (1879) |
| G1 | `NotificationChannels.kt` | `CALC_NOTIFICATION_CHANNEL` (1853; sword's `ProgressNotificationManager` import) |
| G2 | `AsyncMap.kt` | both `asyncMap` overloads (1870, 1874) |
| G2 | `AndBibleBackupManifest.kt` | `DbType` (2008), `BackupType` (2011), `ANDBIBLE_BACKUP_MANIFEST_FILENAME` (2018), `AndBibleBackupManifest` (2020) |
| G2 | (member of `object CommonUtils`) | `deviceIdentifier` (515): not movable to a file; cloudsync call sites switch to `AppEnvironment` instead |
| G3 | `BookKeyExtensions.kt` | `Key.tinyName` (1976), `Key.shortName` (1995), `BookAndKey.prev` (1893), `BookAndKey.next` (1923), `Book.ordinalRangeFor` (1974), `Book.getBookAndKey` (1888) |
| G3 | `FirstBibleDoc.kt` | `firstBibleDoc` (1868) |
| G3 | `LabelDisplayName.kt` | `BookmarkEntities.Label.displayName` (230; bookmark) |

Members of `object CommonUtils` itself (cloudsync/sword use `isDiscrete`, `tmpDir`, `realSharedPreferences`,
`deviceIdentifier`): the group that moves the calling domain replaces its own call sites with ports
(`AppEnvironment`, a port per S8). Nobody removes the members from the object while other callers remain.

## 6. File-ownership table (spec section 6, re-derived at HEAD)

Source: the offending imports behind the `app` column of `scripts/l1-move-readiness.sh`, listed with
`l1-move-app.py ROOT --lines` per domain (96 rows; the column total below also equals 96). "Importers" are
the domains with an explicit, non-wildcard import; same-package and wildcard uses are not seen (the compiler
finds those). One owner per file; a non-owner changes only its own call sites (it asks the controller for
anything inside the file).

`D2` owns, besides, `service/db/*` (`DatabaseContainer`, `AppDatabaseFiles`, `BlockingDb`, `ReadUserVersion`,
`oldmigrations/`; not `ImportDb.kt`, which is G2), `service/llm/AiDatabaseLifetime`, the sync hooks in
`cloudsync/SyncUtilities.kt` and the `CoreModule.kt` split. `scripts/l1-move-allow.txt` lists exactly the
D2-moved paths, so those never count in the `app` column.

| Non-domain `:app` file (symbols imported) | Importers (rows) | Owner | Basis |
|---|---|---|---|
| `android/SharedConstants.kt` | backup 2, download 1, installzip 3, sword 10 (16) | G2 | judgment: spec silent; the `AppDirectories` port replaces it (groups switch call sites to the port, only G2 adds members) |
| `android/BibleApplication.kt` (`BibleApplication`, `MyLocaleProvider`) | sword 3 | G1 | spec |
| `android/view/activity/nav/NavHostComposeActivity.kt` | sword 1 | G1 | judgment: sword-only |
| `service/common/AndBibleAddons.kt` (`AndBibleAddons`, `ProvidedReadingPlan`) | versification, installzip, sword, llm (4); readingplan (1) | G1 | spec |
| `service/common/XmlQueries.kt`; `useKdomBuilder` in `CommonUtils.kt` | sword 2; sword 3, llm 2, page 1 | G1 | spec |
| `service/common/BuildVariantProps.kt` (`BuildVariant`) | sword 1 | G1 | judgment: sword-only |
| `service/common/CommonUtils.kt`: `CALC_NOTIFICATION_CHANNEL` | sword 1 | G1 | judgment |
| `service/device/ProgressNotificationManager.kt` | sword 1 | G1 | judgment: sword-only |
| `service/sword/SwordEnvironmentInitialisation.kt` | document 1 | G1 | judgment: document is G1 |
| `service/common/Logger.kt` | navigation 1 | G1 | judgment: G1 swaps the call for `net.bible.sharedcore.log.Log`, `Logger.kt` untouched |
| `android/control/report/ErrorReportControl.kt` (`ErrorReportControl`; `LAST_CRASH_STACKTRACE_FILE` at line ~69) | link 1; backup 1 (the constant) | G2 | judgment: single owner because G2 must move the constant out of the file; G1 (link) only imports `ErrorReportControl` unchanged, or puts a port in front of its own call site |
| `android/control/report/AiBugReport.kt` | llm 1 | G3 | judgment: llm is G3 |
| `android/control/PassageChangeMediator.kt` | page 5, window 2 (7) | G3 | spec |
| `android/control/PageChange.kt` | page 1, window 1 | G3 | spec |
| `android/misc/ClientPageObjects.kt` (`OsisFragment`, `elementToString`, `sanitizeId`, `uniqueId`, `wrapString`) | bookmark 1, page 4 (`OsisFragment`); sword 1, page 1, page 1, page 2 | G3 | spec |
| `service/device/ScreenSettings.kt` | window 1 | G3 | spec |
| `service/common/CommonUtils.kt`: `firstBibleDoc` | page 1, window 1 | G3 | spec (family `FirstBibleDoc.kt`) |
| `service/common/CommonUtils.kt`: `displayName` (`BookmarkEntities.Label.displayName`, a getter-style extension property) | bookmark 2, page 1, sword 1 (`StudyPadKey.kt`, a cross-group importer) | G3 | spec (family `LabelDisplayName.kt`, section 5); sword (G1) only keeps its import valid, as for `shortName` |
| `android/view/activity/bookmark/LabelEditMapper.kt` (`LabelEditMapper`) | none in a domain by import: bookmark's `ManageLabelsServiceImpl.kt` shares its package (a same-package use the column cannot see); the explicit importer is the non-domain `NavHostComposeActivity.kt` | G3 | judgment: non-domain file used by the bookmark domain, bookmark is G3 |
| `service/common/CommonUtils.kt`: `shortName`, `tinyName`, `next`, `prev`, `ordinalRangeFor` | shortName: speak, sword, page; tinyName: speak, page; next/prev/ordinalRangeFor: speak | G3 | spec ("text/ordinal extension functions"); sword's one import is the G1 consumer, G3 keeps the import valid |
| `sharedui/AbAppTheme.kt` (`deriveToolbarFromTheme`) | page 1 | G3 | judgment: page is G3 |
| `service/common/FileManager.kt` (`FileManager`, `FileManager.copyFile`) | backup 1, download 1 | G2 | spec |
| `service/db/ImportDb.kt` (`importDatabaseFile`, `bookmarksDbStats`) | backup 2, installzip 2 (4) | G2 | spec |
| `service/common/CommonUtils.kt`: `asyncMap`, `BackupType`, `DbType`, `AndBibleBackupManifest`, `ANDBIBLE_BACKUP_MANIFEST_FILENAME` | cloudsync 3; backup 4, core 3, installzip 1 | G2 | spec |
| `service/common/CommonUtils.kt`: the `CommonUtils` object (`deviceIdentifier`, `isDiscrete`, ...) | cloudsync 1, sword 1 | none | shared file under section 5; callers replace their own use with ports |
| `service/cloudsync/nextcloud/NextCloudAdapter.kt` (`FOLDER_MIMETYPE`, a platform adapter) | cloudsync 1 (`WebDavCloudAdapter`) | G2 | judgment: cloudsync is G2 |
| `service/common/ArchiveUtils.kt` | in the `core` domain, not an `app` import | G2 | spec; G2 adds okio overloads first, G1 switches callers after G2 merges |
| ZIP/GZIP common code (S12), jsword `common.compress` | - | G2 | spec |

Check: sum of the `app` column = 96 = number of rows in the offending-import listing (section 7).

Part B status (G1, 2026-10-17; superseded by the phase-1 close-out in section 7, where all 21 domains are done): `sword` and `download` are not in `scripts/l1-move-done.txt`.

- `sword` waits for **G2's common ZIP reader** (`MyBibleInstaller` still uses `java.util.zip.ZipFile` and
  `java.io.File`; the `jvm` column is 2) and for **G3's extraction** of `shortName` (`BookAndKey.kt`),
  `displayName` (`StudyPadKey.kt`) and `elementToString` (`EpubOptimization.kt`), the three rows left in its
  `app` column. Its `jvmapi`, `jvm3p` and `android` columns are 0. `ReadOnlyModuleOpen.kt` and the two edge
  files (`EpubEdges.kt`, `MyDocumentEdges.kt`) are `:app` platform files. `D2`'s `CoreModule.kt` split takes
  G1's port bindings (`SearchIndexScheduler`, `ReadOnlyModuleDbOpener`, ...).
- **Invisible same-package dependencies (phase-2 work).** The `app` column counts imports, so it cannot see that
  sword domain files call platform files in the same package: `EpubEdges.kt` (`epub*Blocking`, from
  `EpubBackendState.kt`, `EpubOptimization.kt`) and `MyDocumentEdges.kt` (`myDocument*Blocking`, from
  `MyDocumentBackend.kt`). They are `:app` platform files (the `blockingAtEdge` bridges), so after the move
  phase 2 must give each an `androidMain` actual or a port. `MyDocumentBackend` reaches `MarkdownToXhtml` only
  through the `MarkdownRenderer` port (resolved at the call). The three G3 imports above are therefore the only
  *counted* `app` rows, not the only real sword dependencies on `:app`.
- `download` is 0 in every column but has `catch (oom: OutOfMemoryError)` at `DownloadManager.kt:132`, which
  D2's extended `jvmapi` check rejects. It waits for **G3 Task 1** (`MemoryHints.kt`, `isOutOfMemory`).
- `sword` is back in `scripts/l1-readiness-done.txt` (all counted columns but the informational `javaio` are 0).

## 7. Move-readiness baseline

`scripts/l1-move-readiness.sh` run at HEAD (columns: JVM imports, JVM-only API uses, JVM-only third-party
imports, android/androidx outside the KMP allowlist, imports of non-domain `:app` symbols). The `jvm` and
`jvm3p` columns count import lines plus inline fully-qualified uses on non-import, non-comment lines
(`java.util.concurrent.atomic.AtomicLong()`, `org.koin.java.KoinJavaComponent`); the earlier import-only
numbers were lower for that reason (and `llm jvm3p` also dropped because Task 5 moved YAML off snakeyaml,
section 8). Notes:

- The `app` column counts only explicit imports; same-package and wildcard references are invisible to it
  (the compile finds them).
- `scripts/l1-move-done.txt` was empty at the baseline (G1 Part A has since added its seven domains): a phase-1 plan adds a domain there when its row reads 0;
  `--check` then fails on any regression in that domain.
- `scripts/l1-readiness-done.txt` (the older L1a done list) no longer contains `cloudsync` and `sword`;
  Task 1 removed them because they have genuine Android/global uses (the WebDAV merge and
  `SearchIndexingService`). The groups that port them must re-add them when their ports land: G2 re-adds
  `cloudsync`, G1 re-adds `sword` (checked against the ownership table: cloudsync is G2, sword is G1).
  Update 2026-10-17: G1 re-added `sword` (Part B); `cloudsync` is still G2's.

```
domain	jvm	jvmapi	jvm3p	android	app
progress	4	2	0	0	0
versification	1	3	0	0	1
bookmark	11	0	0	0	3
readingplan	24	4	1	0	1
history	6	3	0	0	0
document	0	1	0	0	1
navigation	4	0	0	0	1
link	6	4	2	0	1
speak	10	62	0	0	5
backup	18	1	0	0	10
download	19	8	2	0	2
installzip	15	1	1	0	7
cloudsync	35	16	2	10	5
sword	43	66	8	12	27
llm	37	26	4	0	4
page	7	16	8	0	20
window	4	8	0	0	5
search	0	1	1	0	0
event	0	0	0	0	0
core	16	16	3	0	3
```

After G1 Part B (HEAD `36377cf83`): `versification`, `readingplan`, `history`, `document`, `navigation`, `link`,
`search` and `download` read 0 in every column; `sword` reads `jvm 2, jvmapi 0, jvm3p 0, android 0, app 3` (rows
above). `scripts/l1-readiness.sh` `sword` row: bridges 0, edge 0, pending 0, android 0, globals 0, jvmlibs 0,
javaio 2. Only `Math.`/`Charsets.`/`OutOfMemoryError`/`GlobalContext`/`@Volatile` grep (D2's extension) over the
Part B files: `DownloadManager.kt:132` only.

After G3 (branch `ios-6`, 2026-10-17; the baseline above is unchanged). Rows are
`scripts/l1-move-readiness.sh --domain <d>`; `progress`, `event`, `speak` and `bookmark` read 0 and are in
`scripts/l1-move-done.txt`. What remains: `page` jvmapi 4 (`synchronized(BookName::class.java)` x3,
`synchronized(currentDocument)`) and app 1 (`useKdomBuilder`); `window` jvmapi 1 (`synchronized(BookName::class.java)`; after the final-review fix, which moved
`WorkspaceServiceImpl` from `GlobalContext` to `KoinPlatform` and marked the `Window.kt` OOM catch, this is window's only
remaining blocker and waits for G1's `withMonitor`/`bookNameMonitor`, G3 Task 3, which did not land); `page` also waits on
Task 3 / G1 `withMonitor`; `llm` jvm 1 (`java.util.concurrent.ConcurrentHashMap` inline in
`AiDatabaseLifetime.kt`) and app 2 (`useKdomBuilder` imports).

Coordination (resolved at the G3 merge): G1 Task 4 and G3 Task 2 both created `LocalCalendar` + `AndroidLocalCalendar` with different members; the merge unified them into one interface and one class (G3's progress members added to G1's).
`AiDatabaseLifetime.kt` keeps D2's atomicfu `synchronized` and G3's widened members.

```
domain	jvm	jvmapi	jvm3p	android	app
progress	0	0	0	0	0
bookmark	0	0	0	0	0
speak	0	0	0	0	0
llm	1	0	0	0	2
page	0	4	0	0	1
window	0	1	0	0	0
event	0	0	0	0	0
```

Phase-1 close-out (branch `ios-2`, 2026-10-17, plan `plans/2026-10-17-l1b-phase1-closeout.md`). Every row of
`scripts/l1-move-readiness.sh --check` reads 0 and all 21 domains are in `scripts/l1-move-done.txt`. The
`jvmapi` column now ignores whole-line comments (`NONCODE`: import/package lines and lines starting `//`, `/*`
or `*`); a code line with a trailing comment still counts, and the REGRESSION detail skips the same
whole-line comments. `grep -rn L1b-handover app/src/main` prints nothing.

```
domain	jvm	jvmapi	jvm3p	android	app
(all 21 domains)	0	0	0	0	0
```

## 8. YAML and Markdown outcome (S9)

- **YAML**: `yamlToJson` now runs on `kaml` 0.104.0 (common YAML 1.2 parser). snakeyaml stays as a test-only
  oracle (parity test against it). Known differences, none present in the 38 real tool schemas: an unquoted
  `yes` stays a string (YAML 1.2), and quoted `"123"` / `"true"` become a number / boolean in the JSON
  output.
- **Markdown**: decided as a **port**. `MarkdownRenderer` is an interface in `:sharedCore` `platform`;
  `MarkdownToXhtml` is its Android implementation, bound in `CoreModule`. commonmark stays in `:app`;
  the iOS side supplies its own implementation of the port. JetBrains `markdown` 0.7.18 was tried and differed from the commonmark oracle on all 12
  parity fixtures.
- **kaml / Kotlin version**: kaml was built with Kotlin 2.2.21; only its module metadata was verified
  against our Kotlin 2.4.21. Its iOS compile against 2.4.21 is owed in phase 2.
- **App directories**: `AppDirectories` port in `:sharedCore`; `AndroidAppDirectories` delegates to
  `SharedConstants`.

## 9. Open points handed to phase 1

- Decisions marked "judgment" in section 6 and the allow-list step in section 5 are this page's calls where
  the spec is silent; the controller may overrule before plans are written.
- `LAST_CRASH_STACKTRACE_FILE` (done by G2): G2 extracted it from `ErrorReportControl.kt` into a new file in
  `net.bible.android.control.report` and is the only plan that edits `ErrorReportControl.kt`; G1 (link) edits
  only its own call site.
- `CommonUtils.kt` edits merge one group at a time (section 5; section 10 for the final rule).
- Release timing (S13): decided 2026-10-17, no release from `ios`; phase-1 in-place edits stay mechanical and minimal.

## 10. Phase-1 reconciliation (controller, 2026-10-17)

Decisions taken when the four phase-1 plans were written side by side; they override the plans where
they disagree.

- **Locks.** A *shared monitor* (an object whose monitor is taken in more than one file or by jsword:
  `BookName::class.java`, `Book` / `currentDocument` instances) is taken with G1's `withMonitor(target)` and
  `bookNameMonitor` (`:sharedLogic`, G1 part A). On Android it is the same JVM monitor as `synchronized`, so
  converted and unconverted sites keep excluding each other. A *class-private* lock may use atomicfu
  (`SynchronizedObject` + `synchronized` marked `// L1-common-ok: atomicfu lock`, or
  `reentrantLock().withLock`), after checking that no other file synchronizes on the same object. Each group
  converts only its own sites; G3's `BookName`/`currentDocument` sites wait for G1 part A. No lock is removed.
- **Blocking edges.** `blockingAtEdge` stays in `:app` (spec S10 superseded by the D1b follow-up decision);
  D2 adds a readiness check that fails on `runBlocking`/`blockingAtEdge` in any `commonMain`/`iosMain`.
  Domain files with `L1-edge` calls (sword: G1, window: G3) move each call into a small `:app` platform file
  listed in `scripts/l1-readiness-platform.txt`, marker and reason included; turning them into suspend paths
  is the edge-reduction backlog after L1b.
- **Scripts.** D2 owns `scripts/l1-*.sh`, `l1-lib.sh`, `l1-move-app.py` and their self-tests, and extends the
  move-readiness `jvmapi` column with `Math.`, `Charsets.`, `OutOfMemoryError`, `GlobalContext` and
  unqualified `@Volatile`. The other groups edit only data files (domain map, done lists, platform list), in
  their own comment blocks. A group's acceptance is its rows at 0 at HEAD after rebasing on D2.
- **Cross-owner edits.** Allowed only when call-site-only (import, argument, one line), listed in the plan's
  hand-over table, with `call-site edit in <owner> file` in the commit message.
- **Merge order.** The full-suite baseline plan first, then D2, then G1 part A early (it carries
  `withMonitor`), then the rest one at a time as they finish. `CommonUtils.kt` deletions merge one group at a
  time, each rebased and re-anchored at HEAD (section 5's order is preferred, any serial order works).
  Tasks that overlap E5 (emulator-debt fixes) start after E5 merges, and E5's tests are in their gate.
- **`oldmigrations/`** stays in `:app` permanently (Android-only legacy); `DatabaseContainer` reaches it
  through its host port.

## 11. D2 outcome (2026-10-17)

`DatabaseContainer`, `AppDatabaseFiles`, `ReadUserVersion`, `RoomMarked`, `DatabaseHost`,
`AiDatabaseLifetime` and `SyncDatabaseHooks` live in `:sharedLogic` `commonMain` (same FQNs, moved with
`git mv`, 100% renames). What stays in `:app`: `BlockingDb.kt` (`blockingAtEdge` only; the marked Room
wrappers went to `RoomMarked.kt`), `AndroidDatabaseHost.kt`, `oldmigrations/*`, `ImportDb.kt` (G2), and two
shims for G2 to delete when it switches its call sites: `readUserVersion(file: File)` (`JvmFileShims.kt`,
used by `BackupControl.kt`) and `SyncableDatabaseAccessor.localDbFile` (`SyncAccessorFileShim.kt`, used by
`CloudSync.kt`).

- **Spec S10 is superseded** (D1b follow-up decision): no `runBlocking`/`blockingAtEdge` in any
  `commonMain`/`iosMain`; `scripts/l1-readiness.sh` enforces it (`@no-common-blocking`). The cold open is
  `suspend DatabaseContainer.create()`; the synchronous `instance` getter reaches it through
  `AndroidDatabaseHost.openContainer()` (the `L1-edge`). I2 decides how iOS opens the container.
- **Koin.** No `KoinJavaComponent` left in any D2 file. `CoreModule.kt` now includes
  `PlatformBindings.kt` (`:app`) and `SharedBindings.kt` (phase 2 moves it to `:sharedLogic`); a definition
  snapshot test (`CoreModuleSplitTest`) pins the union.
- **Tests.** `:sharedLogic:jvmTest` runs the container on the bundled driver (12 tests incl. the sync-trigger
  and flush-survives-reopen cases); the iOS simulator run is owed to host CI. The "migrate from an exported
  version" common case needs `:sharedData`'s schema fixtures and is phase 2.
- **Test-isolation fix found by the full-suite gate:** `InstallZipBeforeDbInitTest` ran the real
  `initializeApp()` and left an `AiSettingsStore` observer on the production `AppCoroutineScope`, which reopened
  the JVM-global container after every later reset (a pre-existing leak; it surfaced as 4 red D2 tests only in
  the full run). The test now cancels what it started.
- **Baseline re-run** (full `:app` suite with Roborazzi, unfiltered): 5,124 tests, 13 failures = the 12
  environmental `SearchIndexingServiceTest` reds + `SpeakChangesTest` (known order/timing flake, green alone);
  no golden changed. Command: `./gradlew -Dorg.gradle.native=false :app:verifyRoborazziStandardGoogleplayDebug`.
- **Open:** `app/src/androidTest` does not compile (pre-existing drift in `DatabasePatchingTests`,
  `MainBibleActivityTests`; `DatabasePatchingTests.kt:60` additionally passes a `File` where
  `SyncableDatabaseAccessor` now takes a path).

**Shims retired (L1b close-out, 2026-10-17).** `JvmFileShims.kt` and `SyncAccessorFileShim.kt` are deleted from
`:app`: `BackupControl.kt` calls `readUserVersion(path: String)` and `CloudSync.kt` uses
`localDbPath.toPath()`. The old `File` forms live on only as test-source shims with the same FQNs
(`ReadUserVersionFileTestShim.kt`, `LocalDbFileTestShim.kt`) for the five test files that use them.
