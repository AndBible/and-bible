# Tests and coverage in CI

## Shared JVM tests

The **Shared JVM tests** workflow (`.github/workflows/shared-jvm-tests.yml`) runs on
pushes and pull requests affecting shared modules, their build configuration, or
the workflow itself. Its `shared-jvm-tests` job runs these complete suites:

- `:jsword:jvmTest` and `:jsword:parityTest`
- `:kdom:jvmTest`
- `:sharedCore:jvmTest`, `:sharedData:jvmTest`, and `:sharedLogic:jvmTest`
- `:sharedUi:jvmTest`

`sharedUi:jvmTest` uses its existing headless Compose configuration; it does not
need Xvfb. Windowed KCEF tests belong to a separate task and are not covered by
this job.

The trusted-branch job decrypts the same Sword test modules as the Android unit
job, installs them in `~/.sword`, and checks their exact set with
`scripts/check-test-sword-modules.sh`. Secrets are not available to fork pull
requests: those receive a visible notice instead, and require a maintainer run
on a trusted branch. The encrypted fixture is never cached or uploaded.

The job summary lists test counts, failures, errors, and skips per suite. Download
`shared-jvm-test-results` from the workflow's **Artifacts** section for JUnit XML.
Results are uploaded even if a suite fails.

## Coverage

Coverage is informational: there is no minimum-percentage gate.

Kover measures JVM production code in `sharedCore`, `sharedData`, and
`sharedLogic`. Pass `-Pcoverage` to enable instrumentation; ordinary Gradle test
runs remain uninstrumented. CI generates individual XML reports and one merged
HTML/XML report. Download the `shared-jvm-coverage` artifact to browse the merged
HTML report. The job summary includes per-module and merged line coverage.

The merged report includes execution across the three modules' test suites;
individual reports show their own suite's execution. Native-only and Android-only
implementations are outside this JVM measurement. Kover's default compiler filters
apply; Room-generated DAO/database implementations remain in the denominator, so
`sharedData` coverage includes generated database code. `sharedUi` Compose coverage
is deliberately not enabled.

A local `:app` probe used the 109-test BookmarkControl, TextDisplaySettings, and
WindowControl subset (96 passed, 13 skipped). After compilation warmup, the first
uninstrumented/application-only Kover pair took 37.7/30.1 seconds. A repeated warm
pair took 20.0/21.4 seconds (about 7% overhead with `include=net.bible.*`). These
bounded samples do not establish the cost of a complete Robolectric run.
Unrestricted Kover
instrumentation failed 13 tests with `VerifyError` in Robolectric's transformed
`ResourcesImpl`. `:app` coverage therefore remains disabled pending a separate,
full-suite compatibility and timing evaluation; no application test configuration
was changed by this probe.

Reproduce the CI Gradle steps from the repository root after installing the
Sword fixtures. Run the report command even if a test fails; excluding the three
measured test tasks avoids rerunning them and allows reports from available data.
A compilation failure can leave reports partial or unavailable.

```bash
./gradlew --console plain --continue -Pcoverage \
  :jsword:jvmTest :jsword:parityTest :kdom:jvmTest \
  :sharedCore:jvmTest :sharedData:jvmTest :sharedLogic:jvmTest :sharedUi:jvmTest

./gradlew --console plain -Pcoverage \
  :koverHtmlReportShared :koverXmlReportShared \
  :sharedCore:koverXmlReportJvm :sharedData:koverXmlReportJvm :sharedLogic:koverXmlReportJvm \
  -x :sharedCore:jvmTest -x :sharedData:jvmTest -x :sharedLogic:jvmTest
```

The **Javascript checks** workflow's `unit-tests` job runs `npm ci` followed by
`npm run test:coverage` in `app/bibleview-js`. Vitest's V8 provider reports
production TypeScript, JavaScript, and Vue files, including files no test imports.
Its artifact contains HTML, Cobertura XML, JSON summary, and JUnit results; the job
summary shows test counts and line coverage. Run the same two npm commands locally
to generate these reports.

## Native PR validation

For relevant pull requests, `ios.yml` runs only its Kotlin simulator-test job
(`jsword-ios-tests`); the WebKit shim job is push/manual-only to bound macOS usage.
The iOS app build and its XCUITest suites are not in CI (removed 2026-11-11: the
hosted runner made the UI smoke set fail intermittently on almost every run). Run
them on the remote Mac (`scripts/mac-remote.sh xcode-test iphone --smoke`, see
`docs/ios-remote-mac.md`) whenever a change touches the iOS app or shared UI.
