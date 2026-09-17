/*
 * Copyright (c) 2026 Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
 *
 * This file is part of AndBible: Bible Study (http://github.com/AndBible/and-bible).
 *
 * AndBible is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software Foundation,
 * either version 3 of the License, or (at your option) any later version.
 *
 * AndBible is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with AndBible.
 * If not, see http://www.gnu.org/licenses/.
 */

package net.bible.android.view.activity.page

import android.content.ClipData
import android.view.View
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.core.view.GravityCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import net.bible.android.activity.R
import net.bible.android.control.bookmark.BookmarkControl
import net.bible.android.control.document.DocumentControl
import net.bible.android.control.event.ABEventBus
import net.bible.android.control.event.ToastEvent
import net.bible.android.control.navigation.NavigationControl
import net.bible.android.control.page.PageControl
import net.bible.android.control.page.StudyPadDocument
import net.bible.android.control.page.window.Window
import net.bible.android.control.page.window.WindowControl
import net.bible.android.control.search.SearchControl
import net.bible.android.control.speak.SpeakControl
import net.bible.android.database.IdType
import net.bible.android.database.SettingsBundle
import net.bible.android.database.SettingsLevel
import net.bible.android.view.Screen
import net.bible.android.view.ScreenLauncher
import net.bible.android.view.activity.base.ActivityBase.Companion.STD_REQUEST_CODE
import net.bible.android.view.activity.download.imageResource
import net.bible.android.view.activity.page.screen.clipboardKey
import net.bible.android.view.activity.settings.TextDisplaySettingsComposeActivity
import net.bible.android.view.activity.settings.getPrefItem
import net.bible.service.common.CommonUtils
import net.bible.service.db.exportStudyPads
import net.bible.service.download.FakeBookFactory
import net.bible.service.download.isStudyPad
import net.bible.service.llm.PromptContext
import net.bible.service.sword.BookAndKey
import net.bible.service.sword.StudyPadKey
import net.bible.service.sword.SwordDocumentFacade
import net.bible.sharedcore.reading.KeyChooserRoute
import net.bible.sharedcore.reading.OptionsMenuItem
import net.bible.sharedcore.reading.QuickDocAction
import net.bible.sharedcore.reading.QuickDocMenuItem
import net.bible.sharedcore.reading.QuickDocPicker
import net.bible.sharedcore.reading.QuickDocRow
import net.bible.sharedcore.settings.SettingsScope
import net.bible.sharedcore.settings.textSettingEditorPageFor
import net.bible.sharedcore.window.ReadingViewController
import net.bible.sharedui.docCategoryOf
import org.crosswire.jsword.book.Book
import org.crosswire.jsword.book.BookCategory
import org.crosswire.jsword.book.Books
import org.crosswire.jsword.passage.Key
import org.crosswire.jsword.passage.NoSuchVerseException
import org.crosswire.jsword.passage.VerseFactory
import org.crosswire.jsword.versification.BookName
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.coroutines.resume

/**
 * The reading view's COMMAND SURFACE, lifted out of [MainBibleActivity] -- reading-host re-typing
 * Task R3 (design spec §3.2, `2026-09-16-compose-reading-host-retyping`).
 *
 * Every member here used to be a `MainBibleActivity` member that `ComposeReadingViewHost` (or the
 * Compose reading toolbar, drawer, rail and menus it builds) calls to *do* something: the 23
 * `compose*` bridges, the three `applyChosen*` appliers the activity-result arms share with the
 * quick sheets, the three window-rail label/icon resolvers, the overflow and pane-menu
 * build/dispatch pairs, the drawer row dispatcher and the reference overlay's text. It also owns
 * the three PER-HOST objects that surface needs -- [bibleViewFactory], the
 * [textSettingsImagePicker] continuation and [mainMenuCommandHandler] -- which are instance
 * objects, not Koin singletons: a second instance of any of them is a silent bug, which is what
 * `ReadingCommandsDelegationTest` pins with `assertSame`.
 *
 * [activity] is still `MainBibleActivity` at this task. R4 introduces the narrow `ReadingHostActivity`
 * interface and R6 re-types this collaborator (and `ComposeReadingViewHost`) onto it; nothing here
 * should be read as a claim that the command surface *needs* the whole Activity.
 *
 * `MainBibleActivity` keeps a thin delegating stub for every exported member. The stubs are
 * load-bearing, not politeness: the eight Robolectric classes that are this batch's safety net
 * (`ReadingOptionsMenuTest`, `OptionsMenuStateBuilderTest`,
 * `MainBibleActivityHandleWindowPaneMenuItemTest`, …) call these members ON THE ACTIVITY and are not
 * edited in R1--R6.
 *
 * What deliberately did NOT move: the privates the command surface shares with the CLASSIC toolbar
 * and `updateActions()` (`menuForDocs`, `setCurrentDocument`, `startDocumentChooser`,
 * `cycleWorkspace`, `dummyStrongsPrefOption`, `updateStrongsButton`, `updateBottomBars`,
 * `pageTitleText`, `currentDocument`). Moving those would have dragged the classic reading view's
 * own listeners across with them; they were widened from `private` to `internal` on the Activity
 * instead. The four `drawer*` chrome-parity calls stayed too -- they are chrome, which R4's
 * `ReadingHostActivity` interface owns.
 */
class ReadingCommands(private val activity: MainBibleActivity) : KoinComponent {

    // ---- Koin singletons, exactly R1's move (ComposeReadingViewHost.kt) ----
    // Reachable without the Activity: MainBibleActivity resolves every one of these the same way
    // (`by inject()`), so each is the SAME object the Activity's own accessor used to hand back,
    // not a second instance (reading-host re-typing R6c1, spec addendum 2026-09-17).
    private val windowControl: WindowControl by inject()
    private val documentControl: DocumentControl by inject()
    private val speakControl: SpeakControl by inject()
    private val pageControl: PageControl by inject()
    private val bookmarkControl: BookmarkControl by inject()
    private val searchControl: SearchControl by inject()
    private val navigationControl: NavigationControl by inject()

    /**
     * The per-host `BibleView` cache. Built here rather than in `MainBibleActivity.onCreate` so
     * there is exactly ONE instance per host and the Activity's accessor is a view onto it; the
     * constructor only logs, so constructing it with the collaborator (i.e. at Activity
     * construction) is equivalent to the `onCreate` assignment it replaced.
     *
     * The type is spelled out rather than inferred: [BibleViewHostCallbacks.crashAllBibleViews]
     * below reaches this same factory through `MainBibleActivity.bibleViewFactory`, whose own type
     * is inferred FROM this property, so leaving both to inference makes the two definitions
     * circular ("Type checking has run into a recursive problem"). Naming it breaks the cycle and
     * changes nothing at runtime.
     */
    val bibleViewFactory: BibleViewFactory = BibleViewFactory(activity, bibleViewHostCallbacks())

    /**
     * R6a: what [BibleView] and its [BibleJavascriptInterface] need from the host beyond R4's
     * narrow [ReadingHostActivity]. Built HERE because this is where a `MainBibleActivity` is still
     * in hand — that is the whole point of the bundle (see [BibleViewHostCallbacks]'s kdoc).
     *
     * Every lambda is a one-line forward to the member the reading view used to spell out, with no
     * logic of its own; the only two-statement body, [BibleViewHostCallbacks.openDrawerAndFocusIt],
     * is the verbatim pair of lines `BibleJavascriptInterface`'s Alt+M fallback ran, moved here
     * only because `binding` is the host window's and never part of the reading view's contract.
     * Nothing changes about WHEN or HOW OFTEN any of them fires.
     *
     * A function rather than a property so it can be read from [bibleViewFactory]'s own
     * initializer above without a declaration-order constraint between the two.
     */
    private fun bibleViewHostCallbacks(): BibleViewHostCallbacks = BibleViewHostCallbacks(
        hostActivity = activity,
        onNext = { activity.next() },
        onPrevious = { activity.previous() },
        showLlmPromptSelector = { selection, context -> activity.showLlmPromptSelector(selection, context) },
        // This collaborator's own implementation, not the Activity's delegating stub: both run the
        // same body and the stub only exists for the Robolectric tests that call it on the Activity.
        composeSearchIfHosted = { seedQuery, preDecorated ->
            this@ReadingCommands.composeSearchIfHosted(seedQuery, preDecorated)
        },
        composeOpenDrawerIfHosted = { this@ReadingCommands.composeOpenDrawerIfHosted() },
        openDrawerAndFocusIt = {
            activity.binding.drawerLayout.open()
            activity.binding.drawerLayout.requestFocus()
        },
        composeReadingViewHost = { activity.composeReadingViewHost },
        showRegenerate = { pageId, bibleView -> activity.showRegenerate(pageId, bibleView) },
        // Through the Activity's accessor deliberately: it is a view onto `bibleViewFactory` above,
        // which this bundle is an argument to, so reaching it directly would be a cycle at
        // construction time. The lambda runs long after both are built.
        crashAllBibleViews = { activity.bibleViewFactory.crashAll() },
        currentNightMode = { activity.currentNightMode },
        // Read at call time, never captured — `readingInsets` is a mutable ledger, and these three
        // values feed `set_offsets`' JS payload. No arithmetic happens here (R2's constraint): the
        // division by display density stays in `BibleView`, exactly where it was.
        imeHeight = { activity.readingInsets.imeHeight },
        topOffset2 = { activity.readingInsets.topOffset2 },
        bottomOffsetForWebView = { activity.readingInsets.bottomOffsetForWebView },
    )

    /** The drawer/menu command handler [handleDrawerItemClick] and the Activity's own
     *  `onActivityResult` share. Same once-per-host reasoning as [bibleViewFactory]. */
    val mainMenuCommandHandler = MenuCommandHandler(activity)

    // Registered eagerly (constructor-time property, mirroring TextDisplaySettingsComposeActivity's
    // own photoPicker/pendingPick) so it's ready well before RESUMED, whichever reading-view sheet
    // is showing — Settings editor sheets T10, the reading view's in-place text-settings editor.
    private var pendingBackgroundImagePick: CancellableContinuation<String?>? = null
    private val backgroundImagePicker =
        activity.registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            pendingBackgroundImagePick?.resume(uri?.toString())
            pendingBackgroundImagePick = null
        }

    /** This Activity's own image picker for the in-place colours editor
     * ([composeReadingViewHost]'s [net.bible.sharedcore.settings.ColorSettingsController]). Each
     * host owns its own -- the service takes it as a PARAMETER precisely so two hosts cannot
     * clobber one another; see [net.bible.sharedcore.settings.TextDisplaySettingsService
     * .importBackgroundImage]'s kdoc for why that matters, and
     * [net.bible.android.view.activity.settings.TextDisplaySettingsComposeActivity]'s own
     * `imagePicker` for the twin of this property. */
    internal val textSettingsImagePicker: suspend () -> String? = {
        suspendCancellableCoroutine { cont ->
            pendingBackgroundImagePick = cont
            backgroundImagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            cont.invokeOnCancellation { pendingBackgroundImagePick = null }
        }
    }

    // ---- Compose toolbar bridge (Batch 12b-B Task 5) ----
    // Thin `internal` wrappers so `ComposeReadingViewHost`'s `ReadingToolbarCallbacks` (the
    // Compose reading toolbar) can drive the exact same actions as the classic
    // toolbar's click/long-click/fling listeners above and in `updateActions()` /
    // `setupToolbarFlingDetection()`, without widening any of those private members' own
    // visibility — each wrapper just calls into the existing private logic from within this class.

    /**
     * Batch Z-early A6: on the Compose path the ☰ button toggles the Compose
     * `ModalNavigationDrawer` (owned by [composeReadingViewHost]), not the native `DrawerLayout` —
     * which `setupUi` locks closed on that path.
     */
    internal fun composeToggleDrawer() {
        val host = activity.composeReadingViewHost
        if (host != null) {
            host.toggleDrawer()
        } else {
            // Defensive: the Compose toolbar only exists on the compose path, where the host is
            // always installed. Keep the native behaviour as a fallback rather than no-op.
            if (activity.binding.drawerLayout.isDrawerVisible(GravityCompat.START)) {
                activity.binding.drawerLayout.closeDrawers()
            } else {
                activity.binding.drawerLayout.openDrawer(GravityCompat.START)
            }
        }
    }

    /**
     * Whether the Compose drawer is open — `false` only before the host is installed, where the
     * native `binding.drawerLayout.isDrawerVisible(GravityCompat.START)` remains the answer used
     * elsewhere. Batch Z-early A7 fix C.
     */
    internal val composeDrawerOpen: Boolean get() = activity.composeReadingViewHost?.isDrawerOpen == true

    /**
     * Closes the Compose drawer if it is open; returns whether it did (i.e. whether the caller's
     * event has been consumed). Always `false` before the host is installed, so a caller can use it
     * as a leading guard without changing anything either way. Batch Z-early A7 fix A/C.
     */
    internal fun composeCloseDrawerIfOpen(): Boolean {
        val host = activity.composeReadingViewHost ?: return false
        if (!host.isDrawerOpen) return false
        host.closeDrawer()
        return true
    }

    /**
     * Opens the Compose drawer if a host is installed; returns whether it did.
     * Always `false` before the host is installed, so a caller can use it as a leading guard and
     * fall through to its existing native `drawerLayout.open()`.
     *
     * Batch Z-early A7 fix B: `setupUi`'s `LOCK_MODE_LOCKED_CLOSED` only gates `ViewDragHelper`
     * gestures — `DrawerLayout.openDrawer(View, Boolean)` makes no `getDrawerLockMode` call at all —
     * so a programmatic open (the Alt+M hardware-keyboard shortcut in `BibleJavascriptInterface`)
     * would still raise the NATIVE `NavigationView` underneath the Compose one, i.e. two drawers.
     * The classic call's companion `drawerLayout.requestFocus()` has no counterpart here and needs
     * none: the Compose drawer is a modal sheet that takes over input while open.
     */
    internal fun composeOpenDrawerIfHosted(): Boolean {
        val host = activity.composeReadingViewHost ?: return false
        host.openDrawer()
        return true
    }

    /**
     * The Compose toolbar's search button (F6 entry point 2). With a Compose host mounted, search
     * now happens IN the reading view for every document type — the toolbar enters search mode and
     * the results/index sheet rises — instead of starting the search Activity chain. A document that
     * cannot be searched at all is handled downstream by `searchKindFor` → `Unavailable` →
     * [ComposeReadingViewHost.searchUnavailableDocName]'s snackbar, not by keeping it off this path.
     *
     * The Intent route survives only for the case where `composeReadingViewHost` is null — in
     * practice only the brief window before `setupUi()` installs it. This method is only reached
     * from the Compose toolbar, so that branch is unreachable in practice; it is kept as a
     * defensive fallback rather than removed, the same idiom as the Z-early drawer retargeting.
     */
    internal fun composeSearch() {
        val host = activity.composeReadingViewHost
        if (host != null) {
            host.openSearch()
            return
        }
        searchControl.getSearchIntent(documentControl.currentDocument, activity)?.let { intent ->
            activity.startActivityForResult(intent, STD_REQUEST_CODE)
        }
    }

    /**
     * Opens the reading-view search for a hosted Compose activity; returns whether it did. Always
     * `false` before the host is installed, so every caller can use this as a leading guard and fall
     * through to its existing classic `Intent` unchanged — the same idiom as
     * [composeOpenDrawerIfHosted]. F6 Task 8b entry points 4 (`MenuCommandHandler`'s drawer search
     * row), 5 (`BibleJavascriptInterface`'s Ctrl+F) and 6 (the device SEARCH key, below).
     *
     * [preDecorated] marks [seedQuery] as ALREADY run through
     * [net.bible.android.control.search.SearchControl.decorateSearchString] (entry point 7,
     * `BibleView`'s selection "Search…") rather than a raw user query — see
     * [ComposeReadingViewHost.openSearch]'s kdoc for why that needs a different re-decoration path
     * than a fresh query.
     */
    internal fun composeSearchIfHosted(seedQuery: String? = null, preDecorated: Boolean = false): Boolean {
        val host = activity.composeReadingViewHost ?: return false
        host.openSearch(seedQuery, preDecorated)
        return true
    }

    /**
     * Strong's find-all (F6 Task 8b entry point 8, [net.bible.android.control.link.LinkControl.showAllOccurrences])
     * retargeted into a hosted Compose activity's search; returns whether it did. Same "leading
     * guard, fall through to classic otherwise" idiom as [composeSearchIfHosted] — `LinkControl`
     * calls this only once it has already decided the search document is indexed (the not-indexed
     * branch keeps classic's `Screen.SearchIndex` route unconditionally: prompting to index a
     * document other than the active window's is Task 11's machinery, which does not exist yet).
     *
     * A `false` return means the caller MUST use its own classic fallback — this is not just "no
     * host mounted" any more: [ComposeReadingViewHost.openSearchStrongs] also returns `false` (and
     * does nothing) when the active window's document is an EPUB, because Strong's find-all is a
     * Bible concept — it searches Strong's-enabled BIBLES, not the open document — so an EPUB on
     * screen must not swallow the request silently. (F43 Task 6 fix round 1: this method used to
     * return `true` unconditionally whenever a host was mounted, which turned that EPUB decline into
     * a silent no-op instead of falling through to the classic Strong's search.)
     */
    internal fun composeSearchStrongsIfHosted(ref: String, translationIds: List<String>): Boolean {
        val host = activity.composeReadingViewHost ?: return false
        return host.openSearchStrongs(ref, translationIds)
    }

    /**
     * F6 Task 9: the reading-view search's two-stage back, wired into [onBackPressed] as a leading
     * guard right after [composeCloseDrawerIfOpen] — first press closes the results/index sheet
     * (keeping the query and results), second leaves search mode. Returns whether the press was
     * consumed. Always `false` before the host is installed, same idiom as [composeCloseDrawerIfOpen].
     */
    internal fun composeCloseSearchIfOpen(): Boolean = activity.composeReadingViewHost?.closeSearchIfOpen() ?: false

    /**
     * Whether the Compose reading-view search mode is active — `false` only before the host is
     * installed. F6 Task 9: used in [onKeyLongPress] to swallow a long-press back while a search
     * field is focused, the same role [composeDrawerOpen] plays for the drawer there.
     */
    internal val composeSearchModeActive: Boolean
        get() = activity.composeReadingViewHost?.searchController?.searchModeActive?.value == true

    internal fun composeToggleSpeak() {
        if (activity.transportBarVisible) {
            if (speakControl.isStopped) {
                activity.transportBarVisible = false
            }
        } else {
            activity.transportBarVisible = true
        }
        activity.updateBottomBars()
    }

    /**
     * Round 14b §8: show the transport bar, idempotently — the main-menu Speak item's whole
     * behaviour. Deliberately NOT [composeToggleSpeak]: a menu row is a one-way action, so "Speak"
     * must never HIDE the bar (spec D3), and a user who picks it twice must not be worse off than a
     * user who picked it once.
     *
     * Lives here rather than in `ComposeReadingViewHost` because [transportBarVisible] is this
     * activity's private field and the single source of truth for bar visibility — the Compose side
     * only ever OBSERVES it, through the `SpeakTransportVisibilityChanged` the setter posts. The
     * setter's own `if (field == value) return` is what makes this idempotent: a second call posts
     * no event and triggers no recomposition.
     */
    internal fun composeShowSpeakTransport() {
        activity.transportBarVisible = true
        activity.updateBottomBars()
    }

    /** The Compose toolbar's Speak long-press: opens the Speak settings SHEET over the reading view. */
    internal fun composeSpeakLong() {
        activity.composeReadingViewHost?.showSpeakSettings()
    }

    /**
     * Whole-branch review fix C1: the quick sheet's own route to [switchToWorkspace].
     *
     * Every OTHER route to `currentWorkspaceId`'s setter saves the outgoing workspace first:
     * [cycleWorkspace] calls `windowRepository.saveIntoDb()` immediately before switching; the
     * classic `WorkspaceSelectorActivity` path calls it too and additionally gets an `onPause`; the
     * Compose selector calls `service.saveCurrentIntoDb()`. The quick sheet never pauses the
     * activity, so without this it is the only path that reaches `windowRepository.loadFromDb`
     * (whose first act is `clear()`) with the outgoing workspace's windows, page managers and
     * `HistoryManager` entries never written to `dao` -- silently discarding unsaved layout/history
     * changes on a quick switch. Mirrors [cycleWorkspace]'s save call exactly.
     *
     * Deliberately NOT folded into [switchToWorkspace] itself: that function is also the
     * `WORKSPACE_CHANGED` result arm's body, which runs AFTER the full selector has already renamed
     * the outgoing workspace in the DB. Saving there would recompute `contentText` from the STALE
     * in-memory `name` and push it back over a rename the user just made in the selector.
     */
    internal fun quickSwitchToWorkspace(workspaceId: String) {
        windowControl.windowRepository.saveIntoDb()
        activity.switchToWorkspace(workspaceId)
    }

    internal fun composeCycleWorkspace(forward: Boolean) = activity.cycleWorkspace(forward)

    /**
     * The Compose toolbar title's TAP. Round 15b Task 9: with a Compose host mounted, the three key
     * choosers simple enough for a sheet (`KeyChooserRoute`, spec §4.6) now open over the reading
     * view instead of starting their full screen; everything else takes the classic path below,
     * unchanged.
     *
     * Three separate conditions fall through to the classic path. Two are real cases, not
     * belt-and-braces: a page shape `KeyChooserRoute` deliberately keeps on its own screen
     * (dictionary, StudyPad, my-document, multi-document), and a chosen kind whose key list is
     * EMPTY — where both chooser activities apply a fallback selection and finish without drawing
     * anything, which a sheet cannot reproduce (E3). The third, no host mounted yet, is defensive
     * rather than reachable in practice: this method's only caller is the Compose toolbar's title
     * tap ([ComposeReadingViewHost.install]'s `onTitleTap`), wired inside `install()` itself, so the
     * host is already non-null by the time this can fire.
     *
     * `CurrentPage.startKeyChooser` itself is deliberately NOT touched, so `CurrentPageManager`'s
     * auto-open behaves exactly as today. (`BibleJavascriptInterface.refChooserDialog`, which this
     * note used to list alongside it because it needed an Activity result, no longer does:
     * nav-graph slice 7 Task 10 moved it onto [ComposeReadingViewHost.openVerseChooserSheetForResult],
     * a `CompletableDeferred` the sheet's own selection callback completes.)
     */
    internal fun composeStartKeyChooser() {
        val host = activity.composeReadingViewHost
        val sheet = host?.currentKeyChooserPage()?.let { KeyChooserRoute.sheetFor(it) }
        if (host != null && sheet != null) {
            // Resolved HERE, once, and handed to the sheet — never resolved again inside it. The
            // emptiness decision below and the sheet's rows must come from the same list (a
            // `KeyRow`'s id is an INDEX into it), and a second resolution is expensive on the UI
            // thread: `EpubBackendState.tocKeys` is not cached at all and rebuilds every `Key` on
            // each access. `ComposeReadingViewHost.showKeyChooserSheet`'s kdoc has the full note.
            val keys = host.resolveKeyChooserKeys(sheet.kind)
            if (host.keyChooserSheetHasRows(sheet.kind, keys)) {
                host.showKeyChooserSheet(sheet.kind, keys)
                return
            }
        }
        pageControl.currentPageManager.currentPage.startKeyChooser(activity)
    }

    /**
     * The Compose toolbar title's long-press. Round 15b Task 5: with a Compose host mounted this now
     * opens the document QUICK sheet over the reading view (spec §4.5) instead of starting the full
     * `ChooseDocument` screen — which the sheet's own footer row still reaches. Null host = the
     * full-screen path, unchanged below.
     *
     * The reroute lives HERE rather than at the toolbar callback so there is exactly ONE conditional
     * and one classic fall-through for this entry point, and so any later caller of this internal
     * entry point gets the sheet too.
     */
    internal fun composeChooseDocument() {
        val host = activity.composeReadingViewHost
        if (host != null) {
            host.showDocumentSheet()
            return
        }
        activity.startActivityForResult(ScreenLauncher.intentFor(activity, Screen.ChooseDocument), STD_REQUEST_CODE)
    }

    /**
     * Apply a document chosen by the user to the active window.
     *
     * Extracted from the `ChooseDocument` `onActivityResult` arm so that round 15b's document quick
     * sheet — which returns no Intent and therefore cannot use that arm — and the existing activity
     * result cannot drift apart. The `FakeBookFactory` fallback is the reason: it only matters for
     * pseudo-documents, so a second copy could lose it and nothing would notice.
     */
    internal fun applyChosenDocument(bookStr: String?) {
        val book = Books.installed().getBook(bookStr) ?: FakeBookFactory.pseudoDocuments.first { it.initials == bookStr }
        documentControl.changeDocument(book)
        activity.updateActions()
    }

    /**
     * Apply a chosen verse to the active window.
     *
     * Extracted from the `onActivityResult` `in classes` arm so that round 15b's grid quick sheet —
     * which returns no Intent and therefore cannot use that arm — and the existing activity result
     * cannot drift apart. The `NoSuchVerseException` branch is the reason: it only fires on a
     * malformed OSIS id, so a second copy could lose it and nothing would notice.
     */
    internal fun applyChosenVerse(verseStr: String, isFromBookmark: Boolean = false) {
        val verse = try {
            VerseFactory.fromString(navigationControl.versification, verseStr)
        } catch (e: NoSuchVerseException) {
            ABEventBus.post(ToastEvent(activity.getString(R.string.verse_not_found)))
            return
        }
        val pageManager = windowControl.activeWindowPageManager
        if (isFromBookmark && !pageManager.isBibleShown) {
            pageManager.setCurrentDocumentAndKey(windowControl.defaultBibleDoc(false), verse)
        } else {
            pageManager.currentPage.setKey(verse, !isFromBookmark)
        }
    }

    /**
     * Apply a general-book / map / dictionary key chosen by the user to the active window.
     *
     * Extracted from the `in genBookClasses` `onActivityResult` arm so that round 15b's key-chooser
     * quick sheets — which return no Intent and therefore cannot use that arm — and the existing
     * activity result cannot drift apart. [book] is passed explicitly rather than derived from
     * [key] because it cannot be: an EPUB table-of-contents entry is a `BookAndKey` carrying its
     * OWN document, which is not the page's current document, while every other key carries none.
     */
    internal fun applyChosenGenBookKey(book: Book?, key: Key) {
        windowControl.activeWindowPageManager.setCurrentDocumentAndKey(book, key)
    }

    internal fun composeCycleStrongs() {
        val prefOptions = activity.dummyStrongsPrefOption
        prefOptions.value = (prefOptions.value as Int + 1) % 3
        prefOptions.handle()
        activity.updateStrongsButton()
        activity.composeReadingViewHost?.refreshHostedState()
    }

    internal fun composeStrongsLong() {
        val prefOptions = activity.dummyStrongsPrefOption
        fun apply() {
            prefOptions.handle()
            activity.updateStrongsButton()
            activity.composeReadingViewHost?.refreshHostedState()
        }
        prefOptions.openDialog(activity, onChanged = { apply() }, onReset = { apply() })
    }

    /** @param anchor the Compose toolbar's ComposeView (classic `bibleButton` is inside the now-GONE `toolbarLayout` on this path). */
    internal fun composeBibleClick(anchor: View) {
        if (CommonUtils.settings.getString("toolbar_button_actions", "default")?.startsWith("swap-") == true) {
            activity.setCurrentDocument(documentControl.suggestedBible)
        } else {
            activity.menuForDocs(anchor, documentControl.biblesForVerse)
        }
    }

    /**
     * Nav-graph slice 7 Task 2: the `swap-menu` branch opens the Compose quick-doc menu (the same
     * `openBibleQuickDoc` seam the non-swap SHORT press already used), not the native `menuForDocs`
     * `PopupMenu` — so this no longer needs a `View` to anchor on.
     */
    internal fun composeBibleLongClick() {
        if (CommonUtils.settings.getString("toolbar_button_actions", "default") == "swap-menu") {
            activity.composeReadingViewHost?.openBibleQuickDoc(composeQuickDocItems(documentControl.biblesForVerse))
        } else {
            activity.startDocumentChooser("BIBLE")
        }
    }

    /** @param anchor the Compose toolbar's ComposeView (classic `commentaryButton` is inside the now-GONE `toolbarLayout` on this path). */
    internal fun composeCommentaryClick(anchor: View) {
        if (CommonUtils.settings.getString("toolbar_button_actions", "default")?.startsWith("swap-") == true) {
            activity.setCurrentDocument(documentControl.suggestedCommentary)
        } else {
            activity.menuForDocs(
                anchor,
                documentControl.commentariesForVerse
                    + SwordDocumentFacade.getBooks(BookCategory.GENERAL_BOOK)
                    + SwordDocumentFacade.getBooks(BookCategory.DICTIONARY)
            )
        }
    }

    /** [composeBibleLongClick]'s counterpart; same slice 7 Task 2 change. */
    internal fun composeCommentaryLongClick() {
        if (CommonUtils.settings.getString("toolbar_button_actions", "default") == "swap-menu") {
            // Mirrors classic `commentaryLongPress` exactly: unlike `commentaryClick`/
            // `composeCommentaryClick`, the long-press menu does NOT append
            // GENERAL_BOOK/DICTIONARY books.
            activity.composeReadingViewHost?.openCommentaryQuickDoc(composeQuickDocItems(documentControl.commentariesForVerse))
        } else {
            activity.startDocumentChooser("COMMENTARY")
        }
    }

    /** Compose-path quick-doc dispatch: returns the popup items to show (or empty if it switched
     *  directly / had nothing). Mirrors classic `menuForDocs` (:1905-1924) via `QuickDocPicker`. */
    internal fun composeQuickDocItems(books: List<Book>): List<QuickDocMenuItem> {
        val byId = books.associateBy { it.initials }
        val rows = books.map {
            QuickDocRow(
                it.initials,
                activity.getString(R.string.something_with_parenthesis, it.abbreviation, it.language.code),
                it.language.code,
                it.abbreviation,
                category = docCategoryOf(it.bookCategory),
            )
        }
        return when (val a = QuickDocPicker.action(rows, activity.currentDocument?.initials ?: "")) {
            is QuickDocAction.None -> emptyList()
            is QuickDocAction.SwitchDirectly -> { activity.setCurrentDocument(byId[a.id]); emptyList() }
            is QuickDocAction.ShowPopup -> { composeQuickDocBooksById = byId; a.items }
        }
    }

    /** id -> Book for the currently-open compose quick-doc menu (resolves `onSelect`). */
    private var composeQuickDocBooksById: Map<String, Book> = emptyMap()

    /** Compose quick-doc menu selection -> set the doc (mirrors classic `menuForDocs`' click listener). */
    internal fun composeQuickDocSelect(id: String) { activity.setCurrentDocument(composeQuickDocBooksById[id]) }

    // ---- Compose window-tab rail bridge (Batch 12b follow-on, Plan A Task 7) ----
    // `windowLabelFor`/`windowTopLabelFor`/`windowIconFor` resolve a `ComposeReadingViewHost`-supplied
    // opaque window id to the live `Window` and mirror classic `SplitBibleArea.getWindowButtonTitleText` /
    // `WindowButtonWidget.updateSettings`'s `docType` image (`WindowButtonWidget.kt:75-151`) — the
    // label/top-label/icon shown per tab in the Compose `WindowTabBar` (Task 5). Deliberately plain
    // (non-`@Composable`) functions: `WindowTabBar`'s `windowLabel`/`windowTopLabel`/`windowIcon`
    // parameters are plain lambda types (not `@Composable` ones), so nothing in this call chain may
    // invoke a composable (e.g. `painterResource`) — `windowIconFor` builds its `Painter` via
    // `BitmapPainter` instead, which needs no composition context.

    /** Cache of resource id -> [Painter], since the doc-type icon set is small and fixed (one per [BookCategory]). */
    private val composeWindowIconCache = mutableMapOf<Int, Painter>()

    /** Short per-window label for the Compose restore rail — mirrors classic `getWindowButtonTitleText`. */
    internal fun windowLabelFor(id: String): String {
        val window = windowControl.windowRepository.getWindow(IdType(id)) ?: return ""
        return try {
            val curdoc = window.pageManager.currentPage.currentDocument ?: return " "
            if (curdoc.isStudyPad) {
                (window.pageManager.currentPage.key as? StudyPadKey)?.name ?: " "
            } else {
                curdoc.abbreviation
            }
        } catch (e: Exception) { " " }
    }

    /**
     * The rail button's tiny top row: classic `topButtonText`, i.e. `pageManager.titleText`
     * (`WindowButtonWidget.kt:148`). Returns `null` — meaning "render no top row" — for an unknown
     * window or when the page has no title, matching classic's `?: ""` + `View.GONE` handling
     * (`WindowButtonWidget.kt:148-149`).
     *
     * Deliberately NOT part of `WindowSnapshot`: this is a rendering label, not window state, and
     * the snapshot is the iOS-facing model.
     */
    internal fun windowTopLabelFor(id: String): String? {
        val window = windowControl.windowRepository.getWindow(IdType(id)) ?: return null
        return try {
            window.pageManager.titleText.takeIf { it.isNotBlank() }
        } catch (e: Exception) { null }
    }

    /** Doc-type icon for the Compose restore rail — mirrors classic `docType.setImageResource(document.imageResource)`. */
    internal fun windowIconFor(id: String): Painter? {
        val window = windowControl.windowRepository.getWindow(IdType(id)) ?: return null
        val resId = window.pageManager.currentPage.currentDocument?.imageResource ?: return null
        return composeWindowIconCache.getOrPut(resId) {
            val drawable = ContextCompat.getDrawable(activity, resId) ?: return null
            BitmapPainter(drawable.toBitmap().asImageBitmap())
        }
    }

    private fun getItemOptions(itemId: Int, order: Int = 0): OptionsMenuItemInterface {
        val settingsBundle = SettingsBundle(
            level = SettingsLevel.WORKSPACE,
            workspaceId = windowControl.windowRepository.id,
            workspaceName = windowControl.windowRepository.name,
            workspaceSettings = windowControl.windowRepository.textDisplaySettings.apply {
                colors?.workspaceColor = windowControl.windowRepository.workspaceSettings.workspaceColor
            },
            globalSettings = CommonUtils.globalTextDisplaySettings,
        )
        return when(itemId) {
            R.id.allTextOptions -> CommandPreference(launch = { _, _, _ ->
                activity.startActivity(TextDisplaySettingsComposeActivity.intentFor(
                    activity, SettingsScope.Workspace(windowControl.windowRepository.id.toString())))
            }, opensDialog = true)
            R.id.autoAssignLabels -> AutoAssignPreference(windowControl.windowRepository.workspaceSettings)
            R.id.textOptionsSubMenu -> SubMenuPreference(false)
            R.id.textOptionItem -> getPrefItem(settingsBundle, CommonUtils.lastDisplaySettingsSorted[order])
            R.id.splitMode -> SplitModePreference(activity)
            R.id.autoPinMode -> WindowPinningPreference()
            R.id.tiltToScroll -> TiltToScrollPreference(activity)
            R.id.nightMode -> NightModePreference { activity.refreshIfNightModeChange() }
            R.id.fullscreen -> CommandPreference(launch = { _, _, _ ->
                activity.fullScreen = true
            })
            R.id.switchToWorkspace -> CommandPreference(launch = { _, _, _ ->
                // M1 (whole-branch review fix wave): guard on the MOUNTED HOST. The host is
                // mounted by `setupUi`, so it is null until then and null in any state where the
                // reading view is not up; History (MenuCommandHandler.kt / this file's
                // long-press-back) already guards the same way. The alternative this replaced was a
                // live settings read, which could disagree with what is actually on screen.
                val host = activity.composeReadingViewHost
                if (host != null) {
                    host.showWorkspaceSheet()
                } else {
                    val intent = ScreenLauncher.intentFor(activity, Screen.WorkspaceSelector)
                    activity.startActivityForResult(intent, MainBibleActivity.WORKSPACE_CHANGED)
                }
            }, opensDialog = true)
            R.id.llmActionsSubMenu -> CommandPreference(
                launch = { _, _, _ ->
                    val selection = Selection(
                        bookInitials = null,
                        startOrdinal = -1,
                        startOffset = null,
                        endOrdinal = -1,
                        endOffset = null,
                        bookmarks = emptyList(),
                    )
                    activity.composeReadingViewHost?.showPromptSelector(selection, PromptContext.WORKSPACE_MENU, null)
                },
                visible = CommonUtils.settings.llmConfigured,
                opensDialog = true,
            )
            else -> throw RuntimeException("Illegal menu item")
        }
    }

    /**
     * Builds the Compose reading-view toolbar's overflow menu item list — the Compose counterpart
     * of the deleted native `showOptionsMenu`'s build loop, delegated to
     * [OptionsMenuStateBuilder.build]. Exists as a
     * thin bridge (rather than widening [getItemOptions] itself) so the private method's access
     * stays unchanged; see [OptionsMenuStateBuilder]'s kdoc. Called by
     * [net.bible.android.view.activity.page.screen.ComposeReadingViewHost] when the overflow
     * button is tapped, and again after [handleOptionsMenuItem] flips a toggle (to reflect the new
     * checked state).
     */
    fun buildOptionsMenuItems(): List<OptionsMenuItem> =
        OptionsMenuStateBuilder.build { resId, order -> getItemOptions(resId, order) }

    /**
     * Dispatches a click on one of [buildOptionsMenuItems]' rows — the Compose counterpart of
     * the deleted native `handlePrefItem`, delegated to [OptionsMenuStateBuilder.dispatch]. Returns whether the
     * overflow menu should stay open (see that function's kdoc): `true` for a boolean toggle
     * (the host rebuilds the list to show the flipped check), `false` once a dialog/activity/
     * action has been launched (the host closes the menu).
     */
    fun handleOptionsMenuItem(id: String): Boolean =
        OptionsMenuStateBuilder.dispatch(activity, { resId, order -> getItemOptions(resId, order) }, id)

    /**
     * Dispatches a click on one of the Compose drawer's rows — through the SAME
     * [MenuCommandHandler.handleMenuRequest] the classic `NavigationView`'s
     * `setNavigationItemSelectedListener` (`setupUi`, ~564) calls, so every row's command behaviour
     * is classic's by construction. Closing the drawer is the Compose side's own business (the
     * host clears its open-request before calling this), mirroring that listener's `closeDrawers()`.
     */
    internal fun handleDrawerItemClick(itemId: Int) {
        mainMenuCommandHandler.handleMenuRequest(itemId)
    }

    // ---- Compose per-window (☰) pane menu bridge (Batch 12b-followon Plan B Task 5) ----
    // Dispatches a click on one of `WindowPaneMenuStateBuilder.build(window)`'s rows — the
    // Compose per-window menu's counterpart of classic `SplitBibleArea.handlePrefItem`/
    // `getItemOptions(window, itemId, order)` (`screen/SplitBibleArea.kt:709-726, 865-1065`).
    // `SplitBibleArea.getItemOptions`/`handlePrefItem` are `private` and close over their own
    // `View`/`MenuItem`, so — exactly like `WindowPaneMenuStateBuilder`'s build half — there is no
    // clean seam to call them from here; every bridged branch below reproduces the classic action
    // body verbatim (a `SplitBibleArea.kt:NNN` comment marks each one).

    /**
     * Dispatches a click on one of [WindowPaneMenuStateBuilder.build]'s rows for the pane whose ☰
     * menu produced it. Called by [ComposeReadingViewHost]'s `onPaneMenuItemClick`.
     *
     * ATOMIC items (native-in-Compose, per [WindowPaneMenuStateBuilder]'s kdoc) act through
     * [composeReadingViewHost]'s [ReadingViewController] command seam — the SAME seam the pane
     * overlay's own gestures and the restore rail already drive, so every window mutation from
     * the Compose reading view refreshes the SSOT through one path. Falls back to [windowControl]
     * directly only if the host is somehow absent (defensive — this dispatcher is only reachable
     * from the Compose path, where [composeReadingViewHost] is always installed; this fallback is
     * what lets this function be unit-tested without booting the full Compose host) or for
     * `changeToNormal`'s compound add+flag+close (the seam has no such compound command, so it
     * always goes straight through [windowControl], same as classic).
     *
     * Returns whether the menu should stay open (mirrors [OptionsMenuStateBuilder.dispatch]'s
     * contract): `true` for [WindowPaneMenuStateBuilder.ID_PIN_MODE] and a boolean
     * `textOptionItem` row (so the host can rebuild the list and show the flipped check), `false`
     * for every other item.
     */
    fun handleWindowPaneMenuItem(windowId: String, id: String): Boolean {
        val window = windowControl.windowRepository.getWindow(IdType(windowId)) ?: return false
        val controller = activity.composeReadingViewHost?.controller
        return when (val parsed = WindowPaneMenuStateBuilder.parseId(id)) {
            is WindowPaneMenuStateBuilder.ParsedId.MoveItem -> {
                // SplitBibleArea.kt:896-898, :987-992
                controller?.onMove(windowId, parsed.order) ?: windowControl.moveWindow(window, parsed.order)
                false
            }
            is WindowPaneMenuStateBuilder.ParsedId.SyncGroupItem -> {
                // SplitBibleArea.kt:899-901, :993-996
                controller?.onChangeSyncGroup(windowId, parsed.order) ?: windowControl.changeSyncGroup(window, parsed.order)
                false
            }
            is WindowPaneMenuStateBuilder.ParsedId.TextOptionItem -> handleWindowTextOptionItem(window, parsed.order)
            // SplitBibleArea.kt:1005-1007. A/B batch 4a F4: the target window comes from the menu
            // row's own order now (classic's shape), not from a picker dialog.
            is WindowPaneMenuStateBuilder.ParsedId.CopySettingsToWindow -> {
                windowControl.copySettingsToWindow(window, parsed.order)
                false
            }
            is WindowPaneMenuStateBuilder.ParsedId.StaticItem -> handleWindowPaneStaticItem(window, parsed.id, controller)
        }
    }

    /** The `moveWindowSubMenu`/`syncGroupSubMenu`/`textOptionsSubMenu` container ids never reach
     * here — [net.bible.sharedui.reading.WindowPaneMenuRows] treats a non-empty `submenu` row as
     * "enter submenu" (`onEnterSubmenu`), not a leaf click (`onItemClick`). */
    private fun handleWindowPaneStaticItem(window: Window, id: String, controller: ReadingViewController?): Boolean {
        val windowId = window.id.toString()
        return when (id) {
            // SplitBibleArea.kt:880-883
            WindowPaneMenuStateBuilder.ID_WINDOW_NEW -> {
                controller?.onAddWindow(windowId) ?: windowControl.addNewWindow(window)
                false
            }
            // SplitBibleArea.kt:974-977
            WindowPaneMenuStateBuilder.ID_WINDOW_MAXIMISE -> {
                controller?.onMaximise(windowId) ?: windowControl.maximiseWindow(window)
                false
            }
            // SplitBibleArea.kt:970-973
            WindowPaneMenuStateBuilder.ID_WINDOW_MINIMISE -> {
                controller?.onMinimise(windowId) ?: windowControl.minimiseWindow(window)
                false
            }
            // SplitBibleArea.kt:884-890 -- compound (add a new non-links window, close this one);
            // no single seam command for this, so it always goes directly through windowControl,
            // same as classic (the "or directly windowControl" case named in the Task-5 brief).
            WindowPaneMenuStateBuilder.ID_CHANGE_TO_NORMAL -> {
                windowControl.addNewWindow(window).also { it.isLinksWindow = false }
                windowControl.closeWindow(window)
                false
            }
            // SplitBibleArea.kt:891-895 -- checkable toggle; stays open so the host can rebuild
            // and show the flipped check, exactly like `OptionsMenuStateBuilder.dispatch`'s
            // boolean branch.
            WindowPaneMenuStateBuilder.ID_PIN_MODE -> {
                val newValue = !window.isPinMode
                controller?.onSetPin(windowId, newValue) ?: windowControl.setPinMode(window, newValue)
                true
            }
            // SplitBibleArea.kt:997-999
            WindowPaneMenuStateBuilder.ID_DISABLE_SYNC -> {
                controller?.onSetSynchronised(windowId, false) ?: windowControl.setSynchronised(window, false)
                false
            }
            // SplitBibleArea.kt:906-909
            WindowPaneMenuStateBuilder.ID_WINDOW_CLOSE -> {
                controller?.onClose(windowId) ?: windowControl.closeWindow(window)
                false
            }

            // ---- Bridge rows: reproduce the classic action body verbatim (private in SplitBibleArea) ----

            // SplitBibleArea.kt:865-874 (settingsBundle), :978-986 (window-level allTextOptions --
            // distinct from this activity's OWN workspace-level `getItemOptions(R.id.allTextOptions)`
            // used by the overflow menu).
            WindowPaneMenuStateBuilder.ID_ALL_TEXT_OPTIONS -> {
                activity.startActivity(TextDisplaySettingsComposeActivity.intentFor(
                    activity, SettingsScope.Window(window.id.toString(), windowControl.windowRepository.id.toString())))
                false
            }
            // SplitBibleArea.kt:1002-1004
            WindowPaneMenuStateBuilder.ID_COPY_SETTINGS_TO_WORKSPACE -> {
                windowControl.copySettingsToWorkspace(window)
                false
            }
            // SplitBibleArea.kt:1008-1010
            WindowPaneMenuStateBuilder.ID_COPY_SETTINGS_TO_GLOBAL -> {
                windowControl.copySettingsToGlobal(window)
                false
            }
            // SplitBibleArea.kt:1011-1019
            WindowPaneMenuStateBuilder.ID_EXPORT_HTML -> {
                window.bibleView?.exportHtml()
                false
            }
            // SplitBibleArea.kt:1020-1026
            WindowPaneMenuStateBuilder.ID_EXPORT_STUDYPAD -> {
                (window.bibleView?.firstDocument as? StudyPadDocument)?.label?.let { label ->
                    activity.lifecycleScope.launch { exportStudyPads(activity, label) }
                }
                false
            }
            // SplitBibleArea.kt:1027-1035
            WindowPaneMenuStateBuilder.ID_EXPORT_STUDYPAD_CSV -> {
                (window.bibleView?.firstDocument as? StudyPadDocument)?.label?.let { label ->
                    activity.lifecycleScope.launch {
                        val bookmarks = bookmarkControl.getBibleBookmarksWithLabel(label)
                        bookmarkControl.exportBookmarksToCSV(activity, bookmarks)
                    }
                }
                false
            }
            // SplitBibleArea.kt:1036-1048
            WindowPaneMenuStateBuilder.ID_ADD_WHOLE_PAGE_BOOKMARK -> {
                val currentPage = window.pageManager.currentPage
                val book = currentPage.currentDocument
                val key = currentPage.key
                if (book != null && key != null) {
                    window.bibleView?.createWholePageBookmark(book.initials, key.osisRef)
                }
                false
            }
            // SplitBibleArea.kt:1049-1063
            WindowPaneMenuStateBuilder.ID_LLM_ACTIONS_SUBMENU -> {
                val currentPage = window.pageManager.currentPage
                val book = currentPage.currentDocument
                val key = currentPage.key
                if (book != null && key != null) {
                    val selection = Selection(book.initials, key.osisRef, -1, -1)
                    // This ☰ pane menu only exists on the Compose path (composeReadingViewHost
                    // installed), so route straight through the host; the classic call is kept as
                    // an `?:` fallback for safety rather than assumed unreachable.
                    activity.composeReadingViewHost?.showPromptSelector(selection, PromptContext.WINDOW_MENU, currentPage.documentCategory)
                        ?: activity.llmDialogHelper.showPromptSelector(selection, PromptContext.WINDOW_MENU, currentPage.documentCategory)
                }
                false
            }
            // SplitBibleArea.kt:950-967
            WindowPaneMenuStateBuilder.ID_COPY_REFERENCE -> {
                val doc = window.pageManager.currentPage.currentDocument
                val key = window.pageManager.currentPage.singleKey
                if (doc != null && key != null) {
                    val ordinalRange = window.pageManager.currentPage.anchorOrdinal
                    val ordinal = ordinalRange?.start
                    clipboardKey = BookAndKey(key, doc, ordinalRange)
                    val url = CommonUtils.makeAndBibleUrl(keyStr = key.osisRef, docInitials = doc.initials, ordinal = ordinal)
                    CommonUtils.copyToClipboard(ClipData.newPlainText(key.name, url), R.string.reference_copied_to_clipboard)
                }
                false
            }
            // SplitBibleArea.kt:929-949
            WindowPaneMenuStateBuilder.ID_GO_TO_REFERENCE -> {
                clipboardKey?.let {
                    if (it.document?.bookCategory == BookCategory.BIBLE && window.pageManager.isVersePageShown) {
                        window.pageManager.setCurrentDocumentAndKey(null, it.key)
                    } else if (it.document == null) {
                        window.pageManager.setCurrentDocumentAndKey(null, it.key)
                    } else {
                        window.pageManager.setCurrentDocumentAndKey(it.document, it)
                    }
                }
                false
            }
            // SplitBibleArea.kt:910-928
            WindowPaneMenuStateBuilder.ID_GO_TO_SPEAK -> {
                speakControl.speakBookAndKey?.let {
                    if (it.document?.bookCategory == BookCategory.BIBLE && window.pageManager.isVersePageShown) {
                        window.pageManager.setCurrentDocumentAndKey(null, it.key)
                    } else {
                        window.pageManager.setCurrentDocumentAndKey(it.document, it)
                    }
                }
                false
            }
            // moveWindowSubMenu/syncGroupSubMenu/textOptionsSubMenu container ids never reach a
            // leaf onItemClick (see this function's kdoc); llmActionsSubMenu is handled above (it
            // has no `submenu` children despite the name -- see WindowPaneMenuStateBuilder).
            else -> false
        }
    }

    /**
     * SplitBibleArea.kt:1000: the dynamic `textOptionItem` row -- classic `handlePrefItem`'s
     * generic isBoolean/openDialog dispatch, reproduced at WINDOW level. Minus the `MenuItem`-only
     * bits (`item.isChecked`, `invalidateOptionsMenu()`): there is no `MenuItem` on this path, and
     * the host rebuilds the whole item list instead (mirrors
     * [OptionsMenuStateBuilder.dispatch]'s identical carve-out).
     *
     * Settings editor sheets T11: the non-boolean branch carries the SAME sheet-vs-dialog
     * interception as [OptionsMenuStateBuilder.dispatch] -- a sheet-editable
     * [Preference.type] opens IN PLACE over the reading view via
     * `composeReadingViewHost.showTextSettingEditor` when the host is installed, reusing the same
     * `onReady` closure `openDialog` would otherwise have received; everything else still calls
     * `openDialog` unchanged. The scope passed is
     * `settingsBundle.toScope()` at WINDOW level, so the sheet edits THIS pane's own setting,
     * matching what the ☰ pane menu means (as opposed to the workspace-level scope
     * [OptionsMenuStateBuilder.dispatch] passes for the 3-dot overflow menu).
     */
    private fun handleWindowTextOptionItem(window: Window, order: Int): Boolean {
        val settingsBundle = SettingsBundle(
            level = SettingsLevel.WINDOW,
            windowId = window.id,
            pageManagerSettings = window.pageManager.textDisplaySettings,
            workspaceId = windowControl.windowRepository.id,
            workspaceName = windowControl.windowRepository.name,
            workspaceSettings = windowControl.windowRepository.textDisplaySettings,
            globalSettings = CommonUtils.globalTextDisplaySettings,
        )
        val itemOptions = getPrefItem(settingsBundle, CommonUtils.lastDisplaySettingsSorted[order])
        return if (itemOptions.isBoolean) {
            itemOptions.value = !(itemOptions.value == true)
            itemOptions.handle()
            true
        } else {
            val onReady: () -> Unit = { window.bibleView?.updateTextDisplaySettings() }
            val host = activity.composeReadingViewHost
            val page = (itemOptions as? Preference)?.let { textSettingEditorPageFor(it.type.name) }
            if (page != null && host != null) {
                // WINDOW-scoped: settingsBundle.toScope() carries level=WINDOW, so the sheet edits
                // this pane's own setting — which is what the pane menu means.
                host.showTextSettingEditor(settingsBundle.toScope(), page, onReady)
                return false
            }
            itemOptions.openDialog(activity, { onReady() }, onReady)
            false
        }
    }

    val bibleOverlayText: String
        get() {
            val bookName = pageControl.currentPageManager.currentPage.currentDocument?.abbreviation
            synchronized(BookName::class.java) {
                val oldValue = BookName.isFullBookName()
                BookName.setFullBookName(false)
                try {
                    return "$bookName:${activity.pageTitleText}"
                } finally {
                    BookName.setFullBookName(oldValue)
                }
            }
        }
}
