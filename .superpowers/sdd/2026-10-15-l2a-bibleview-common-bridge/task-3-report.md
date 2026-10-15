# Task 3 Report: Typed BibleView Events

## Implementation

Added `JsExpr`, `JsCall`, and `jsString` in `JsCall.kt`, plus `SetupContent` and the typed `BibleViewEvents` builders in `BibleViewEvents.kt`. Added 11 event characterization tests covering escaped strings (including U+2028/U+2029), raw expressions, response calls, event argument strings, omitted-versus-null key behavior, call ordering, and the exact event-name set.

## Changed files

- `sharedCore/src/commonMain/kotlin/net/bible/sharedcore/bibleview/JsCall.kt`
- `sharedCore/src/commonMain/kotlin/net/bible/sharedcore/bibleview/BibleViewEvents.kt`
- `sharedCore/src/commonTest/kotlin/net/bible/sharedcore/bibleview/BibleViewEventsTest.kt`

## RED/GREEN

RED command:

```text
./gradlew -Dorg.gradle.native=false :sharedCore:jvmTest --tests 'net.bible.sharedcore.bibleview.BibleViewEventsTest'
```

Observed `BUILD FAILED` during `:sharedCore:compileTestKotlinJvm`, with the expected `Unresolved reference 'BibleViewEvents'` (also missing `jsString`, `JsExpr`, and `SetupContent`). The test therefore failed because the requested production API did not yet exist.

GREEN command: same focused command after implementation. Observed `BUILD SUCCESSFUL` and `:sharedCore:jvmTest` ran. XML `sharedCore/build/test-results/jvmTest/TEST-net.bible.sharedcore.bibleview.BibleViewEventsTest.xml` reported `tests=11`, `failures=0`, `errors=0`, `skipped=0`. The scoped `sharedCore/build/test-results/jvmTest/` directory was then removed as required.

## iOS compile

Command:

```text
./gradlew -Dorg.gradle.native=false :sharedCore:compileKotlinIosSimulatorArm64
```

Observed `BUILD SUCCESSFUL`. Existing warnings include `IosSpeakEngine.kt` unnecessary safe call and always-true condition. Gradle also reports the known Linux simulator-test target disabled warnings and sharedCore Android host-test configuration warning.

## Review / concerns

- `git diff --check` passed.
- Assertions use independently specified expected JS strings; raw `JsExpr` contents pass through without JSON quoting.
- Tests cover `bookmarkClicked` omission when `openNotes` is null and explicit `null` payloads in other fields.
- No dependency or out-of-scope source changes were needed.
- Gradle emits configuration and Kotlin warnings described above; no warnings originated from the new production files.

## Commit

Pending.
