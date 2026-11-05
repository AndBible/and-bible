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

There are 88 posted methods and one synchronous exception, `getActiveLanguages`.
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

## Document generation (stale pages)

A web view outlives its document, and since #3950/#3955 even its window: a workspace switch hands the outgoing
view to an incoming window, and its old page stays on screen and keeps calling until the new document replaces it.
Calls of such a page must not act on the window that now owns the view (osTicket 3508, #3975).

- Admission is ONE state per window, `DocumentAdmission` (`session.admission`), replaced only on the main thread
  and driven by `DocumentLoader`. It holds the admitted document (its generation, sent with its `clear_document`, its
  immutable `DocumentSource` = window, page object, module and document context, and the contexts the page holds:
  the loaded chapter plus what infinite scroll added), the pending request (`Window.loadText` ->
  `WindowView.documentRequested`) with whether it changes the document, and the generation the page reports it shows
  (`documentShown`). The document context is the Bible book + chapter (Bible: in the module's versification, which
  the window's verse need not be in; MyNotes: in KJVA; a book introduction, chapter 0, belongs to chapter 1), the
  Bible book (commentary), and the key for every other page (general book, EPUB, MyDocument / AI page, StudyPad,
  multi-reference, dictionary, map): another entry that replaces the page is another document. A loaded document's
  contexts are the chapters of its range; infinite scroll adds a chapter or entry when it reserves it and removes it
  again when its read fails; a page restart (`setClientReady`) resets them to the replayed document's, and if the
  window stands outside it (it had scrolled into an added chapter) the window loads its position again.
- Everything is derived from that state where a call acts, on the main thread (`DocumentAdmission.admits`): the call's
  generation is the admitted one, no request of a different document is pending, and the window still shows the
  admitted source (`DocumentSource.of(window)` compared with it). The last check makes a page change that has not
  been requested yet safe: an off-main sync or restore, the gap between `setCurrentDocument` and `loadText`.
  `session.documentGeneration` (the dispatcher's first, off-main check) is the admitted generation, 0 while none is,
  while a view is parked for reuse, and while a different document is requested. A request of the SAME document (a
  reload of the shown chapter, an unchanged synced commentary) keeps the page's calls admitted; only scroll reports
  (`scrolledToOrdinal`) wait for no request at all, because the request reads the key they write.
- A request ends (`documentRequestEnded(request, unchanged, failure)`): `unchanged` (the commentary checksum matched)
  makes the page's document stand for the request; a `failure` of a request of a different document, or of a retry
  from an error document, admits an error document with a retry link (`ErrorDocument(retry = true)`, `ab-retry://`
  -> `window.loadText()`), so the window is never left closed silently; otherwise the page's document counts again
  only while the window still shows it. `Window.loadText` reports a failing delivery instead of letting it end the
  app; a delivery that fails after the document was admitted (the web view throws on `clear_document`) shows the
  error document from `DocumentLoader.loadDocument` itself.
- JS (`src/bridge/document-generation.ts`): `clear_document` switches the page to the new generation once the cleared
  content has left the DOM, and `native-bridge.ts` puts it on every call's envelope
  (`{method, args, documentGeneration}`). Then the page posts `documentShown(generation)`.
  `clear_document` also drops everything still pending for the old content: infinite scroll's buffered chapters,
  queued requests and the scroll correction of a top insert, and a `setup_content` still awaiting the old document's
  render (`documentEpoch()`), so no old work acts on the new DOM under the new generation.
- Every call is in exactly one `BridgeGenerationPolicy` set (`BridgeGenerationPolicyTest`); `BridgeDispatcher` routes
  by it through the bridge's `DocumentGenerationGate`:
  - `guarded` (default for anything that navigates or acts on the current or active window): dropped at once when
    its generation is not admitted; otherwise run on the main thread and only if the generation is still admitted
    there for the same window (`ReadingBridge.runAdmitted`). A handler that acts after a further hop or suspension
    (queued writes, an IO or database read, `launch(Main)`) takes a `session.documentTicket()` and checks
    `session.holdsDocument(ticket)` right before it acts, after its last suspension (e.g. `LinkControl.openStudyPad`'s
    `stillWanted`, after the label read).
  - `paging` (`requestMoreToBeginning`, `requestMoreToEnd`): admitted against the document the PAGE holds
    (`DocumentAdmission.runPaging`), on the main thread, in the same step as its synchronous effects: a Bible chapter
    is reserved in the loaded range and read off main from the module and Bible book captured there
    (`CurrentBiblePage.getDocumentForChapter(chapter, document, bibleBook)`); a commentary block or general book entry
    is computed off main from the captured module under a per-direction lock and moves the cursor on main only while
    the page's document is still admitted. A call of a replaced page is answered null at once. While a different
    document is requested, or the window does not show the page's document, the call waits: it runs when the page's
    document stands again (also re-checked periodically, 250 ms backing off to 5 s, since the window can return to it
    without any transition) and is answered null when a new document replaces it. A general book's next entry is
    read from the captured module's own key list (`CachedKeyPage.getKeyPlus(key, num, document)`; a key list is
    cached only for the book it was built from). A dropped request answers null,
    which ends infinite scroll for good, so it must not be dropped while the page's document may still stand.
  - `unguarded`: user data addressed explicitly (ids, book + ordinals), answers to the page, the live page's own state.
  The gate is the bridge itself; a bridge without one gets `ClosedDocumentGate` (guarded and paging calls always
  dropped), a bridge with no window document (the PoC) passes `OpenDocumentGate` explicitly.
- Link navigations (`window.location.assign` and plain anchors: Android `shouldOverrideUrlLoading` and the long-press
  menu, iOS and desktop link navigation) carry no generation. `ReadingLinkHandler` opens a link only while the page
  shows the admitted document (`session.pageShowsAdmittedDocument()`: its last `documentShown` is admitted,
  `DocumentAdmission.admits`); a swallowed link is logged with the admission state; the host's `openLink` does not even activate the window otherwise. The selection menu's
  compare and memorize use the same check. A restarted page shows nothing until its `documentShown`.
- Reusing a web view for another window closes the old page's modals (`close_modals`) on every platform.
- A guarded tap between the request of a DIFFERENT document and the page showing it is dropped (logged). That is
  the intended trade-off.

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
5. Classify it in `BridgeGenerationPolicy` (`guarded` unless dropping it from a replaced page would lose user data or
   wedge the page; anything that navigates or acts on the current or active window is guarded; `paging` only for
   chapter requests that extend the page's own content); `BridgeGenerationPolicyTest` fails for an unclassified
   method. A guarded handler that acts after a hop or a suspension (queued writes, IO, a database read,
   `launch(Main)`) rechecks `session.holdsDocument(ticket)` after its last suspension, right where it acts.
6. Add meaningful decoder/behavior coverage. `BibleJavascriptInterfaceParityTest` and
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
methods were. Unguarded calls run there; guarded and paging calls are moved to the main
thread by the gate (`ReadingBridge.runAdmitted`, `DocumentAdmission.runPaging`), where
navigation, document admission and view rebinds happen and the admission state lives, so
they can run after an unguarded call the page posted later. `documentShown` updates the
admission state on main too. WebKit invokes the iOS message handler on main.

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
