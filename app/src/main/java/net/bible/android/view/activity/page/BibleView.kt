/*
 * Copyright (c) 2020-2026 Martin Denham, Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
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

import android.annotation.SuppressLint
import android.app.Activity
import android.app.SearchManager
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.Looper
import android.text.TextUtils
import android.util.LayoutDirection
import net.bible.sharedcore.log.Log
import android.view.ActionMode
import android.view.ContextMenu
import android.view.ContextMenu.ContextMenuInfo
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.webkit.ConsoleMessage
import android.widget.Toast
import android.webkit.ValueCallback
import android.webkit.JsResult
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.annotation.RequiresApi
import androidx.core.view.GestureDetectorCompat
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.lifecycleScope
import net.bible.sharedui.reading.bridge.ReadingBridge
import net.bible.sharedui.reading.bridge.ReadingKeyActions
import net.bible.sharedui.reading.session.BibleWindowSession
import net.bible.sharedui.reading.session.ConfigCommand
import net.bible.sharedui.reading.session.ConfigInputs
import net.bible.sharedui.reading.session.DocumentLoader
import net.bible.sharedui.reading.session.DocumentLoaderPlatform
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewAssetLoader.PathHandler
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Transient
import kotlinx.serialization.serializer
import net.bible.android.activity.R
import net.bible.android.common.toV11n
import net.bible.android.control.PassageChangeMediator
import net.bible.android.control.bookmark.BookmarkControl
import net.bible.android.control.bookmark.BookmarkChange
import net.bible.android.control.progress.ProgressChange
import net.bible.android.control.progress.ProgressControl
import net.bible.service.common.ReadingProgressSettings
import net.bible.sharedcore.reading.ShareVersesInput
import net.bible.sharedui.reading.bridge.shareableSelectionText
import net.bible.sharedui.reading.session.ReadingSheets
import net.bible.service.llm.PromptContext
import net.bible.service.llm.agent.NoteEditorEntityType
import net.bible.android.control.download.DownloadControl
import net.bible.android.control.link.LinkControl
import net.bible.android.control.link.WindowMode
import net.bible.android.control.page.contentJson
import net.bible.android.control.page.BibleDocument
import net.bible.android.control.page.MemorizeDocument
import net.bible.android.control.page.ClientAiDocMarker
import net.bible.android.control.page.ClientBibleBookmark
import net.bible.android.control.page.ClientBookmarkLabel
import net.bible.android.control.page.ClientGenericBookmark
import net.bible.android.control.page.CurrentPageManager
import net.bible.android.control.page.Document
import net.bible.android.control.page.DocumentCategory
import net.bible.android.control.page.ErrorDocument
import net.bible.android.control.page.ErrorSeverity
import net.bible.android.control.page.MyNotesDocument
import net.bible.android.control.page.OrdinalRange
import net.bible.android.control.page.OsisDocument
import net.bible.android.control.page.PageControl
import net.bible.android.control.page.PageTiltScrollControl
import net.bible.android.control.page.StudyPadDocument
import net.bible.android.control.page.window.Window
import net.bible.android.control.page.window.WindowView
import net.bible.android.control.page.window.WindowChange
import net.bible.android.control.page.window.WindowControl
import net.bible.android.control.page.window.WindowStateServiceImpl
import net.bible.android.control.search.SearchControl
import net.bible.android.control.versification.toVerseRange
import net.bible.android.database.IdType
import net.bible.android.database.WorkspaceEntities
import net.bible.android.database.bookmarks.BookmarkEntities
import net.bible.android.database.bookmarks.KJVA
import net.bible.android.control.link.BibleLink
import net.bible.android.database.json
import net.bible.android.misc.wrapString
import net.bible.android.view.activity.base.DocumentView
import net.bible.android.view.activity.base.IntentHelper
import net.bible.android.view.activity.base.SharedActivityState
import net.bible.android.view.activity.bookmark.ManageLabelsContract
import net.bible.android.view.activity.bookmark.updateFrom
import net.bible.android.view.activity.nav.NavHostComposeActivity
import net.bible.android.view.Screen
import net.bible.android.view.ScreenLauncher
import net.bible.android.view.activity.page.screen.PageTiltScroller
import net.bible.android.view.activity.page.screen.clipboardKey
import net.bible.android.view.util.UiUtils
import net.bible.service.sword.SwordContentFacade
import net.bible.android.control.page.window.WorkspaceChange
import net.bible.android.control.page.window.WorkspaceChanges
import net.bible.service.common.CommonUtils
import net.bible.service.common.AndBibleAddons
import net.bible.service.common.AndBibleAddons.fontsByModule
import net.bible.service.common.CommonUtils.parseAndBibleReference
import net.bible.service.db.DatabaseContainer
import net.bible.service.device.ScreenSettings
import net.bible.sharedcore.event.Subscriptions
import net.bible.sharedcore.nav.NavRoutes
import net.bible.sharedui.currentWorkspaceThemeSeedArgb
import net.bible.sharedui.theme.themeColorsJson
import net.bible.service.sword.BookAndKey
import net.bible.service.sword.mydocument.MyDocumentBookManager
import net.bible.service.sword.mydocument.MyDocumentChange
import net.bible.service.sword.SwordDocumentFacade
import net.bible.service.sword.epub.EpubBackend
import net.bible.service.sword.epub.isEpub
import org.crosswire.jsword.book.Book
import org.crosswire.jsword.book.BookCategory
import org.crosswire.jsword.book.Books
import org.crosswire.jsword.book.sword.SwordBook
import okio.buffer
import org.crosswire.jsword.book.sword.SwordGenBook
import org.crosswire.jsword.index.IndexStatus
import org.crosswire.jsword.index.search.SearchType
import org.crosswire.jsword.passage.Key
import org.crosswire.jsword.passage.KeyUtil
import org.crosswire.jsword.passage.NoSuchKeyException
import org.crosswire.jsword.passage.RangedPassage
import org.crosswire.jsword.passage.Verse
import org.crosswire.jsword.passage.VerseRange
import org.crosswire.jsword.versification.BookName
import org.crosswire.jsword.versification.Versification
import org.crosswire.jsword.versification.system.SystemKJVA
import org.crosswire.jsword.versification.system.Versifications
import java.io.File
import java.io.IOException
import java.lang.ref.WeakReference
import java.net.URLConnection
import java.util.*
import kotlin.math.abs
import net.bible.sharedcore.bibleview.BibleViewJsChannel
import net.bible.sharedcore.bibleview.BibleViewEvents
import net.bible.sharedcore.bibleview.JsExpr
import net.bible.sharedcore.bibleview.SetupContent
import net.bible.sharedcore.bibleview.JsCall
import net.bible.sharedcore.bibleview.JsHost
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.core.context.GlobalContext
import net.bible.service.db.blockingAtEdge
import net.bible.android.control.bookmark.BookmarkJsActions

private val notFound = WebResourceResponse(null, null, null)

const val white = -1
const val black = -16777216

/**
 * Resolves the reader background colour for [window] — day/night, per-window/workspace/global
 * colour-scheme, and monochrome aware. Extracted from [BibleView.backgroundColor] (which now
 * delegates here) so [net.bible.android.view.activity.page.screen.ComposeReadingViewHost.paneBackgroundArgbFor]
 * can resolve the SAME colour for a [Window] that has no live [BibleView] yet — a brand-new window
 * created via [net.bible.android.control.page.window.WindowControl.restoreWindow] doesn't get one
 * until its pane's `AndroidView` factory runs, which happens strictly AFTER the pane `Box`'s
 * background modifier is evaluated (A/B batch 4a whole-batch review C1: `paneBackgroundArgbFor`
 * previously read `Window.bibleView?.backgroundColor`, which is exactly `null` in that window —
 * the one case F5 exists to paint).
 *
 * Only depends on [window] (never on `this@BibleView`), so it needs no [BibleView] instance at all.
 */
fun bibleViewBackgroundColorFor(window: Window): Int {
    val colors = window.pageManager.actualTextDisplaySettings.colors
    val monochromeMode = CommonUtils.settings.monochromeMode
    val nightBackground = if (monochromeMode) black else colors?.nightBackground
    val dayBackground = if (monochromeMode) white else colors?.dayBackground
    return (if (ScreenSettings.nightMode) nightBackground else dayBackground) ?: UiUtils.bibleViewDefaultBackgroundColor
}

/** The WebView component that shows the bible and other documents */
@SuppressLint("ViewConstructor")
class BibleView(
                /**
                 * R6a: R4's narrow reading-host interface, not the Activity. The
                 * `MainBibleActivity`-only members this class used to reach through it are in
                 * [hostCallbacks]; everything else here is chrome the interface owns, a plain
                 * `Context` ([ReadingHostActivity.hostContext]) or `LifecycleOwner`.
                 */
                val host: ReadingHostActivity,
                /** See [BibleViewHostCallbacks]. Also read by this view's [BibleJavascriptInterface]. */
                val hostCallbacks: BibleViewHostCallbacks,
                initialWindowRef: WeakReference<Window>,
                internal val windowControl: WindowControl,
                private val pageControl: PageControl,
                private val pageTiltScrollControl: PageTiltScrollControl,
                internal val linkControl: LinkControl,
                internal val bookmarkControl: BookmarkControl,
                internal val downloadControl: DownloadControl,
                private val searchControl: SearchControl
) : WebView(host.hostContext.applicationContext), DocumentView, WindowView
{
    private lateinit var bibleJavascriptInterface: BibleJavascriptInterface
    private val swordContentFacade: SwordContentFacade by lazy { GlobalContext.get().get() }
    private val bookmarkJsActions: BookmarkJsActions by lazy { GlobalContext.get().get() }

    private lateinit var pageTiltScroller: PageTiltScroller
    private var hideScrollBar: Boolean = false
    private var nativeScrollbarState: NativeScrollbarState? = null

    private class NativeScrollbarState(view: WebView) {
        val vertical = view.isVerticalScrollBarEnabled
        val horizontal = view.isHorizontalScrollBarEnabled
    }

    /** MONO hides native chrome on every API; retaining the original flags makes exit reversible. */
    private fun updateNativeScrollbars() {
        if (!CommonUtils.settings.pureMonochromeMode) {
            val saved = nativeScrollbarState ?: return
            isVerticalScrollBarEnabled = saved.vertical
            isHorizontalScrollBarEnabled = saved.horizontal
            nativeScrollbarState = null
            return
        }
        if (nativeScrollbarState == null) nativeScrollbarState = NativeScrollbarState(this)
        isVerticalScrollBarEnabled = false
        isHorizontalScrollBarEnabled = false
        // Leave drawables, fading and geometry untouched; autoscroll retains its own range policy.
    }

    private var wasAtRightEdge: Boolean = false
    private var wasAtLeftEdge: Boolean = false

    private var minChapter: Int
        get() = session.minChapter
        set(value) { session.minChapter = value }
    private var maxChapter: Int
        get() = session.maxChapter
        set(value) { session.maxChapter = value }

    private var gestureDetector: GestureDetectorCompat

    /** Used to prevent scroll off bottom using auto-scroll
     * see http://stackoverflow.com/questions/5069765/android-webview-how-to-autoscroll-a-page
     */
    //TODO get these once, they probably won't change
    private val maxVerticalScroll: Int
        get() = computeVerticalScrollRange() - computeVerticalScrollExtent()

    private val maxHorizontalScroll: Int
        get() = computeHorizontalScrollRange() - computeHorizontalScrollExtent()

    private val gestureListener  = BibleGestureListener(host, this,
        onNext = { hostCallbacks.onNext() }, onPrevious = { hostCallbacks.onPrevious() })

    private var toBeDestroyed = false

    private inner class WebViewJsHost : JsHost {
        override val isMainThread: Boolean get() = Looper.myLooper() == Looper.getMainLooper()
        override val isAttached: Boolean get() = isAttachedToWindow
        override val mainDispatcher: CoroutineDispatcher = Dispatchers.Main
        override fun post(block: () -> Unit) { this@BibleView.post { block() } }
        override fun evaluate(js: String, callback: ((String) -> Unit)?) {
            evaluateJavascript(js, callback?.let { cb -> ValueCallback<String> { cb(it) } })
        }
    }

    internal val jsChannel = BibleViewJsChannel(WebViewJsHost()) { Log.e(TAG, it) }

    /** Platform-independent session state; [window], [firstDocument] etc. below forward to it. */
    val session: BibleWindowSession = BibleWindowSession(
        window = initialWindowRef.get(),
        jsChannel = jsChannel,
        hostScope = { host.lifecycleScope },
        surface = BibleViewSurface(this),
        navigator = AndroidReadingNavigator(
            host = { hostCallbacks.hostActivity },
            downloadControl = { downloadControl },
            workspaceSettings = { windowControl.windowRepository.workspaceSettings },
        ),
        windowControlProvider = { windowControl },
        bookmarkControlProvider = { bookmarkControl },
        linkControlProvider = { linkControl },
        downloadControlProvider = { downloadControl },
        searchControlProvider = { searchControl },
        pageControlProvider = { pageControl },
    ).also {
        it.keyActions = object : ReadingKeyActions {
            override fun openDrawer() {
                if (!hostCallbacks.composeOpenDrawerIfHosted()) hostCallbacks.openDrawerAndFocusIt()
            }

            override fun openOverflowMenu() {
                hostCallbacks.composeReadingViewHost()?.openOverflowMenu()
            }

            override fun showWorkspaceSheetIfHosted(): Boolean {
                val readingHost = hostCallbacks.composeReadingViewHost() ?: return false
                readingHost.showWorkspaceSheet()
                return true
            }

            override fun copySelection() = copySelectionToClipboard()

            override fun searchIfHosted(): Boolean =
                hostCallbacks.composeSearchIfHosted(/* seedQuery = */ null, /* preDecorated = */ false)

            override fun crashAllBibleViews() = hostCallbacks.crashAllBibleViews()
        }
        it.hostSheets = object : ReadingSheets {
            override fun showReadHistory(bookId: String, chapter: Int) {
                hostCallbacks.composeReadingViewHost()?.showReadHistorySheet(bookId, chapter)
            }

            override fun showCompare(range: VerseRange) {
                linkControl.openCompare(range)
            }

            override fun showShare(input: ShareVersesInput) {
                hostCallbacks.composeReadingViewHost()?.showShareSheet(input)
            }

            override fun showPromptSelector(selection: Selection, context: PromptContext, category: DocumentCategory?) {
                hostCallbacks.composeReadingViewHost()?.showPromptSelector(selection, context, category)
            }

            override fun showRegenerate(pageId: IdType) {
                hostCallbacks.showRegenerate(pageId, this@BibleView)
            }

            override fun showHelp(title: String?, html: String) {
                hostCallbacks.composeReadingViewHost()?.showHelp(title, html)
            }

            override fun showDeleteDocumentPageConfirm(onConfirm: () -> Unit) {
                hostCallbacks.composeReadingViewHost()?.showDeleteDocumentPageConfirm(onConfirm)
            }
        }
        it.verseChooser = { hostCallbacks.composeReadingViewHost()?.openVerseChooserSheetForResult()?.await() }
    }

    private val loaderPlatform = object : DocumentLoaderPlatform {
        override val lastUpdated: Long get() = this@BibleView.lastUpdated
        override val topOffset: Float get() = this@BibleView.topOffset
        override val bottomOffset: Float get() = this@BibleView.bottomOffset
        override val hasActiveIndicator: Boolean get() = this@BibleView.hasActiveIndicator
        override fun configInputs(favouriteLabelIds: List<IdType>) = this@BibleView.configInputs(favouriteLabelIds)
        override fun isCurrentView() = window.bibleView === this@BibleView
        override fun enableZoomForMap(isMap: Boolean) = this@BibleView.enableZoomForMap(isMap)
    }

    /** Document loading, chapter paging and `set_config` (common code); the bridge reaches it via `session.paging`. */
    val documentLoader = DocumentLoader(session, loaderPlatform).also { session.paging = it }

    /** The platform-independent JS bridge; [BibleJavascriptInterface] delegates the moved families to it. */
    val readingBridge = ReadingBridge(session, viewIsCurrent = { window.bibleView === this })

    internal val windowRef: WeakReference<Window> get() = WeakReference(session.windowOrNull)

    var window: Window
        get() = session.window
        set(value) { session.window = value }

    init {
        //if ((0 != BibleApplication.application.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) || CommonUtils.isBeta) {
            setWebContentsDebuggingEnabled(true)
        //}
        gestureDetector = GestureDetectorCompat(context, gestureListener)
        setOnTouchListener { v, event ->
            if (gestureDetector.onTouchEvent(event)) {
                true
            } else v.performClick()
        }
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            defaultFocusHighlightEnabled = false
        }
        setOnLongClickListener(BibleViewLongClickListener())
        updateNativeScrollbars()
    }

    private var step2 = false

    private fun onActionMenuItemClicked(mode: ActionMode, item: MenuItem): Boolean {
        return when(item.itemId) {
            R.id.add_bookmark -> {
                findViewTreeLifecycleOwner()
                val cat = currentSelection?.book?.bookCategory
                if(cat != null && cat != BookCategory.BIBLE) {
                    makeBookmark()
                    mode.finish()
                } else {
                    step2 = true
                    mode.menu.clear()
                    mode.invalidate()
                }
                return false
            }
            R.id.add_bookmark_selection -> {
                makeBookmark()
                mode.finish()
                return true
            }
            R.id.add_bookmark_whole_verse -> {
                makeBookmark(wholeVerse = true)
                mode.finish()
                return true
            }
            R.id.add_paragraph_break -> {
                addParagraphBreakBookmark()
                mode.finish()
                return true
            }
            R.id.compare -> {
                compareSelection()
                mode.finish()
                return true
            }
            R.id.memorize -> {
                memorizeSelection()
                mode.finish()
                return true
            }
            R.id.share_verses -> {
                val sel = currentSelection ?: return true
                hostCallbacks.composeReadingViewHost()?.showShareSheet(swordContentFacade.buildShareVersesInput(sel))
                return true
            }
            R.id.open_ref -> {
                val ref = currentSelectionRef
                if(ref != null) { linkControl.showLink(null,  ref) }
                return true
            }
            R.id.copy -> {
                surface.copyText(application.getString(R.string.add_bookmark3), currentSelectionText ?: "")
                return true
            }
            R.id.web_search -> {
                if (currentSelectionText != null) { surface.openWebSearch(currentSelectionText!!) }
                return true
            }
            R.id.lookup_dictionary -> {
                val text = currentSelectionText
                if (text != null) {
                    linkControl.lookupInDictionaries(text)
                }
                return true
            }
            R.id.llm_action -> {
                val sel = currentSelection
                if (sel != null) {
                    hostCallbacks.showLlmPromptSelector(sel, PromptContext.VERSE_SELECTION)
                }
                mode.finish()
                return true
            }
            R.id.search -> {
                val text = currentSelectionText
                val sel = currentSelection
                val selText = sel?.text?:text?:return true
                val currentBible = currentPageManager.currentBible.currentDocument ?: return true
                // Section-less decorated query for highlighting result previews (mirrors Search.kt).
                val highlightText = searchControl.highlightSearchString(selText, SearchType.PHRASE)
                val searchText = searchControl.decorateSearchString(
                    selText,
                    SearchType.PHRASE,
                    SearchControl.SearchBibleSection.ALL,
                    ""
                )

                // F6 Task 8b entry point 7: retarget into the reading view's search when a Compose
                // host is mounted. `searchText` is ALREADY decorated (PHRASE + no section term) —
                // `preDecorated = true` tells the host to re-decorate with identity decorators
                // instead of the live settings-sheet word-mode/section, reproducing this exact
                // string rather than decorating it twice (see
                // `ComposeReadingViewHost.openSearch`'s kdoc). Classic behaviour unchanged otherwise.
                if (hostCallbacks.composeSearchIfHosted(searchText, /* preDecorated = */ true)) {
                    return true
                }

                // nav-graph slice 5/Task 6: the search cluster lives in the graph now, so the
                // screen's arguments travel IN the route. `ScreenLauncher.intentFor(..., Screen
                // .SearchIndex)` resolves to the nav host with an ARGUMENT-FREE route, and the
                // three-key bundle this used to putExtras() would land nowhere (the host reads
                // only EXTRA_ROUTE) -- see NavHostRoutingGuardTest
                // .migratedScreenArgumentIsNeverDroppedByAPutExtra. No SELECTED_TRANSLATIONS and
                // no IS_STRONGS_SEARCH here, exactly as the classic bundle carried neither.
                val route = if (currentBible.indexStatus != IndexStatus.DONE) {
                    NavRoutes.searchIndex(
                        searchText = searchText,
                        highlightText = highlightText,
                        searchDocument = currentBible.initials,
                    )
                } else {
                    NavRoutes.searchResults(
                        searchText = searchText,
                        highlightText = highlightText,
                        searchDocument = currentBible.initials,
                    )
                }
                host.hostContext.startActivity(NavHostComposeActivity.intentFor(host.hostContext, route))

                return true
            }
            else -> false
        }
    }

    fun makeBookmark(selection: Selection? = currentSelection, wholeVerse: Boolean = false, openNotes: Boolean = false) =
        readingBridge.bookmarkCreation.makeBookmark(selection, wholeVerse, openNotes)

    fun createWholePageBookmark(bookInitials: String, bookKey: String) =
        readingBridge.bookmarkCreation.createWholePageBookmark(bookInitials, bookKey)

    fun addParagraphBreakBookmark(selection: Selection? = currentSelection) =
        readingBridge.bookmarkCreation.addParagraphBreakBookmark(selection)

    internal fun compareSelection(selection: Selection? = currentSelection) {
        Log.i(TAG, "compareSelection")
        readingBridge.compareSelection(selection)
    }

    internal fun memorizeSelection(selection: Selection? = currentSelection) {
        Log.i(TAG, "memorizeSelection")
        readingBridge.memorizeSelection(selection)
    }

    val scope get() = host.lifecycleScope
    private val bookmarkChanges by lazy { net.bible.sharedcore.platform.OrderedLauncher(scope) }
    private var documentLoadGeneration = 0L

    /** Platform seams (see [AndroidReadingNavigator], [BibleViewSurface]). */
    val navigator get() = session.navigator as AndroidReadingNavigator
    val surface get() = session.surface as BibleViewSurface

    var menuPrepared = false
    var currentSelection: Selection? = null
    var currentSelectionRef: Key? = null
    var currentSelectionText: String? = null

    @RequiresApi(Build.VERSION_CODES.M)
    private fun createProcessTextIntent() = Intent()
        .setAction(Intent.ACTION_PROCESS_TEXT)
        .setType("text/plain")
        .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        .putExtra(Intent.EXTRA_PROCESS_TEXT, currentSelectionText!!)

    @RequiresApi(Build.VERSION_CODES.M)
    private fun getSupportedActivities(): List<ResolveInfo> {
        val packageManager: PackageManager = context.packageManager
        // No filter: AndBible stopped registering itself as a PROCESS_TEXT handler when the
        // SearchResults <activity-alias> was removed (837515385690, 2025-08-21), so there is no
        // longer an own entry to strip out of this list. See spec §9.2 / Appendix F.2.
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.queryIntentActivities(createProcessTextIntent(), PackageManager.ResolveInfoFlags.of(0))
        } else {
            packageManager.queryIntentActivities(createProcessTextIntent(), 0)
        }
    }

    private fun getLabel(resolveInfo: ResolveInfo): CharSequence {
        return resolveInfo.loadLabel(context.packageManager)
    }

    @RequiresApi(Build.VERSION_CODES.M)
    private fun createProcessTextIntentForResolveInfo(info: ResolveInfo) =
        createProcessTextIntent()
            .putExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, true)
            .setClassName(info.activityInfo.packageName, info.activityInfo.name)

    private fun onPrepareActionMenu(mode: ActionMode, menu: Menu): Boolean {
        Log.i(TAG, "onPrepareActionMode $menuPrepared ${currentSelection?.verseRange}")

        if(menuPrepared) {
            mode.menu.clear()
            mode.menuInflater.inflate(R.menu.bibleview_selection, menu)

            val sel = currentSelection

            // For some reason, these do not seem to be correct from XML, even though specified there
            if(isBible && CommonUtils.settings.getBoolean("disable_two_step_bookmarking", false)) {
                menu.findItem(R.id.add_bookmark_selection).run {
                    setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
                    setVisible(true)
                }
                menu.findItem(R.id.add_bookmark_whole_verse).run{
                    setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
                    setVisible(true)
                }
            } else {
                menu.findItem(R.id.add_bookmark).run {
                    setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
                    setVisible(true)
                }
            }

            menu.findItem(R.id.compare).setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
            menu.findItem(R.id.share_verses).setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
            if(sel == null) {
                menu.findItem(R.id.add_bookmark).isVisible = false
                menu.findItem(R.id.add_bookmark_selection).isVisible = false
                menu.findItem(R.id.add_bookmark_whole_verse).isVisible = false
                menu.findItem(R.id.add_paragraph_break).isVisible = false
            }
            if(!isBible) {
                menu.findItem(R.id.compare).isVisible = false
                menu.findItem(R.id.share_verses).isVisible = false
            }
            if (!CommonUtils.settings.addParagraphBreakEnabled) {
                menu.findItem(R.id.add_paragraph_break).isVisible = false
            }

            val ref = currentSelectionRef
            if(ref != null) {
                val item = menu.findItem(R.id.open_ref)
                item.isVisible = true
                synchronized(BookName::class.java) {
                    val wasFullBookName = BookName.isFullBookName()
                    BookName.setFullBookName(false)
                    try {
                        item.title = context.getString(R.string.go_to_ref, ref.getName())
                    } finally {
                        BookName.setFullBookName(wasFullBookName)
                    }
                }
            }
            if(ref == null && currentSelectionText != null) {
                val item = menu.findItem(R.id.search)
                item.isVisible = true
                item.title = if(currentSelectionText!!.length < 16) context.getString(R.string.search_what, currentSelectionText) else context.getString(R.string.search)
            }
            if (currentSelectionText != null) {
                menu.findItem(R.id.web_search).apply {
                    isVisible = true
                }
            }
            if (currentSelectionText != null && SwordDocumentFacade.wordLookupDictionaries(CommonUtils.settings).isNotEmpty()) {
                menu.findItem(R.id.lookup_dictionary).apply {
                    isVisible = true
                }
            }
            if (currentSelectionText != null && CommonUtils.settings.llmConfigured) {
                menu.findItem(R.id.llm_action).apply {
                    isVisible = true
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && currentSelectionText != null) {
                var menuItemOrder = 100
                for (resolveInfo in getSupportedActivities()) {
                    menu.add(Menu.NONE, Menu.NONE,
                        menuItemOrder++,
                        getLabel(resolveInfo))
                        .setIntent(createProcessTextIntentForResolveInfo(resolveInfo))
                        .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
                }
                if(!isBible) {
                    menu.findItem(R.id.copy).isVisible = true
                }
            }

            menuPrepared = false
            return true
        }
        else {
            if(step2) {
                mode.menu.clear()
                mode.menuInflater.inflate(R.menu.bibleview_selection2, menu)
                step2 = false
                return true
            }
            if (editingTextInJs) {
                return true
            } else {
                menu.clear()
                scope.launch {
                    if (setCurrentSelection())
                        menuPrepared = true

                    withContext(Dispatchers.Main) {
                        mode.invalidate()
                    }
                }
                return false
            }
        }
    }

    /** @return true if bibleView.querySelection() result was not null */
    private suspend fun setCurrentSelection(): Boolean = withContext(Dispatchers.Main) {
        val result = evaluateJavascriptAsync("bibleView.querySelection()")
        if (result != "null") {
            val sel = try {
                json.decodeFromString(serializer<Selection?>(), result)
            } catch (e: SerializationException) {
                null
            }
            val selText = try { json.decodeFromString(serializer(), result) } catch (e: SerializationException) { result }
            currentSelection = sel
            currentSelectionText = sel?.text ?: selText
            currentSelectionRef = linkControl.resolveRef(currentSelectionText?: "")
            return@withContext true
        }

        return@withContext false
    }

    /** Copies the current native text selection (Ctrl+C) through the surface's clipboard. */
    fun copySelectionToClipboard() {
        scope.launch {
            currentSelection = null
            setCurrentSelection()
            currentSelection?.let {
                surface.copyText(it.verseRange?.getName(), shareableSelectionText(it, swordContentFacade, CommonUtils.settings))
            }
        }
    }

    var editingTextInJs: Boolean
        get() = session.editingTextInJs
        set(value) { session.editingTextInJs = value }

    fun stopSelection(removeRanges: Boolean = false) {
        currentSelection = null
        currentSelectionText = null
        currentSelectionRef = null
        menuPrepared = false
        if(removeRanges) emit(BibleViewEvents.removeRanges())
    }

    @RequiresApi(Build.VERSION_CODES.M)
    private inner class ActionModeCallback2(val callback: ActionMode.Callback): ActionMode.Callback2() {
        override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
            emit(BibleViewEvents.setActionMode(true))
            val wasUpdated2 = callback.onCreateActionMode(mode, menu)
            val wasUpdated1 = false
            return wasUpdated1 || wasUpdated2
        }

        override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
            val wasUpdated2 = callback.onPrepareActionMode(mode, menu)
            val wasUpdated1 = onPrepareActionMenu(mode, menu)
            return wasUpdated1 || wasUpdated2
        }

        override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
            if(editingTextInJs) {
                val rv = callback.onActionItemClicked(mode, item)
                mode.finish()
                return rv
            }
            val handled = onActionMenuItemClicked(mode, item)
            if(handled) stopSelection(true)
            return handled
        }

        override fun onDestroyActionMode(mode: ActionMode) {
            emit(BibleViewEvents.setActionMode(false))
            return callback.onDestroyActionMode(mode)
        }

        override fun onGetContentRect(mode: ActionMode, view: View, outRect: Rect) {
            if(callback is ActionMode.Callback2) {
                callback.onGetContentRect(mode, view, outRect)
            } else {
                super.onGetContentRect(mode, view, outRect)
            }
        }
    }

    override fun startActionMode(callback: ActionMode.Callback, type: Int): ActionMode {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            super.startActionMode(ActionModeCallback2(callback), type)
        } else {
            super.startActionMode(ActionModeCallback(callback), type)
        }
    }

    override fun startActionMode(callback: ActionMode.Callback): ActionMode {
        return super.startActionMode(ActionModeCallback(callback))
    }

    // This can be removed after Lollipop support is dropped.
    private inner class ActionModeCallback(val callback: ActionMode.Callback): ActionMode.Callback {
        override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
            emit(BibleViewEvents.setActionMode(true))
            callback.onCreateActionMode(mode, menu)
            return true
        }

        override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
            val wasUpdated1 = callback.onPrepareActionMode(mode, menu)
            val wasUpdated2 = onPrepareActionMenu(mode, menu)
            return wasUpdated1 || wasUpdated2
        }

        override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
            return onActionMenuItemClicked(mode, item) || callback.onActionItemClicked(mode, item)
        }

        override fun onDestroyActionMode(mode: ActionMode?) {
            emit(BibleViewEvents.setActionMode(false))
            return callback.onDestroyActionMode(mode)
        }
    }

    fun setBibleJavascriptInterface(bibleJavascriptInterface: BibleJavascriptInterface) {
        this.bibleJavascriptInterface = bibleJavascriptInterface
        val dispatcher = net.bible.sharedcore.bibleview.BridgeDispatcher(
            bridge = bibleJavascriptInterface,
            respondNull = { callId -> emit(BibleViewEvents.response(callId, null)) },
            log = { Log.e(TAG, it) },
        )
        addJavascriptInterface(AndroidBridgeEndpoint(dispatcher, bibleJavascriptInterface::getActiveLanguages), "androidBridge")
    }

    @SuppressLint("SetJavaScriptEnabled")
    fun initialise() {
        Log.i(TAG, "initialise")
        webViewClient = BibleViewClient()

        webChromeClient = object : WebChromeClient() {
            override fun onJsAlert(view: WebView, url: String, message: String, result: JsResult): Boolean {
                Log.i(TAG, message)
                result.confirm()
                return true
            }
            override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
                // If errorBox is enabled, console logging is handled in js interface so we don't want anything
                // from here anymore.
                if (!showErrorBox) {
                    Log.i(TAG, "bibleview-js: ${consoleMessage.messageLevel()} ${consoleMessage.message()}")
                }
                return true
            }
        }

        settings.javaScriptEnabled = true

        pageTiltScroller = PageTiltScroller(this, pageTiltScrollControl)
        pageTiltScroller.enableTiltScroll(true)

        // if this webview becomes (in)active then must start/stop auto-scroll
        listenEvents = true

        val locale = Locale.getDefault()
        val isRtl = TextUtils.getLayoutDirectionFromLocale(locale) == LayoutDirection.RTL
        val lang = locale.toLanguageTag()

        val fontModuleNames = AndBibleAddons.fontModuleNames.joinToString(",")
        val featureModuleNames = AndBibleAddons.featureModuleNames.joinToString(",")
        val styleModuleNames = AndBibleAddons.styleModuleNames.joinToString(",")
        // Workaround for #1756
        if (setOf(Build.VERSION_CODES.O, Build.VERSION_CODES.O_MR1).contains(Build.VERSION.SDK_INT)) {
            setLayerType(View.LAYER_TYPE_SOFTWARE, null)
        }
        loadUrl("https://appassets.androidplatform.net/assets/bibleview-js/index.html" +
            "?lang=$lang&fontModuleNames=$fontModuleNames&styleModuleNames=$styleModuleNames&featureModuleNames=$featureModuleNames&rtl=$isRtl&night=$nightMode")
    }


    override fun destroy() {
        toBeDestroyed = true
        gestureListener.destroy()
        pageTiltScroller.destroy()
        removeJavascriptInterface("androidBridge")
    }

    private val subscriptions = Subscriptions()

    private fun onActiveWindowChanged(activeWindow: Window) {
        if (window == activeWindow) {
            bibleJavascriptInterface.notificationsEnabled = true
            resumeTiltScroll()
        } else {
            bibleJavascriptInterface.notificationsEnabled = false
            pauseTiltScroll()
        }
        updateActive()
    }

    private fun onWindowsChanged() {
        if(window.isVisible) {
            updateOffsets(true)
            updateConfig()
        }
    }

    override var listenEvents: Boolean = false
        set(value) {
            if(value == field) return
            if(value) {
                subscriptions.add(AndBibleAddons.reloaded.subscribe {
                    emit(BibleViewEvents.reloadAddons(AndBibleAddons.fontModuleNames, AndBibleAddons.featureModuleNames, AndBibleAddons.styleModuleNames))
                })
                subscriptions.add(WorkspaceChanges.changes.subscribe { if (it == WorkspaceChange.SettingsEdited) updateConfig() })
                // updateConfig carries llmConfigured: follow the value itself (aiSettings.configChanged fires before it is refreshed).
                subscriptions.add(CommonUtils.llmConfiguredChanges.subscribe { updateConfig() })
                // `WebViewsBuiltEvent` / `AfterRemoveWebViewEvent` handlers used to sit here
                // and finish a deferred teardown. Both events were posted only by the classic
                // split reading area, so they became unpostable when it went; the live teardown
                // route is `BibleViewFactory.clear()`, which calls `doDestroy()` directly and
                // is not event-driven. Removed in Batch Z-late's epilogue (spec 10.3).
                subscriptions.add(hostCallbacks.insetsChanges().subscribe { updateOffsets(it.immediate) })
                subscriptions.add(windowState.windowChanges.subscribe { change ->
                    when (change) {
                        is WindowChange.ActiveWindowChanged -> onActiveWindowChanged(change.window)
                        WindowChange.WindowsChanged -> onWindowsChanged()
                        WindowChange.RestoreButtonsChanged -> updateOffsets()
                        WindowChange.LayoutConfigurationChanged -> checkWindows = true
                    }
                })
                subscriptions.add(SharedActivityState.instance.fullScreenChanged.subscribe { updateOffsets() })
                subscriptions.add(ProgressControl.changes.subscribe { change ->
                    when (change) {
                        is ProgressChange.Memorization -> onMemorizationChanged(change)
                        is ProgressChange.ChapterReadStatus -> onChapterReadStatusChanged(change)
                        is ProgressChange.ActiveCycle -> onActiveCycleChanged()
                    }
                })
                subscriptions.add(ReadingProgressSettings.changed.subscribe { onReadingProgressSettingsChanged() })
                subscriptions.add(bookmarkControl.changes.subscribe { change ->
                    documentLoader.favouriteLabels.apply(change)
                    bookmarkChanges.launch("bibleview-bookmarks-${window.id}") { onBookmarkChange(change) }
                })
                subscriptions.add(MyDocumentBookManager.changes.subscribe {
                    if (it is MyDocumentChange.AiDocPages) onAiDocPagesChanged(it)
                })
            } else {
                subscriptions.cancelAll()
            }
            field = value
        }

    private fun onAiDocPagesChanged(change: MyDocumentChange.AiDocPages) {
        // For Bible documents, convert ordinals to target versification.
        // For all other documents, pass markers as-is — Vue.js filters by sourceBookInitials/Key.
        val v11n = (firstDocument as? BibleDocument)?.swordBook?.versification

        if (change.markers.isNotEmpty()) {
            val markerStr = change.markers.map { ClientAiDocMarker(it, v11n).asJson }.joinToString(",", "[", "]")
            emit(BibleViewEvents.addOrUpdateAiDocMarkers(JsExpr(markerStr)))
        }
        if (change.deletedPageIds.isNotEmpty()) {
            emit(BibleViewEvents.deleteAiDocMarkers(change.deletedPageIds.map { it.toString() }))
        }
    }

    /** Emits bookmark changes to the reading view after any required suspend reads. */
    private suspend fun onBookmarkChange(change: BookmarkChange) {
        when (change) {
            is BookmarkChange.BookmarksUpserted -> {
                val document = firstDocument

                val clientBookmarks = change.bookmarks.map {
                    when (it) {
                        is BookmarkEntities.BibleBookmarkWithNotes ->
                            ClientBibleBookmark(
                                it,
                                when (document) {
                                    is BibleDocument -> document.swordBook.versification
                                    is MyNotesDocument -> KJVA
                                    else -> null
                                }
                            )

                        is BookmarkEntities.GenericBookmarkWithNotes -> ClientGenericBookmark(it)
                        else -> throw RuntimeException("Invalid type")
                    }
                }.map { it.asJson }

                val bookmarkStr = clientBookmarks.joinToString(",", "[", "]")
                emit(BibleViewEvents.addOrUpdateBookmarks(JsExpr(bookmarkStr)))
            }
            is BookmarkChange.NoteModified -> {
                emit(BibleViewEvents.bookmarkNoteModified(change.bookmarkId.toString(), change.lastUpdatedOn, change.notes))
            }
            is BookmarkChange.StudyPadOrder -> {
                val doc = firstDocument
                if(doc !is StudyPadDocument || doc.label.id != change.labelId) return
                val studyPadTextEntryJson = json.encodeToString(serializer(), change.newStudyPadTextEntry)
                val bookmarkToLabels = json.encodeToString(serializer(), change.bookmarkToLabelsOrderChanged)
                val genericBookmarkToLabels = json.encodeToString(serializer(), change.genericBookmarkToLabelsOrderChanged)
                val studyPadItems = json.encodeToString(serializer(), change.studyPadOrderChanged)
                emit(BibleViewEvents.addOrUpdateStudyPad(JsExpr(studyPadTextEntryJson), JsExpr(bookmarkToLabels), JsExpr(genericBookmarkToLabels), JsExpr(studyPadItems)))
            }
            is BookmarkChange.BookmarkToLabelUpserted -> {
                val doc = firstDocument
                if(doc !is StudyPadDocument || doc.label.id != change.bookmarkToLabel.labelId) return
                val bookmarkToLabelStr = when(change.bookmarkToLabel) {
                    is BookmarkEntities.BibleBookmarkToLabel ->json.encodeToString(serializer(), change.bookmarkToLabel)
                    is BookmarkEntities.GenericBookmarkToLabel -> json.encodeToString(serializer(), change.bookmarkToLabel)
                    else -> throw RuntimeException("Illegal type")
                }
                emit(BibleViewEvents.addOrUpdateBookmarkToLabel(JsExpr(bookmarkToLabelStr)))
            }
            is BookmarkChange.StudyPadTextEntryDeleted -> {
                if(firstDocument !is StudyPadDocument) return
                emit(BibleViewEvents.deleteStudyPadTextEntry(change.studyPadTextEntryId.toString()))
            }
            is BookmarkChange.LabelUpserted -> {
                val workspaceId = windowControl.windowRepository.id
                val dao = DatabaseContainer.instance.workspaceDb.workspaceDao()
                documentLoader.labelOverrides = dao.labelOverrides(workspaceId).associateBy { it.labelId }
                val overriddenLabel = change.label.withStyleOverrides(documentLoader.labelOverrides[change.label.id])
                val labelStr = json.encodeToString(serializer(), ClientBookmarkLabel(overriddenLabel))
                emit(BibleViewEvents.updateLabels(JsExpr("[$labelStr]")))
            }
            is BookmarkChange.BookmarksDeleted -> {
                val bookmarkIds = json.encodeToString(serializer(), change.bookmarkIds)
                emit(BibleViewEvents.deleteBookmarks(JsExpr(bookmarkIds)))
            }
            is BookmarkChange.LabelsDeleted -> {
                val labelIds = json.encodeToString(serializer(), change.labelIds)
                emit(BibleViewEvents.deleteLabels(JsExpr(labelIds)))
            }
        }
    }

    private fun onReadingProgressSettingsChanged() {
        val settingsJson = ReadingProgressSettings.getBundleAsJson()
        emit(BibleViewEvents.updateReadingProgressSettings(JsExpr(settingsJson)))
        updateConfig()
    }

    private fun onMemorizationChanged(event: ProgressChange.Memorization) {
        val doc = firstDocument
        if (doc !is BibleDocument && doc !is MemorizeDocument) return

        // Convert KJV ordinals to document versification
        val v11n = when (doc) {
            is BibleDocument -> doc.swordBook.versification
            is MemorizeDocument -> doc.bookInitials?.let {
                (SwordDocumentFacade.getDocumentByInitials(it) as? SwordBook)?.versification
            }
            else -> null
        }
        fun convertOrdinals(kjvOrdinals: List<Int>): List<Int> {
            val converted = if (v11n != null) {
                kjvOrdinals.map { Verse(KJVA, it).toV11n(v11n).ordinal }
            } else {
                kjvOrdinals
            }
            return converted
        }

        val addedMemorized = convertOrdinals(event.addedMemorized)
        val removedMemorized = convertOrdinals(event.removedMemorized)
        val addedTargets = convertOrdinals(event.addedTargets)
        val removedTargets = convertOrdinals(event.removedTargets)
        emit(BibleViewEvents.updateMemorizationData(addedMemorized, removedMemorized, addedTargets, removedTargets))
    }

    private fun onChapterReadStatusChanged(event: ProgressChange.ChapterReadStatus) {
        val doc = firstDocument
        if (doc !is BibleDocument) return
        // Different windows may show the same chapter number across different books, so
        // filter by KJV book ordinal here — the Vue-side tracker only checks chapter number.
        val docKjvBookOrdinal = doc.verseRange.toV11n(KJVA).start.book.ordinal
        if (docKjvBookOrdinal != event.kjvBookOrdinal) return
        emit(BibleViewEvents.updateChapterReadStatus(event.chapter, event.count))
    }

    private fun onActiveCycleChanged() {
        val doc = firstDocument
        if (doc !is BibleDocument) return
        if (minChapter < 0 || maxChapter < 0) return
        val v11n = doc.swordBook.versification
        val book = doc.verseRange.start.book
        val chapters = minChapter..maxChapter
        scope.launch {
            for (chapter in chapters) {
                val count = ProgressControl.getChapterReadCount(v11n, book, chapter)
                emit(BibleViewEvents.updateChapterReadStatus(chapter, count))
            }
        }
    }

    fun doDestroy() {
        if(!toBeDestroyed) {
            destroy()
        }
        listenEvents = false
        Log.i(TAG, "Destroying Bibleview")
        session.dispose()
        super.destroy()
        val win = windowRef.get()
        if(win != null && win.bibleView === this) {
            win.bibleView = null
        }
        onDestroy?.invoke()
    }

    object UriConstants {
        const val SCHEME_DOWNLOAD = "download"
        const val SCHEME_ERROR = "ab-error"
        const val SCHEME_W = "ab-w"
        const val SCHEME_REFERENCE = "osis"
        const val MULTI_REFERENCE = "multi"
        const val SCHEME_MYNOTES = "my-notes"
        const val SCHEME_EPUB_REF = "epub-ref"
        const val SCHEME_STUDYPAD = "journal"
        const val SCHEME_FIND_ALL_OCCURRENCES = "ab-find-all"
        const val SCHEME_SWORD = "sword"
        const val SCHEME_STRONGS = "strongs"
        const val SCHEME_MORPHOLOGY = "morphology"
    }

    class ModuleAssetHandler: PathHandler {
        override fun handle(path: String): WebResourceResponse {
            val parts = path.split("/", limit = 2);
            if(parts.size != 2) return notFound
            val (bookName, resourcePath) = parts
            val location = File(Books.installed().getBook(bookName)!!.bookMetaData.location)
            val f = File(location, resourcePath)
            return if(f.isFile && f.exists()) {
                WebResourceResponse(URLConnection.guessContentTypeFromName(resourcePath), null, f.inputStream())
            } else notFound
        }
    }

    inner class EpubResourcesAssetHandler: PathHandler {
        override fun handle(path: String): WebResourceResponse {
            val book = (firstDocument as? OsisDocument)?.book ?: return notFound
            val stream = ((book as? SwordGenBook)?.backend as? EpubBackend)?.openResource(path)?.buffer()?.inputStream() ?: return notFound
            return WebResourceResponse(URLConnection.guessContentTypeFromName(path), null, stream)
        }
    }

    class ModuleStylesAssetHandler: PathHandler {
        private val epubRe = Regex("""^epub/([^/]+)/([^/]+)/style.css$""")
        private val epubRelatedResourcesRe = Regex("""^epub/([^/]+)/(.*)$""")
        private val colorRe = Regex("""\b(background-color|background|background-image|color):[^;]+;""")
        private val bodyRe = Regex("""\bbody\s*\{[^}]*\}""")
        private val lineHeightRe = Regex("""\bline-height:[^;]*;""")
        override fun handle(path: String): WebResourceResponse {
            val epubMatch = epubRe.matchEntire(path)
            if(epubMatch != null) {
                val bookInitials = epubMatch.groupValues[1]
                val keyStr = epubMatch.groupValues[2]
                val book = Books.installed().getBook(bookInitials)?: return notFound
                if (!book.isEpub) return notFound
                val key = try { book.getKey(keyStr) } catch (e: NoSuchKeyException) {return notFound}

                val backend = (book as? SwordGenBook)?.backend as? EpubBackend ?: return notFound
                val content = try {
                    blockingAtEdge { backend.styleSheetContent(key) } // L1-edge: WebView shouldInterceptRequest
                } catch (_: IllegalStateException) {
                    return notFound // A request for a retired page must not read replacement assets.
                }.replace(colorRe, "")
                    .replace(bodyRe, "")
                    .replace(lineHeightRe, "")

                return WebResourceResponse(URLConnection.guessContentTypeFromName(path), null, content.byteInputStream())
            }
            val resourceMatch = epubRelatedResourcesRe.matchEntire(path)
            if(resourceMatch != null) {
                val bookInitials = resourceMatch.groupValues[1]
                val relativePath = resourceMatch.groupValues[2]
                val book = Books.installed().getBook(bookInitials)?: return notFound
                if (!book.isEpub) return notFound
                val stream = ((book as? SwordGenBook)?.backend as? EpubBackend)?.openResource(relativePath)?.buffer()?.inputStream() ?: return notFound
                return WebResourceResponse(URLConnection.guessContentTypeFromName(relativePath), null, stream)
            }
            val parts = path.split("/", limit = 2);
            if(parts.size != 2) return notFound
            val (bookName, resourcePath) = parts
            val book = Books.installed().getBook(bookName) ?: return notFound

            val styleFile = book.bookMetaData.getProperty("AndBibleCSS") ?: return notFound

            val location = File(book.bookMetaData.location)
            var f = File(location, styleFile)
            if(resourcePath != "style.css") {
                f = File(f.parent, resourcePath)
            }

            return if (f.isFile && f.exists()) {
                WebResourceResponse(URLConnection.guessContentTypeFromName(resourcePath), null, f.inputStream())
            } else notFound
        }
    }

    class FontsAssetHandler: PathHandler {
        override fun handle(path: String): WebResourceResponse {
            val parts = path.split("/", limit = 2);
            if(parts.size != 2) return notFound
            val (moduleName, resourcePath) = parts
            val book = Books.installed().getBook(moduleName) ?: return notFound
            if(resourcePath == "fonts.css") {
                val fontCss = StringBuilder()
                val fonts = fontsByModule[book.initials] ?: return notFound
                for(font in fonts) {
                    fontCss.append("""@font-face {
                        |font-family: '${font.name}';
                        |src: url('${font.path}') format('truetype');
                        |}
                        |""".trimMargin())
                }
                return WebResourceResponse("text/css", null, fontCss.toString().byteInputStream())
            }
            else {
                val location = File(book.bookMetaData.location)
                val f = File(location, resourcePath)
                return if (f.isFile && f.exists()) {
                    WebResourceResponse(URLConnection.guessContentTypeFromName(resourcePath), null, f.inputStream())
                } else notFound
            }
        }
    }

    class BackgroundImageAssetHandler: PathHandler {
        override fun handle(path: String): WebResourceResponse {
            val moduleName = path.trim('/')
            val provided = AndBibleAddons.providedBackgroundImages[moduleName] ?: return notFound
            val f = provided.file
            return if (f.isFile && f.exists()) {
                WebResourceResponse(URLConnection.guessContentTypeFromName(f.name), null, f.inputStream())
            } else notFound
        }
    }

    class FeatureAssetHandler: PathHandler {
        override fun handle(path: String): WebResourceResponse {
            val parts = path.split("/", limit = 2);
            if(parts.size != 2) return notFound;
            val (moduleName, resourcePath) = parts
            val book = Books.installed().getBook(moduleName) ?: return notFound
            val location = File(book.bookMetaData.location)
            val f = File(location, resourcePath)
            return if (f.isFile && f.exists() && checkSignature(f)) {
                WebResourceResponse(URLConnection.guessContentTypeFromName(resourcePath), null, f.inputStream())
            } else notFound
        }

        private fun checkSignature(file: File): Boolean {
            val signatureFile = File(file.path + ".sign")
            return CommonUtils.verifySignature(file, signatureFile)
        }
    }
    inner class MyAssetsPathHandler: PathHandler {
        override fun handle(path: String): WebResourceResponse {
            return try {
                val inputStream = context.resources.assets.open(path)
                val mimeType = when(File(path).extension) {
                    "js" -> "application/javascript"
                    "html" -> "text/html"
                    "css" -> "text/css"
                    "svg" -> "image/svg+xml"
                    else -> "text/plain"
                }
                WebResourceResponse(mimeType, null, inputStream)
            } catch (e: IOException) {
                Log.e(TAG, "Error opening asset path: $path", e)
                notFound
            }
        }
    }
    class NotFoundHandler: PathHandler {
        override fun handle(path: String): WebResourceResponse = notFound
    }

    val assetLoader = WebViewAssetLoader.Builder()
        .addPathHandler("/assets/", MyAssetsPathHandler())
        .addPathHandler("/module/", ModuleAssetHandler())
        .addPathHandler("/fonts/", FontsAssetHandler())
        .addPathHandler("/background/", BackgroundImageAssetHandler())
        .addPathHandler("/features/", FeatureAssetHandler())
        .addPathHandler("/module-style/", ModuleStylesAssetHandler())
        .addPathHandler("/epub/", EpubResourcesAssetHandler())
        .addPathHandler("/", NotFoundHandler())
        .build()


    internal fun openEpubLink(book: SwordGenBook, backend: EpubBackend, keyStr: String, idStr: String) =
        readingBridge.openEpubLink(book, backend, keyStr, idStr)

    fun openLink(uri: Uri): Boolean = when(uri.scheme) {
        UriConstants.SCHEME_W -> {
            val links = mutableListOf<BibleLink>()
            for (paramName in uri.queryParameterNames) {
                links.addAll(uri.getQueryParameters(paramName).map { BibleLink(paramName, it) })
            }
            if (links.size > 1) {
                linkControl.openMulti(links)
            } else {
                linkControl.loadApplicationUrl(links.first(), null)
            }
            true
        }
        UriConstants.SCHEME_MYNOTES -> {
            val ordinal = uri.getQueryParameter("ordinal")?.toInt()!!
            val v11n = uri.getQueryParameter("v11n")
            linkControl.openMyNotes(v11n!!, ordinal)
        }
        UriConstants.SCHEME_EPUB_REF -> {
            val bookStr = uri.getQueryParameter("book")!!
            val keyStr = uri.getQueryParameter("toKey")!!
            val idStr = uri.getQueryParameter("toId")!!

            val book = Books.installed().getBook(bookStr) as SwordGenBook
            val backend = book.backend as EpubBackend
            openEpubLink(book, backend, keyStr, idStr)
            true
        }
        UriConstants.MULTI_REFERENCE -> {
            val osisRefs = uri.getQueryParameters("osis")
            val v11n = uri.getQueryParameter("v11n")
            if (osisRefs != null) {
                linkControl.openMulti(osisRefs.map { BibleLink("osis", it, v11n) })
            } else false
        }
        UriConstants.SCHEME_STUDYPAD -> {
            val id = uri.getQueryParameter("id")
            val entryId = uri.getQueryParameter("entryId") ?: uri.getQueryParameter("bookmarkId")
            if (id != null) {
                // The label lookup suspends, so launch; the context-menu window mode is read now because
                // the menu handler resets it as soon as this synchronous URL handler returns.
                val windowMode = linkControl.windowMode
                scope.launch(Dispatchers.Main) { linkControl.openStudyPad(IdType(id), IdType(entryId), windowMode) }
                true
            } else false
        }
        UriConstants.SCHEME_REFERENCE -> {
            val osisRef = uri.getQueryParameter("osis")
            val doc = uri.getQueryParameter("doc")
            val ordinal = uri.getQueryParameter("ordinal")
            val v11n = uri.getQueryParameter("v11n")
            val forceDoc = uri.getBooleanQueryParameter("force-doc", false)
            val book = Books.installed().getBook(doc)
            if(ordinal != null && ordinal.toIntOrNull() != null && book != null) {
                val bookKey = book.getKey(osisRef).let {if(it is RangedPassage) it.first() else it }
                linkControl.showLink(book, BookAndKey(bookKey, book, OrdinalRange(ordinal.toInt())))
            } else if (osisRef != null) {
                linkControl.loadApplicationUrl(BibleLink("osis", osisRef.trim(), v11n, forceDoc = forceDoc), book)
            } else {
                val contentRef = uri.getQueryParameter("content")!!
                linkControl.loadApplicationUrl(BibleLink("content", contentRef.trim(), v11n, forceDoc = forceDoc), book)
            }
            true
        }
        UriConstants.SCHEME_FIND_ALL_OCCURRENCES -> {
            val type = uri.getQueryParameter("type")!!
            var name = uri.getQueryParameter("name")!!.lowercase()
            if(!(name.startsWith("g") || name.startsWith("h"))) {
                name = type[0] + name
            }
            linkControl.showAllOccurrences(name, SearchControl.SearchBibleSection.ALL)
            true
        }
        UriConstants.SCHEME_ERROR -> {
            linkControl.errorLink()
            true
        }
        UriConstants.SCHEME_DOWNLOAD -> {
            navigator.openDownloadSearch(uri.getQueryParameter("initials"))
            true
        }
        UriConstants.SCHEME_SWORD -> {
            // Handle sword://MODULE/KEY format
            // URI like sword://KJV/Matt.5.3 or sword://MHC/Matt.5.3
            val url = uri.toString()
            linkControl.loadApplicationUrl(BibleLink("sword", url), null)
            true
        }
        UriConstants.SCHEME_STRONGS -> {
            // Document-independent Strong's links: strongs://G2316, strongs://H430
            // Resolves to user's configured default Strong's dictionary via UriAnalyzer
            val ref = uri.authority ?: uri.schemeSpecificPart
            linkControl.loadApplicationUrl(BibleLink("strong", ref), null)
            true
        }
        UriConstants.SCHEME_MORPHOLOGY -> {
            // Document-independent morphology links: morphology://robinson/V-PAI-3S
            // Resolves to user's configured default morphology dictionary via UriAnalyzer
            val morphType = uri.authority ?: ""
            val code = uri.path?.trimStart('/') ?: ""
            linkControl.loadApplicationUrl(BibleLink(morphType, code), null)
            true
        }
        "http", "https" -> {
            CommonUtils.openLink(uri.toString())
            true
        }
        else -> {
            Log.e(TAG, "Unsupported scheme ${uri.scheme}")
            true
        }
    }

    private inner class BibleViewClient: WebViewClient() {
        override fun onLoadResource(view: WebView, url: String) {
            Log.i(TAG, "onLoadResource:$url")
            super.onLoadResource(view, url)
        }

        override fun shouldOverrideUrlLoading(view: WebView?, url: String): Boolean {
            return if(openLink(Uri.parse(url))) {
                true
            } else {
                super.shouldOverrideUrlLoading(view, url)
            }
        }
        override fun shouldOverrideUrlLoading(view: WebView, req: WebResourceRequest): Boolean {
            return if(openLink(req.url)) {
                true
            } else {
                super.shouldOverrideUrlLoading(view, req)
            }
        }


        override fun onReceivedError(view: WebView, errorCode: Int, description: String, failingUrl: String) {
            super.onReceivedError(view, errorCode, description, failingUrl)
            Log.e(TAG, description)
        }

        override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
            assetLoader.shouldInterceptRequest(request.url)

        override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
            Log.i(TAG, "onRenderProcessGone")
            // Renderer-crash RECOVERY is RETIRED, not ported: this used to cast `view.parent` to
            // classic `BibleFrame` and call `bf.recreate()`, which rebuilt the frame -- and with it
            // this BibleView -- after the WebView renderer process died. On the Compose path the
            // parent is the `AndroidView` container, so the cast has been null and the recovery a
            // silent no-op ever since the host was introduced; the frame it reached for is deleted
            // in Batch Z-late's epilogue (spec 10.3). What is no longer recovered: after a renderer
            // death the pane is left showing a blank WebView until something else rebuilds it
            // (a window/workspace change, or the user reopening the app). Returning `true` still
            // keeps the crash from taking the whole process down, exactly as before.
            Log.w(TAG, "WebView renderer gone; no recreate on the compose path")
            return true
        }
    }

    private var contextMenuInfo: BibleViewContextMenuInfo? = null
    override fun getContextMenuInfo(): ContextMenuInfo? {
        return contextMenuInfo
    }

    private inner class BibleViewLongClickListener() : OnLongClickListener {
        override fun onLongClick(v: View): Boolean {
            Log.i(TAG, "onLongClickListener")
            val result = hitTestResult
            return if (result.type == HitTestResult.SRC_ANCHOR_TYPE) {
                contextMenuInfo = LinkLongPressContextMenuInfo(result.extra!!)
                v.showContextMenu()
            } else {
                contextMenuInfo = null
                false
            }
        }
    }

    internal interface BibleViewContextMenuInfo: ContextMenuInfo {
        fun onContextItemSelected(item: MenuItem): Boolean
        fun onCreateContextMenu(menu: ContextMenu, v: View, menuInflater: MenuInflater)
    }

    internal inner class LinkLongPressContextMenuInfo(private val targetLink: String) : BibleViewContextMenuInfo {
        override fun onContextItemSelected(item: MenuItem): Boolean {
            val uri = Uri.parse(targetLink)
            if(item.itemId == R.id.copy_link_to_clipboard) {
                val osisRef = uri.getQueryParameter("osis")?: return false
                val doc = uri.getQueryParameter("doc")
                val ordinal = uri.getQueryParameter("ordinal")?.toInt()
                val v11n = uri.getQueryParameter("v11n")
                val abUrl = CommonUtils.makeAndBibleUrl(
                    osisRef,
                    doc,
                    v11n,
                    ordinal
                )
                clipboardKey = parseAndBibleReference(abUrl)
                CommonUtils.copyToClipboard(
                    ClipData.newPlainText(abUrl, abUrl),
                    R.string.reference_copied_to_clipboard
                )
            } else {
                val windowMode = when (item.itemId) {
                    R.id.open_link_in_special_window -> WindowMode.WINDOW_MODE_SPECIAL
                    R.id.open_link_in_new_window -> WindowMode.WINDOW_MODE_NEW
                    R.id.open_link_in_this_window -> WindowMode.WINDOW_MODE_THIS
                    else -> WindowMode.WINDOW_MODE_UNDEFINED
                }
                linkControl.windowMode = windowMode
                openLink(uri)
                linkControl.windowMode = WindowMode.WINDOW_MODE_UNDEFINED
                contextMenuInfo = null
            }
            return true
        }

        override fun onCreateContextMenu(menu: ContextMenu, v: View, menuInflater: MenuInflater) {
            menuInflater.inflate(R.menu.link_context_menu, menu)

            val parsed: Uri = Uri.parse(targetLink)
            menu.findItem(R.id.copy_link_to_clipboard).isVisible = parsed.scheme == UriConstants.SCHEME_REFERENCE
            val openLinksInSpecialWindowByDefault = CommonUtils.settings.getBoolean("open_links_in_special_window_pref", true)
            val item =
                if(openLinksInSpecialWindowByDefault)
                    menu.findItem(R.id.open_link_in_special_window)
                else
                    menu.findItem(R.id.open_link_in_this_window)
            item.isVisible = false
        }
    }

    // A/B batch 4a whole-batch review C1: delegates to the top-level bibleViewBackgroundColorFor
    // (declared above, next to the `white`/`black` consts it uses) so ComposeReadingViewHost can
    // resolve the SAME colour for a window with no BibleView yet. Behaviour-preserving: identical
    // body, just extracted.
    val backgroundColor: Int get() = bibleViewBackgroundColorFor(window)

    override var lastUpdated = 0L

    override var firstDocument: Document?
        get() = session.firstDocument
        set(value) { session.firstDocument = value }

    override suspend fun loadDocument(document: Document,
                             updateLocation: Boolean,
                             key: Key?,
                             anchorOrdinal: OrdinalRange?,
                             htmlId: String?,
                             windowContentGeneration: Long?,
    ) = documentLoader.loadDocument(document, updateLocation, key, anchorOrdinal, htmlId, windowContentGeneration)

    internal var initialKey: Key?
        get() = session.initialKey
        set(value) { session.initialKey = value }
    private var lastKey: Key?
        get() = session.lastKey
        set(value) { session.lastKey = value }
    private var firstKey: Key?
        get() = session.firstKey
        set(value) { session.firstKey = value }
    private val displaySettings get() = window.pageManager.actualTextDisplaySettings
    private val windowState: WindowStateServiceImpl by lazy { GlobalContext.get().get() }
    internal val workspaceSettings get() = session.workspaceSettings

    override fun updateTextDisplaySettings(onAttach: Boolean) {
        Log.i(TAG, "updateTextDisplaySettings")
        updateBackgroundColor()
        updateConfig(onAttach)
    }

    private val hasActiveIndicator get() =
        CommonUtils.settings.getBoolean("show_active_window_indicator", true)
            && isActive && windowControl.windowRepository.visibleWindows.size > 1

    private val isActive get() = windowControl.activeWindow.id == window.id

    private fun updateActive() = documentLoader.updateActive()

    private val showErrorBox get() = if(CommonUtils.isBeta) CommonUtils.settings.getBoolean("show_errorbox", false) else false

    /** Gathers what `set_config` reads (Android settings and window state); the script itself is [ConfigCommand]. */
    private fun configInputs(favouriteLabelIds: List<IdType>) = ConfigInputs(
        displaySettings = displaySettings,
        isActive = isActive,
        isBottomWindow = isBottomWindow,
        hasActiveIndicator = hasActiveIndicator,
        nightMode = nightMode,
        showErrorBox = showErrorBox,
        favouriteLabels = favouriteLabelIds,
        recentLabels = workspaceSettings.recentLabels.map { it.labelId },
        studyPadCursors = workspaceSettings.studyPadCursors.toMap(),
        autoAssignLabels = workspaceSettings.autoAssignLabels.toList(),
        hideCompareDocuments = workspaceSettings.hideCompareDocuments.toSet(),
        limitAmbiguousModalSize = workspaceSettings.limitAmbiguousModalSize,
        windowDisplayId = window.displayId,
        disableBibleModalButtons = CommonUtils.settings.getStringSet("disable_bible_bookmark_modal_buttons", emptySet()),
        disableGenericModalButtons = CommonUtils.settings.getStringSet("disable_gen_bookmark_modal_buttons", emptySet()),
        monochromeMode = CommonUtils.settings.monochromeMode,
        einkMode = CommonUtils.settings.einkMode,
        colorEinkMode = CommonUtils.settings.colorEinkMode,
        pureMonochromeMode = CommonUtils.settings.pureMonochromeMode,
        disableAnimations = CommonUtils.settings.disableAnimations,
        fontSizeMultiplier = CommonUtils.settings.fontSizeMultiplierFloat,
        enabledExperimentalFeatures = CommonUtils.settings.enabledExperimentalFeatures.toList(),
        themeColors = themeColorsJson(
            seedArgb = currentWorkspaceThemeSeedArgb(),
            dark = nightMode,
            colorMode = CommonUtils.settings.displayColorMode,
            style = CommonUtils.settings.themePaletteStyle,
        ),
        disableClickToEdit = CommonUtils.settings.disableClickToEdit,
        llmConfigured = CommonUtils.settings.llmConfigured,
        notesContentType = CommonUtils.settings.notesContentType,
    )

    // Synchronous, so set_config keeps its place in the JS task queue relative to what is emitted
    // right after it (an `initial` config must never land after a later one). Favourites come from
    // the [favouriteLabels] cache.
    private fun updateConfig(initial: Boolean = false) = documentLoader.updateConfig(initial)

    fun updateBackgroundColor() {
        Log.i(TAG, "updateBackgroundColor")
        updateNativeScrollbars()
        setBackgroundColor(backgroundColor)
    }

    private val nightMode get() = hostCallbacks.currentNightMode()

    private var labelsUploaded = false

    override fun adjustLoadingCount(adj: Int): Boolean {
        return emit(BibleViewEvents.adjustLoadingCount(adj))
    }

    /**
     * Enable or disable zoom controls depending on whether map is currently shown
     */
    private fun enableZoomForMap(isMap: Boolean) {
        Log.i(TAG, "enableZoomForMap $isMap")
        settings.builtInZoomControls = true
        settings.setSupportZoom(isMap)
        settings.displayZoomControls = false
        // http://stackoverflow.com/questions/3808532/how-to-set-the-initial-zoom-width-for-a-webview
        settings.loadWithOverviewMode = isMap
        //settings.useWideViewPort = isMap
    }


    var contentVisible: Boolean
        get() = session.contentVisible
        set(value) { session.contentVisible = value }

    /** prevent swipe right if the user is scrolling the page right  */
    override val isPageNextOkay: Boolean get () {
        var isOkay = true
        if(modalOpen) return false
        if(firstDocument is StudyPadDocument) return false
        if(firstDocument is MemorizeDocument) return false
        if (window.pageManager.isMapShown) {
            // allow swipe right if at right side of map
            val isAtRightEdge = if(CommonUtils.isRtl) scrollX == 0 else scrollX >= maxHorizontalScroll

            // the first side swipe takes us to the edge and second takes us to next page
            isOkay = isAtRightEdge && wasAtRightEdge
            wasAtRightEdge = isAtRightEdge
            wasAtLeftEdge = false
        }
        return isOkay
    }

    /** prevent swipe left if the user is scrolling the page left  */
    override val isPagePreviousOkay: Boolean get () {
        var isOkay = true
        if(modalOpen) return false
        if(firstDocument is StudyPadDocument) return false
        if(firstDocument is MemorizeDocument) return false
        if (window.pageManager.isMapShown) {
            // allow swipe left if at left edge of map
            val isAtLeftEdge = if(!CommonUtils.isRtl) scrollX == 0 else scrollX >= maxHorizontalScroll

            // the first side swipe takes us to the edge and second takes us to next page
            isOkay = isAtLeftEdge && wasAtLeftEdge
            wasAtLeftEdge = isAtLeftEdge
            wasAtRightEdge = false
        }
        return isOkay
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        Log.i(TAG, "Focus changed so start/stop scroll $hasWindowFocus")
        if (hasWindowFocus) {
            resumeTiltScroll()
        } else {
            pauseTiltScroll()
        }
    }

    private fun pauseTiltScroll() {
        pageTiltScroller.enableTiltScroll(false)
    }

    private fun resumeTiltScroll() {
        // but if multiple windows then only if the current active window
        if (windowControl.isActiveWindow(window)) {
            pageTiltScroller.enableTiltScroll(true)
        }
    }

    var lastTouched: Long = 0L

    override fun onScrollChanged(l: Int, t: Int, oldl: Int, oldt: Int) {
        lastTouched = System.currentTimeMillis()
        super.onScrollChanged(l, t, oldl, oldt)
    }

    /** ensure auto-scroll does not continue when screen is powered off
     */
    override fun onScreenTurnedOn() {
        resumeTiltScroll()
    }

    override fun onScreenTurnedOff() {
        pauseTiltScroll()
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        windowControl.activeWindow = window

        val handled = super.onTouchEvent(event)

        // Allow user to redefine viewing angle by touching screen
        pageTiltScroller.recalculateViewingPosition()

        return handled
    }

    fun scroll(forward: Boolean, scrollAmount: Int): Boolean {
        var ok = false
        hideScrollBar = true
        for (i in 0 until scrollAmount) {
            if (forward) {
                // scroll down/forward if not at bottom
                if (scrollY + 1 < maxVerticalScroll) {
                    scrollBy(0, 1)
                    ok = true
                }
            } else {
                // scroll up/backward if not at top
                if (scrollY > TOP_OF_SCREEN) {
                    // scroll up/back
                    scrollBy(0, -1)
                    ok = true
                }
            }
        }
        hideScrollBar = false
        return ok
    }

    /** allow vertical scroll bar to be hidden during auto-scroll
     */
    override fun awakenScrollBars(startDelay: Int, invalidate: Boolean): Boolean {
        return if (!hideScrollBar) {
            super.awakenScrollBars(startDelay, invalidate)
        } else {
            false
        }
    }

    override fun asView(): View {
        return this
    }

    private var checkWindows = false

    private fun updateOffsets(immediate: Boolean = false) {
        if(isTopWindow || isBottomWindow && contentVisible && window.isVisible)
            emit(BibleViewEvents.setOffsets(topOffset, bottomOffset, immediate, hostCallbacks.imeHeight() > 0))
    }

    /**
     * R6a: `MainBibleActivity.isSplitVertically` DERIVED rather than bundled as a lambda. Its body
     * is `if (workspaceSettings.enableReverseSplitMode) !CommonUtils.isPortrait else
     * CommonUtils.isPortrait`, and both halves are already in reach here: the settings come off the
     * very `windowControl.windowRepository` the two properties below read on the same line (that IS
     * the Activity's `windowRepository` — `ReadingAppBootstrap` assigns
     * `windowControl.windowRepository = windowRepository` when it creates it), and
     * `CommonUtils.isPortrait` is a global reading the current Activity's configuration. A derived
     * value beats a lambda: there is no host left that could supply a different answer, and nothing
     * to keep in sync.
     */
    private val isSplitVertically: Boolean
        get() {
            val reverse = windowControl.windowRepository.workspaceSettings.enableReverseSplitMode
            return if (reverse) !CommonUtils.isPortrait else CommonUtils.isPortrait
        }

    private val isTopWindow
        get() = !isSplitVertically || windowControl.windowRepository.firstVisibleWindow == window

    private val isBottomWindow
        get() = !isSplitVertically || windowControl.windowRepository.lastVisibleWindow == window

    val topOffset
        get() =
            if(isTopWindow && !SharedActivityState.instance.isFullScreen)
                (hostCallbacks.topOffset2()
                    / host.hostContext.resources.displayMetrics.density)
            else 0F
    
    val bottomOffset
        get() =
            if(isBottomWindow)
                (hostCallbacks.bottomOffsetForWebView()
                    / host.hostContext.resources.displayMetrics.density)
            else 0F

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        super.onSizeChanged(w, h, ow, oh)
        // `separatorMoving` used to gate this: the classic split reading area suppressed the check
        // while a separator was being dragged. Its only writer was the `WindowSizeChangedEvent`
        // handler, whose only poster was that separator, so the epilogue removed the whole chain
        // (spec 10.3) and the check is unconditional again.
        if(lastUpdated != 0L && w != ow) {
            doCheckWindows()
        }
    }

    private fun doCheckWindows() {
        if(!jsChannel.loading && checkWindows) {
            jsChannel.executeNow(BibleViewEvents.setOffsetsWithoutScroll(topOffset, bottomOffset))
            if (window.pageManager.currentPage.documentCategory == DocumentCategory.BIBLE) {
                scrollOrJumpToVerse(window.pageManager.currentBible.currentBibleVerse.verse, true)
            }
            checkWindows = false
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        Log.i(TAG, "Detached from window")
        // prevent random verse changes while layout is being rebuild because of window changes
        bibleJavascriptInterface.notificationsEnabled = false
        pauseTiltScroll()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        Log.i(TAG, "Attached to window")
        if (windowControl.isActiveWindow(window)) {
            bibleJavascriptInterface.notificationsEnabled = true

            resumeTiltScroll()
        }
        if(contentVisible) {
            updateTextDisplaySettings(true)
        }
        jsChannel.flush()
    }

    override fun scrollOrJumpToVerse(key: Key, forceNow: Boolean) = documentLoader.scrollOrJumpToVerse(key, forceNow)

    override fun scrollOrJumpToOrdinal(ordinal: OrdinalRange?, htmlId: String?, bookInitials: String?, osisRef: String?, forceNow: Boolean) =
        documentLoader.scrollOrJumpToOrdinal(ordinal, htmlId, bookInitials, osisRef, forceNow)

    override fun highlightOrdinalRange(bookInitials: String, osisRef: String, range: IntRange) =
        documentLoader.highlightOrdinalRange(bookInitials, osisRef, range)

    override fun highlightBibleOrdinalRange(range: IntRange) = documentLoader.highlightBibleOrdinalRange(range)

    fun emit(call: JsCall): Boolean = session.emit(call)

    private suspend fun evaluateJavascriptAsync(javascript: String): String = jsChannel.evaluate(javascript)

    private val isBible get() = firstDocument is BibleDocument
    private val isCommentary get() = (firstDocument as? OsisDocument)?.book?.bookCategory == BookCategory.COMMENTARY
    private val isMyNotes get() = firstDocument is MyNotesDocument

    override val verseRangeLoaded: VerseRange? get() {
        val key = (firstKey as? Verse)?: return null
        return CommonUtils.getWholeChapters(key.versification, key.book, minChapter, maxChapter)
    }

    override fun hasChapterLoaded(chapter: Int) = session.hasChapterLoaded(chapter)

    /** JS finished starting up; the bridge reaches [documentLoader] itself, this stays for callers holding the view. */
    fun setClientReady() = documentLoader.setClientReady()

    var modalOpen: Boolean
        get() = session.modalOpen
        set(value) { session.modalOpen = value }

    private fun closeModals() {
        emit(BibleViewEvents.closeModals())
    }

    fun backButtonPressed(): Boolean {
        if(modalOpen) {
            closeModals()
            return true;
        }
        return false
    }

    fun volumeUpPressed(): Boolean {
        emit(BibleViewEvents.scrollUp())
        return true
    }

    fun volumeDownPressed(): Boolean {
        emit(BibleViewEvents.scrollDown())
        return true
    }

    fun exportHtml() {
        emit(BibleViewEvents.exportHtml())
    }

    override fun onFocusChanged(focused: Boolean, direction: Int, previouslyFocusedRect: Rect?) {
        if(focused && windowRepository.activeWindow.id != window.id) {
            windowRepository.activeWindow = window
        }
        super.onFocusChanged(focused, direction, previouslyFocusedRect)
    }

    var onDestroy: (() -> Unit)? = null

    private val TAG get() = "BibleView[${windowRef.get()?.displayId}]"

    companion object {
        // never go to 0 because a bug in Android prevents invalidate after loadDataWithBaseURL so
        // no scrollOrJumpToVerse will occur
        private const val TOP_OF_SCREEN = 1
    }

    private val currentPageManager: CurrentPageManager
        get() = windowControl.activeWindowPageManager


}

