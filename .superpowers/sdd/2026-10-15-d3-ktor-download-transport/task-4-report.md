# D3 Task 4 Report: KtorDownloadHttp

## Changes

- Added `sharedCore/src/commonMain/kotlin/net/bible/sharedcore/http/KtorDownloadHttp.kt` with shared Ktor client defaults and the `DownloadHttp` adapter.
- Added `sharedCore/src/jvmTest/kotlin/net/bible/sharedcore/http/KtorDownloadHttpTest.kt` with the 12 requested MockEngine cases. Clients are closed from `@AfterTest`; the positive content-length fixture explicitly supplies `Content-Length`.
- No build configuration changes or K5 files touched.

## RED

Command:

```text
./gradlew -Dorg.gradle.native=false :sharedCore:jvmTest --tests "*KtorDownloadHttpTest"
```

After adapting the test's Ktor response-data type, the run failed at `:sharedCore:compileTestKotlinJvm` with `Unresolved reference 'KtorDownloadHttp'` (and unresolved `downloadClientDefaults`/adapter members), as expected before the implementation existed. An earlier attempt with the wrong response-data type also failed compilation; it was corrected before accepting RED evidence. The output included normal Linux-disabled iOS simulator warnings.

## GREEN

Command:

```text
./gradlew -Dorg.gradle.native=false :sharedCore:jvmTest --tests "*KtorDownloadHttpTest"
```

Output: `BUILD SUCCESSFUL in 5s`. JUnit XML at `sharedCore/build/test-results/jvmTest/TEST-net.bible.sharedcore.http.KtorDownloadHttpTest.xml` reports `tests=12`, `failures=0`, `errors=0`, `skipped=0`.

The 12 passing tests cover GET header metadata, absent/malformed metadata defaults, real HEAD and non-200 reason, idle-only timeouts, streamed body reads, 200-only text, pre-request HTTPS enforcement, HTTPS-to-HTTP redirect rejection, socket-timeout mapping, other engine failure mapping, unchanged caller-block exceptions, and cancellation propagation.

## Self-review

- HTTP statuses remain returned as metadata; request methods preserve the GET/HEAD distinction.
- Request connect/socket timeout values come from `timeoutMs`; request timeout remains unset.
- `open` reads through Ktor's body channel from inside `execute`'s response block, keeping the response lifecycle active while the caller consumes it. Caller exceptions are captured until `execute` releases the response, then rethrown unchanged; cancellation remains unwrapped.
- The channel EOF case handles a zero-byte `readAvailable` on an already closed channel.
- Scheme validation occurs before a request; redirect response headers reject an HTTP `Location`. Ktor defaults keep HTTPS downgrade redirects disabled.
- No issue found during self-review. Diff whitespace check passed (`git diff --check`).

## Remaining gates / concerns

- The scoped JVM tests were run; the requested full suite and `:sharedCore:compileKotlinIosSimulatorArm64 :sharedCore:compileKotlinIosArm64` remain for the controller, per task instructions.
- No independent review was run, as explicitly directed.

## Fix Round 1: Preserve Caller Cancellation (2026-10-15)

### Regression and RED

Added `callerCancellationExceptionWithCausePassesThroughUnchanged`, which throws a specific `CancellationException` with an `IllegalStateException` cause from `open`'s caller block and asserts the same object is rethrown. Added `coroutineCancellationPropagatesAndReleasesRequest`, which cancels a real in-flight MockEngine request and waits for the engine handler's `finally` block.

Command (raw log: `evidence/fix1/red.log`; JUnit XML: `evidence/fix1/red.xml`):

```text
./gradlew -Dorg.gradle.native=false :sharedCore:jvmTest --tests "*KtorDownloadHttpTest"
```

Expected RED observed: `13 tests completed, 1 failed`; the identity regression failed with `DownloadTransportException` caused by the original `IllegalStateException`. This confirms Ktor's statement execution unwraps the caused cancellation and the adapter previously mapped it as an ordinary transport failure.

### Fix and GREEN

`open` now records a `CancellationException` thrown by the caller block before rethrowing it through Ktor. If `execute` throws, the adapter checks the current coroutine context with `ensureActive()` first (so actual coroutine cancellation remains authoritative), then rethrows the recorded caller cancellation object unchanged. Request execution remains within `execute`'s response block, and normal coroutine cancellation still cancels/releases the request as asserted by the new cleanup regression.

Command (raw log: `evidence/fix1/green.log`; JUnit XML: `evidence/fix1/green.xml`):

```text
./gradlew -Dorg.gradle.native=false :sharedCore:jvmTest --tests "*KtorDownloadHttpTest"
```

Output: `BUILD SUCCESSFUL in 30s`. JUnit XML reports `tests=13`, `failures=0`, `errors=0`, `skipped=0`. The compiler-error attempt during adjustment of the nested `try`/`catch` was not accepted as verification; after correcting block structure, the full scoped test class passed.

### Review and gates

Self-review confirmed the caller cancellation identity is restored only when the caller block recorded one, and a cancelled coroutine fails `ensureActive()` rather than having its cancellation replaced. The in-flight cancellation test verifies MockEngine cleanup. The controller's full JVM and both iOS compile gates were green on the initial commit; reruns after this fix are required and remain controller-owned. No independent reviewer/subagent was used.

Fix commit: `2910d1d63` (`D3: preserve caller cancellation through Ktor`), footer `Implemented by gpt-6-luna (Haiku role).`.
