# R8 obfuscation of Android release builds

Android release builds are shrunk, optimised **and obfuscated** by R8 (#4160). Google Play requires at least 25 %
obfuscated DEX code ("DEX code optimization", deadline February 2027); until #4160 `app/proguard-rules.pro` had
`-dontobfuscate` and Play reported 0 %.

## Scope

| Code | Obfuscated | Why |
|------|------------|-----|
| AndBible (`net.bible.**`: `:app`, `:sharedCore`, `:sharedData`, `:sharedLogic`, `:sharedUi`) | yes | the bulk of the gain; the reflective entry points have keep rules (below) |
| Libraries (Kotlin, kotlinx, AndroidX/Compose, Room, Koin, Ktor/OkHttp, ...) | yes | they ship their own consumer keep rules for R8 |
| JSword (`org.crosswire.**`, in-tree) | **no** (`-keepnames`) | still derives names from classes (`MsgBase` resource bundles named after `this::class.simpleName`, `ResourceUtil`/`CallContext`, `Convert`/`ReflectionUtil`) |
| Nextcloud/WebDAV client, commons-compress zip, commons-httpclient, slf4j | no | kept by the existing rules in `proguard-rules.pro` |
| **F-Droid flavour** (`fdroid`, both appearances) | **no** (`app/proguard-fdroid.pro`) | F-Droid builds from source on its own server; see "F-Droid" below |

Measured on `standardGithubRelease` at 6.0.1277 with `scripts/r8-obfuscation-share.py <mapping.txt> --apk <apk>`:

| Scope | DEX classes | renamed | methods (mapping) | fields (mapping) |
|-------|-------------|---------|-------------------|------------------|
| `net.bible.` | 4831 | 87.0 % | 44.3 % | 98.4 % |
| `org.crosswire.` | 1097 | 2.8 % | 3.8 % | 1.6 % |
| other (libraries) | 6885 | 85.1 % | 58.1 % | 99.7 % |
| **all** | **12813** | **78.8 %** | **49.7 %** | **98.8 %** |

Methods are lower than classes because overrides of framework and library methods (`onCreate`, `invoke`, `equals`,
...) keep their names. Play does not publish its exact formula; every measure here is well above 25 %. Obfuscating
`net.bible.` alone would give about 33 % of the DEX classes, so library obfuscation is what keeps a safe margin.

## Writing code that survives obfuscation

Class and member names of obfuscated code change in **every** build. So:

- Never persist or compare a class name (`javaClass.name`, `::class.simpleName`, `qualifiedName`) across builds:
  not in preferences, databases, sync payloads or files. Logging them is fine (the log shows the obfuscated name).
- Code that loads a class by name (`Class.forName`, `ServiceLoader`, a name in a resource or config file) needs a
  `-keep` rule naming that class, e.g. `GoogleDriveAdapterProvider` in `proguard-rules.pro`.
- WebView bridge methods are `@JavascriptInterface` methods; AGP's default rules keep them. Bridge dispatch inside
  AndBible goes through JSON method strings (`BridgeDispatcher`), not reflection.
- kotlinx.serialization: serial names are fixed at compile time (the original qualified name, or `@SerialName`), so
  serialized data does not change with obfuscation. Enum `name()` and `valueOf` keep the source names.
- New JSword code is covered by the `org.crosswire.**` rule.

When adding a reflective path, run a release build on the emulator (below) before merging.

## Reading obfuscated stack traces

A release build prints frames like

```text
at a32.E(r8-map-id-a2ce5146...b639a9:721)
```

The source file attribute is replaced by R8's **map id**, the `# pg_map_id:` line at the top of that build's
`mapping.txt`; the line numbers are kept (`-keepattributes SourceFile,LineNumberTable`). The map id identifies the exact
mapping, so a trace and its mapping cannot be mismatched. (`-renamesourcefileattribute SourceFile` is deliberately
**not** used: it would replace the map id with the constant `SourceFile`.)

De-obfuscate with `scripts/retrace-crash.sh` (the Android SDK `retrace` tool, picking the mapping by map id):

```bash
scripts/retrace-crash.sh logcat.txt.gz                      # in-app bug report attachment (osTicket), mapping found by map id
scripts/retrace-crash.sh --version-code 1277 logcat.txt.gz  # first fetch the GitHub release's mappings (gh, read-only)
scripts/retrace-crash.sh --mapping mapping.txt trace.txt    # any mapping file; refused if its map id is another build's
```

It searches `$ANDBIBLE_MAPPINGS_DIR` (default `.local/r8-mappings/`) and `.local/release/*/android/`. In-app bug
reports (osTicket: `logcat.txt.gz`, `last_crash_logcat.txt.gz`) carry the version and commit in the subject, followed by
the crash location: the first AndBible frame, or in an obfuscated build the first obfuscated frame such as `t58.j:234`
(`errorReportSubject` in `ErrorReportControl.kt`). `--version-code` takes the number after the last dot (6.0.**1277**)
and fetches the GitHub CI build's mappings only; a Play, Samsung, ... build of the same version is another build with
another map id. Play Console de-obfuscates its own crash and ANR reports with the mapping inside the uploaded AAB.

Tests: `bash scripts/test-retrace-crash.sh` (a real obfuscated trace from a release build on the emulator,
`scripts/tests/fixtures/retrace/`).

## Where each release's mapping is kept

| Channel | Who builds | Mapping |
|---------|-----------|---------|
| Google Play (`standardGoogleplayRelease` AAB) | `make release-internal` (`scripts/release/android.sh`) | inside the AAB (`BUNDLE-METADATA/com.android.tools.build.obfuscation/proguard.map`; `android.sh verify` refuses an AAB without it), so Play has it; `android.sh build` also keeps `.local/release/<versionCode>/android/mapping-<versionName>.txt`. Play Console: App bundle explorer, the version, Downloads, "ReTrace mapping file". |
| GitHub (`standardGithubRelease`, `discreteGithubRelease` APKs) | CI, `build-apk.yml` on a tag | release assets `mapping-andbible-<tag>.txt.gz` and `mapping-calculator-<tag>.txt.gz` (`retrace-crash.sh --version-code` fetches them) |
| Accrescent | `make accrescent` | archived by `scripts/archive-r8-mappings.sh standardAccrescentRelease` (below), and copied next to the APK set as `app/standardAccrescent/release/mapping.txt` |
| Samsung, Huawei, Amazon (manual builds) | the maintainer | run `scripts/archive-r8-mappings.sh <variant>...` (e.g. `standardSamsungRelease`) right after the build: `.local/r8-mappings/<versionCode>/<variant>/mapping-<commit>.txt.gz`; a mapping older than the HEAD commit is skipped, since its versionCode/commit key would be wrong |
| F-Droid | F-Droid's build server | none needed: the `fdroid` flavour is not obfuscated |

`.local/` is host scratch, not an archive: keep the mappings of released builds somewhere durable too (the GitHub
release assets are durable; Play keeps its own copy).

## F-Droid

F-Droid builds the `fdroid` flavour from the tagged source with its own toolchain, so the APK its users run was never
built by us. R8 itself is deterministic (re-running R8 on the same inputs gives the same `pg_map_id`), but we cannot
verify that F-Droid's whole build (JDK, Gradle cache, dependency resolution) reproduces ours byte for byte, and without
the exact mapping its users' traces could not be read. The flavour therefore stays unobfuscated
(`app/proguard-fdroid.pro`, applied by the `fdroid` product flavour); shrinking and optimisation stay on. Play's
threshold does not apply to F-Droid. If F-Droid builds are ever proven reproducible against ours (same `pg_map_id`
from the same tag), the flavour can be obfuscated and the mapping regenerated from the tag.

## Verifying a change to the keep rules

1. `./gradlew assembleStandardGithubRelease` (unsigned without the release keystore; sign the APK with the debug key:
   `zipalign -p 4` then `apksigner sign --ks ~/.android/debug.keystore --ks-pass pass:android`).
2. `scripts/r8-obfuscation-share.py app/build/outputs/mapping/standardGithubRelease/mapping.txt --apk <apk>`.
3. Smoke-test it on the emulator (`android-emulator` skill): startup, Bible/commentary/dictionary, search (index
   creation), a module download, bookmarks, StudyPad, backup and restore, AI settings, and the discrete flavour. Grep
   the app's logcat for `ClassNotFound|NoSuchMethod|NoSuchField|NoClassDefFound|SerializationException|ClassCast`.
