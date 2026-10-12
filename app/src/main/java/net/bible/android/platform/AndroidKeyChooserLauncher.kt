package net.bible.android.platform

import android.app.Activity
import android.content.Intent
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.bible.android.control.page.CurrentBiblePage
import net.bible.android.control.page.CurrentCommentaryPage
import net.bible.android.control.page.CurrentDictionaryPage
import net.bible.android.control.page.CurrentGeneralBookPage
import net.bible.android.control.page.CurrentMapPage
import net.bible.android.control.page.CurrentPage
import net.bible.android.control.page.KeyChooserLauncher
import net.bible.android.database.WorkspaceEntities
import net.bible.android.view.activity.base.ActivityBase
import net.bible.android.view.activity.base.ActivityBase.Companion.STD_REQUEST_CODE
import net.bible.android.view.activity.bookmark.ManageLabelsContract
import net.bible.android.view.activity.bookmark.updateFrom
import net.bible.android.view.activity.nav.NavHostComposeActivity
import net.bible.android.view.activity.page.KeyChooserResults
import net.bible.service.download.FakeBookFactory
import net.bible.service.sword.mydocument.isMyDocument
import net.bible.service.sword.mydocument.myDocumentId
import net.bible.sharedcore.nav.NavRoutes

/** Android chooser routing, with results owned by the page and lifecycle that opened it. */
class AndroidKeyChooserLauncher(private val currentActivity: () -> ActivityBase?) : KeyChooserLauncher {
    override fun startKeyChooser(page: CurrentPage): Boolean {
        val context = currentActivity() ?: return false
        when (page) {
            is CurrentBiblePage, is CurrentCommentaryPage -> context.startActivityForResult(
                NavHostComposeActivity.intentFor(context, NavRoutes.gridChoosePassage(isScripture = true)), STD_REQUEST_CODE)
            is CurrentDictionaryPage -> context.startActivityForResult(
                NavHostComposeActivity.intentFor(context, NavRoutes.CHOOSE_DICTIONARY_WORD), STD_REQUEST_CODE)
            is CurrentMapPage -> context.startActivityForResult(
                NavHostComposeActivity.intentFor(context, NavRoutes.CHOOSE_MAP_KEY), STD_REQUEST_CODE)
            is CurrentGeneralBookPage -> startGeneralBookChooser(page, context)
        }
        return true
    }

    /**
     * The workspace settings this page's key chooser seeds and writes back — **this page's own
     * window's**, never the Activity's and never `windowControl`'s.
     *
     * Reading-host re-typing T8a item 1. The body of [startGeneralBookChooser] used to read
     * `context.workspaceSettings`, which only `MainBibleActivity` declares (`:400`), and guarded
     * that read with `if(context !is MainBibleActivity) return` — added by `b718fe85c` (2022) in
     * the same commit that widened the parameter from `MainBibleActivity` to [ActivityBase], i.e. a
     * TYPE artefact, not a policy. On `NavHostComposeActivity` it made every general-book /
     * StudyPad / multi-document key-chooser tap a silent no-op.
     *
     * A `WindowRepository` reached through the HOST (`ReadingHostActivity.hostWindowRepository`)
     * would have worked for three of the four callers; this one is correct for all four and needs
     * no host at all. `Window.windowRepository` is a constructor property and `Window`'s `init`
     * sets `pageManager.window = this`, so the page a key chooser is opened FOR always knows the
     * repository that owns it — which is the repository whose workspace the chooser must edit, even
     * with two live reading hosts (the identity finding of R6c1/R6d). `CurrentPageManager`'s
     * auto-open path in particular calls in with `CurrentActivityHolder.currentActivity`, which may
     * be any Activity at all.
     *
     * A `get()`, not a captured value: `WindowRepository.workspaceSettings` is a `var` that
     * `loadFromDb` replaces, and classic re-read `context.workspaceSettings` on both sides of the
     * `awaitIntent` below. Same two reads, same instants.
     */
    private fun workspaceSettings(page: CurrentGeneralBookPage): WorkspaceEntities.WorkspaceSettings =
        page.pageManager.window.windowRepository.workspaceSettings

    /**
     * Open the key chooser for this page's document.
     *
     * Runs on ANY [ActivityBase], exactly as [CurrentBiblePage], [CurrentDictionaryPage],
     * [CurrentMapPage] and [CurrentCommentaryPage] always have — see [workspaceSettings] for the
     * `!is MainBibleActivity` early return that used to stand here and why it is gone. There is no
     * replacement check and no path that cannot proceed, so nothing here returns silently.
     *
     * **All four arms await their own answer** (reading-host re-typing T8b step 0). Three of them
     * used to hand the result back through `startActivityForResult(…, STD_REQUEST_CODE)`, whose
     * dispatcher lives only on `MainBibleActivity.onActivityResult`. Every caller of this function
     * passes an [ActivityBase] — `BibleJavascriptInterface`'s `CtrlKeyB`, `ReadingCommands`'
     * `composeStartKeyChooser` fallback, `CurrentPageManager.setCurrentDocument`'s
     * auto-open-after-switch (which uses `CurrentActivityHolder.currentActivity`, i.e. whatever is
     * resumed) and `MainBibleActivity` itself — so on every other host
     * `ActivityBase.onActivityResult` found no `resultByCode[1 - ASYNC_REQUEST_CODE_START]`, fell
     * through to `super` and DISCARDED the selection in silence. [ActivityBase.awaitIntent] is
     * resolved by `ActivityBase` itself and therefore works on all of them; it is what the StudyPad
     * arm below has always used, and the other three now match it. Since slice 8 B4 all four arms
     * self-launch nav-graph routes; each is intercepted by `navigateInsteadOfSelfLaunch` (F53) and
     * answered by the reading destination's collector, and backing out of a chooser is answered as
     * a cancel (B1).
     *
     * The answer is applied through [KeyChooserResults], the single implementation
     * `MainBibleActivity`'s surviving `STD_REQUEST_CODE` arms delegate to as well.
     */
    private fun startGeneralBookChooser(page: CurrentGeneralBookPage, context: ActivityBase) {
        context.lifecycleScope.launch(Dispatchers.Main) {
            val doc = page.currentDocument
            val fakeBookFactory = org.koin.java.KoinJavaComponent.get<FakeBookFactory>(FakeBookFactory::class.java)
            when {
                doc == fakeBookFactory.journalDocument -> {
                    // Screen.ManageLabels is deliberately not in ScreenLauncher.MIGRATED (its `data`
                    // argument is required), and the classic ManageLabelsComposeActivity
                    // ScreenLauncher.targetFor used to resolve it to is gone (nav-graph slices 2+4
                    // Task 7), so this builds the nav-host Intent directly. The "data" extra on the
                    // RESULT is unchanged -- NavResultIntents.forManageLabels still writes it under
                    // that key.
                    val data = ManageLabelsContract.ManageLabelsData(mode = ManageLabelsContract.Mode.STUDYPAD)
                        .applyFrom(workspaceSettings(page))
                        .toJSON()
                    val result = context.awaitIntent(
                        NavHostComposeActivity.intentFor(context, NavRoutes.manageLabels(data))
                    )
                    if(result.resultCode == Activity.RESULT_OK) {
                        val resultData = ManageLabelsContract.ManageLabelsData.fromJSON(result.data?.getStringExtra("data")!!)
                        workspaceSettings(page).updateFrom(resultData)
                    }
                }
                doc == fakeBookFactory.multiDocument ->
                    awaitChosenKey(page, context, NavHostComposeActivity.intentFor(context, NavRoutes.chooseDocument()))
                doc?.isMyDocument == true -> {
                    val docId = doc.myDocumentId
                    if (docId != null) {
                        // Screen.MyDocumentPages is deliberately not in ScreenLauncher.MIGRATED (all
                        // three of its route arguments are required), and its classic
                        // MyDocumentPagesComposeActivity is gone (deleted nav-graph slice 4 Task 9)
                        // -- so this builds the nav-host Intent directly rather than through
                        // ScreenLauncher, the same shape the StudyPad branch above already uses for
                        // ManageLabels.
                        awaitChosenKey(
                            page, context,
                            NavHostComposeActivity.intentFor(
                                context,
                                NavRoutes.myDocumentPages(
                                    documentId = docId.toString(),
                                    documentInitials = doc.initials,
                                    documentName = doc.name,
                                ),
                            ),
                        )
                    }
                }
                else -> awaitChosenKey(page, context, NavHostComposeActivity.intentFor(context, NavRoutes.CHOOSE_GENERAL_BOOK_KEY))
            }
        }
    }

    /**
     * Launch one key chooser and apply what it hands back, on whatever [ActivityBase] opened it.
     *
     * **Why the result is applied to [CurrentPage.pageManager] and not to `windowControl.activeWindowPageManager`
     * as classic's dispatcher did**: the same reason [workspaceSettings] is read off
     * `pageManager.window.windowRepository`. `windowControl`'s repository is whichever reading host
     * RESUMED last, not necessarily the one this page's window belongs to (the identity finding of
     * R6c1/R6d), while the page a chooser was opened FOR always knows its own manager. In the single
     * -host case the two are the same object, so this is classic's behaviour everywhere classic ran.
     *
     * **The cancel guard is classic's**, ported rather than dropped: `MainBibleActivity
     * .onActivityResult`'s first statement goes back in history when a cancelled `STD_REQUEST_CODE`
     * chooser has left the page with no key at all (a general book switched to but never opened).
     * [ActivityBase.awaitIntent] uses its own request codes, so that statement can no longer see
     * these four arms; it still covers every chooser that has not moved. `key` here is this page's
     * own, for the reason above; classic read `currentPage.key` off the active window.
     *
     * **When it resumes matters, and it is later than the dispatcher's was.** `awaitIntent`
     * completes its `CompletableDeferred` from `onActivityResult`, but this coroutine runs on
     * `Dispatchers.Main` (not `.immediate`), so the continuation is POSTED and runs after
     * `ActivityThread` has finished the same looper message — i.e. after the host's `onResume`.
     * On `NavHostComposeActivity` that is the difference between applying a key into a host that
     * has not yet reclaimed `windowControl.windowRepository`, re-declared itself foreground or
     * re-armed its bootstrap bridge, and one that has: the history entry that
     * `setCurrentDocumentAndKey` records is read by `HistoryManager.createHistoryItem` through
     * `ReadingViewVisibility.isVisible`, which is false until `ReadingHostPresence` says this host
     * is foreground. Classic needed `CurrentActivityHolder.activate` +
     * `ReadingHostPresence.setForeground` + `ReadingViewVisibility.setActivityVisible` in its
     * dispatcher precisely because `onActivityResult` runs BEFORE `onResume`; awaiting needs none of
     * them, and adds no pre-composition producer of history items — so
     * [net.bible.sharedcore.reading.ReadingViewVisibility]'s bootstrap-bridge invariant is untouched.
     */
    private suspend fun awaitChosenKey(page: CurrentGeneralBookPage, context: ActivityBase, intent: Intent) {
        val result = context.awaitIntent(intent)
        if (result.resultCode == Activity.RESULT_CANCELED) {
            if (page.key == null) context.goBackInHistory()
            return
        }
        KeyChooserResults.apply(page.pageManager, result.data?.extras, context.lifecycleScope)
    }


}
