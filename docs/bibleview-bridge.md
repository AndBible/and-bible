# BibleView bridge

The L2a bridge shares transport, decoding and native-to-JS event encoding in
`:sharedCore` (`net.bible.sharedcore.bibleview`). Android handler logic and document
building stay in `:app`; moving document building is L2b, not part of this refactor.
The iOS host currently uses the common contract for its PoC, not full Android feature parity.

## Directions

### JS to native

`app/bibleview-js/src/bridge/native-bridge.ts`, imported first by `main.ts`, installs
`window.android` as a Proxy. Existing callers continue to use `window.android.*`:

- Android: `window.androidBridge.postMessage(JSON.stringify({method, args}))` enters
  `AndroidBridgeEndpoint`, then `BridgeDispatcher`.
- iOS: `window.webkit.messageHandlers.bridge.postMessage({method, args})` enters
  `IosBridgeMessageHandler`, which serializes the object for `BridgeDispatcher`.
- `BridgeDispatcher` parses the envelope, then `BibleBridgeDecoder` validates and coerces
  arguments and calls `BibleBridge`. Android's `BibleJavascriptInterface` implements that
  contract; iOS uses the PoC implementation based on `UnimplementedBibleBridge`.

There are 87 posted methods and one synchronous exception, `getActiveLanguages`.
The latter calls the Android endpoint directly; iOS reads injected `__activeLanguages__`
(with an English fallback). It cannot use a return value from `postMessage`.
Top-level `undefined` arguments become JSON `null` before posting.

The shim preserves an existing app bridge or installed Proxy only when it has the
`setClientReady` function. Chromium may itself define `window.android` for WebView media
integrity: namespace existence alone is not an app-bridge capability check. In that case
the shim installs the Proxy while preserving the original `android.webview` object.
Android registers and removes the transport under the same name, `androidBridge`.

### Native to JS

`BibleViewEvents` produces typed `JsCall` values. On Android, `BibleView.emit(call)` sends
these through `BibleViewJsChannel` and its platform `JsHost` to WebView evaluation.
The iOS PoC supplies its own `JsCall` sink to evaluate on main. Deferred replies use
`BibleViewEvents.response` or `responseString`, which encode `bibleView.response(...)`.

`JsExpr` is deliberately raw JavaScript inserted verbatim. App-built document literals
(such as `contentJson()`) are not necessarily JSON: never parse them as JSON just to
send them through the bridge. Producers must guarantee valid JavaScript. Use typed event
functions and `jsString` for strings, not interpolated user text. Encoding escapes quotes,
backslashes, control characters and U+2028/U+2029. Preserve omitted payload keys separately
from keys explicitly sent as `null`.

## Adding a JS-to-native method

1. Add the TypeScript member to `BibleJavascriptInterface` in
   `app/bibleview-js/src/composables/android.ts`.
2. Add the function to `BibleBridge` in
   `sharedCore/src/commonMain/kotlin/net/bible/sharedcore/bibleview/BibleBridge.kt`.
3. Add its entry to `BibleBridgeDecoder.entries`, including the parameter `kinds` string:
   `S` = `String`, `s` = `String?`, `I` = `Int`, `L` = `Long`, `B` = `Boolean`.
   If the JS caller uses `deferredCall`, also add the name to `deferredMethods` and put
   `callId: Long` first. The existing deferred methods are `parseRef`, `refChooserDialog`,
   `requestMoreToEnd`, `requestMoreToBeginning` and `getMyDocumentPageRawContent`.
4. Add the Android `override` to `BibleJavascriptInterface.kt`; keep handler behavior and
   threading there. Add the logging no-op to `UnimplementedBibleBridge`, which the iOS PoC
   inherits, unless iOS implements the operation.
5. Add meaningful decoder/behavior coverage. `BibleJavascriptInterfaceParityTest` and
   `BibleBridgeDecoderTest` must pass; they guard agreement between the TS contract,
   common contract/decoder and Android implementation. Check deferred failure replies too.

## Adding a native-to-JS event

1. Add the name to `EventTypeNames` in `app/bibleview-js/src/eventbus.ts` and implement
   the JS listener with the intended payload shape.
2. Add the typed function and name to `BibleViewEvents` (including its `names` set), then
   send the resulting `JsCall` through the host's event sink (`BibleView.emit` on Android).
3. Add encoding and payload-shape coverage to `BibleViewEventsTest`, including omitted
   versus explicit-null keys. `BibleViewEventNamesParityTest` guards event-name parity.

## Coercion and errors

The decoder preserves the old Java bridge's coercion contract (L2a spec section 4.2 / R4):
number-to-`Int`/`Long` conversion truncates toward zero; JSON `null` becomes numeric zero
or Boolean `false`. Null for a non-null `String`, wrong argument types and wrong arity
raise `BridgeArgumentException`. Numeric strings are not numbers. Integer conversion
uses the decoder's `Long` conversion followed by `toInt()` where needed; do not replace
this with a different overflow policy.

At the platform boundary nothing is allowed to throw (spec section 4.3). The dispatcher
logs malformed messages, unknown methods and handler/argument errors. Endpoint and iOS
handler boundaries also catch failures; Android endpoint/dispatcher logging is protected
against a throwing logger. Failed deferred calls answer `bibleView.response(callId, null)`
when the ID is recoverable. ID recovery follows decoder truncation/overflow behavior:
explicit JSON `null` recovers zero, but a missing, string or Boolean ID cannot be recovered.
A malformed envelope cannot promise a deferred response without a usable method and ID.

## Threading

Android dispatch is synchronous on WebView's JavaBridge thread, just as the old annotated
methods were; existing handlers retain their own main-thread/coroutine hops. WebKit invokes
the iOS message handler on main. Sharing the dispatcher does not move Android handler work
to main or into common code.

`BibleViewJsChannel` preserves Android's native-to-JS timing rules:

- Calls submitted while loading are dropped, not buffered. Client readiness is marked
  through `runOnMain` before normal event delivery resumes.
- On main, attached, and with an empty queue, work runs immediately. Off-main, detached,
  or behind queued work, it is queued FIFO under the channel lock. The first queued item
  posts a flush when attached; Android's attach callback also flushes queued work.
- `executeNow` bypasses the queue but still checks loading; its caller must ensure main.
- Suspending `evaluate` runs on the host's main dispatcher and awaits the callback without
  the loading gate (used for selection queries). It is not the normal event path.

The developer spec and acceptance record live in the optional private `docs/superpowers`
submodule: `specs/2026-10-15-l2a-bibleview-common-bridge-design.md` and the L2a section of
`status/ios-readiness-status.md`. Linux compilation is not iOS runtime acceptance.
