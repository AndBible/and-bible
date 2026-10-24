package net.bible.android.control.page

import android.app.Activity
import android.content.Intent
import android.os.Looper
import net.bible.android.view.activity.page.ActivityResultKind
import net.bible.service.download.FakeBookFactory
import org.crosswire.jsword.book.Books
import androidx.test.core.app.ApplicationProvider
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.platform.AndroidKeyChooserLauncher
import net.bible.android.view.activity.base.ActivityBase
import net.bible.android.view.activity.nav.NavHostComposeActivity
import net.bible.sharedcore.nav.NavRoutes
import net.bible.sharedcore.reading.ReadingHostPresence
import net.bible.sharedcore.reading.ReadingViewVisibility
import net.bible.test.DatabaseResetter
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Route assertions catch a launcher dispatching a page to the wrong chooser. */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class KeyChooserDispatchTest {
    private val controllers = mutableListOf<ActivityController<NavHostComposeActivity>>()

    @After
    fun tearDown() {
        controllers.forEach { it.close() }
        ReadingHostPresence.setForeground(null)
        ReadingViewVisibility.setVisible(false)
        DatabaseResetter.resetDatabase()
    }

    // Created-only: no composed navController intercepts the actual platform Intent.
    private fun host(): NavHostComposeActivity = Robolectric.buildActivity(
        NavHostComposeActivity::class.java,
        NavHostComposeActivity.intentFor(ApplicationProvider.getApplicationContext(), NavRoutes.READING),
    ).also { controllers += it }.create().get()

    private fun assertRoute(select: (CurrentPageManager) -> CurrentPage, route: String) {
        val activity = host()
        val page = select(activity.hostWindowRepository.activeWindow.pageManager)
        assertTrue(AndroidKeyChooserLauncher { activity }.startKeyChooser(page))
        shadowOf(Looper.getMainLooper()).idle()
        val started = assertNotNull(shadowOf(activity).nextStartedActivityForResult)
        assertEquals(route, started.intent.getStringExtra(NavHostComposeActivity.EXTRA_ROUTE))
        if (page !is CurrentGeneralBookPage) assertEquals(ActivityBase.STD_REQUEST_CODE, started.requestCode)
    }

    @Test fun bibleUsesScriptureGrid() = assertRoute({ it.currentBible }, NavRoutes.gridChoosePassage(isScripture = true))
    @Test fun commentaryUsesScriptureGrid() = assertRoute({ it.currentCommentary }, NavRoutes.gridChoosePassage(isScripture = true))
    @Test fun myNotesInheritCommentaryChooser() = assertRoute({ it.currentMyNotePage }, NavRoutes.gridChoosePassage(isScripture = true))
    @Test fun dictionaryUsesDictionaryChooser() = assertRoute({ it.currentDictionary }, NavRoutes.CHOOSE_DICTIONARY_WORD)
    @Test fun mapUsesMapChooser() = assertRoute({ it.currentMap }, NavRoutes.CHOOSE_MAP_KEY)
    @Test fun plainGeneralBookUsesGeneralBookChooser() = assertRoute({ it.currentGeneralBook }, NavRoutes.CHOOSE_GENERAL_BOOK_KEY)

    @Test fun absentHostReturnsFalseAndStartsNothing() {
        val activity = host()
        assertFalse(AndroidKeyChooserLauncher { null }.startKeyChooser(activity.hostWindowRepository.activeWindow.pageManager.currentBible))
        shadowOf(Looper.getMainLooper()).idle()
        assertNull(shadowOf(activity).nextStartedActivityForResult)
    }

    @Test fun multiDocumentResultIsPostedAndAppliedToTheOwningPageManager() {
        val activity = host()
        val manager = activity.hostWindowRepository.activeWindow.pageManager
        val other = host()
        val foreignManager = other.hostWindowRepository.activeWindow.pageManager
        val foreignDocument = foreignManager.currentPage.currentDocument
        val factory = org.koin.java.KoinJavaComponent.get<FakeBookFactory>(FakeBookFactory::class.java)
        manager.currentGeneralBook.onlySetCurrentDocument(factory.multiDocument)
        AndroidKeyChooserLauncher { activity }.startKeyChooser(manager.currentGeneralBook)
        shadowOf(Looper.getMainLooper()).idle()
        val started = assertNotNull(shadowOf(activity).nextStartedActivityForResult)
        assertEquals(NavRoutes.chooseDocument(), started.intent.getStringExtra(NavHostComposeActivity.EXTRA_ROUTE))
        val chosen = assertNotNull(Books.installed().getBook("ESV2011"))
        val before = manager.currentPage.currentDocument
        activity.onActivityResult(started.requestCode, Activity.RESULT_OK, Intent()
            .putExtra(ActivityResultKind.EXTRA, ActivityResultKind.ChooseDocument.name)
            .putExtra("book", chosen.initials))
        assertEquals(before, manager.currentPage.currentDocument, "the Main continuation must be posted, not applied inside onActivityResult")
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(chosen, manager.currentPage.currentDocument)
        assertEquals(foreignDocument, foreignManager.currentPage.currentDocument, "another live host must not receive this selection")
    }

    class CancelProbeActivity : ActivityBase() {
        var backCount = 0
        override fun goBackInHistory(): Boolean { backCount++; return true }
        public override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) =
            super.onActivityResult(requestCode, resultCode, data)
    }

    @Test fun cancellingWithoutAKeyGoesBackButCancellingWithAKeyDoesNot() {
        val activity = host()
        val page = activity.hostWindowRepository.activeWindow.pageManager.currentGeneralBook
        val probe = Robolectric.buildActivity(CancelProbeActivity::class.java).create()
        try {
            page.doSetKey(null)
            AndroidKeyChooserLauncher { probe.get() }.startKeyChooser(page)
            shadowOf(Looper.getMainLooper()).idle()
            val started = assertNotNull(shadowOf(probe.get()).nextStartedActivityForResult)
            probe.get().onActivityResult(started.requestCode, Activity.RESULT_CANCELED, null)
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(1, probe.get().backCount)

            page.doSetKey(org.crosswire.jsword.passage.DefaultKeyList())
            AndroidKeyChooserLauncher { probe.get() }.startKeyChooser(page)
            shadowOf(Looper.getMainLooper()).idle()
            val second = assertNotNull(shadowOf(probe.get()).nextStartedActivityForResult)
            probe.get().onActivityResult(second.requestCode, Activity.RESULT_CANCELED, null)
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(1, probe.get().backCount, "a cancellation with an existing key must not navigate history")
        } finally {
            probe.close()
        }
    }

    private fun withForeignSingleton(other: NavHostComposeActivity, action: () -> Unit) {
        val original = org.koin.core.context.GlobalContext.get().get<KeyChooserLauncher>()
        var singletonCalls = 0
        val foreign = AndroidKeyChooserLauncher { other }
        org.koin.core.context.loadKoinModules(org.koin.dsl.module {
            single<KeyChooserLauncher> { object : KeyChooserLauncher {
                override fun startKeyChooser(page: CurrentPage): Boolean {
                    singletonCalls++
                    return foreign.startKeyChooser(page)
                }
            } }
        })
        assertTrue(org.koin.core.context.GlobalContext.get().get<KeyChooserLauncher>() !== original)
        try {
            action()
            assertEquals(0, singletonCalls, "explicit-host callers must not use the foreground singleton")
        } finally {
            org.koin.core.context.loadKoinModules(org.koin.dsl.module { single<KeyChooserLauncher> { original } })
        }
    }

    @Test fun readingCommandsFallbackUsesTouchedHostNotForegroundHost() {
        val touched = host()
        val other = host()
        val holder = net.bible.android.view.activity.base.CurrentActivityHolder
        shadowOf(Looper.getMainLooper()).idle()
        holder.deactivate(other)
        holder.activate(other)
        assertTrue(holder.currentActivity === other, "fixture must publish a different foreground host")
        assertNull(shadowOf(touched).nextStartedActivityForResult, "caller must be the first launch")
        assertNull(shadowOf(other).nextStartedActivityForResult)
        try {
            withForeignSingleton(other) {
                touched.readingCommands.composeStartKeyChooser()
                shadowOf(Looper.getMainLooper()).idle()
            }
            assertNotNull(shadowOf(touched).nextStartedActivityForResult)
            assertNull(shadowOf(other).nextStartedActivityForResult)
        } finally { holder.deactivate(other) }
    }

    @Test fun ctrlKeyBUsesTouchedHostAndTheWebViewsWindow() {
        val touched = host()
        val other = host()
        val holder = net.bible.android.view.activity.base.CurrentActivityHolder
        val window = touched.hostWindowRepository.activeWindow
        // A different page from the globally active host makes wrong-window dispatch observable.
        CurrentPageManager::class.java.getDeclaredField("currentPage").apply {
            isAccessible = true
            set(window.pageManager, window.pageManager.currentDictionary)
        }
        val factory = touched.readingCommands.bibleViewFactory
        val view = net.bible.android.view.activity.page.BibleView(touched, factory.hostCallbacks,
            java.lang.ref.WeakReference(window), factory.windowControl, factory.pageControl,
            net.bible.android.control.page.PageTiltScrollControl(), factory.linkControl, factory.bookmarkControl,
            factory.downloadControl, factory.searchControl)
        shadowOf(Looper.getMainLooper()).idle()
        holder.deactivate(other)
        holder.activate(other)
        assertTrue(holder.currentActivity === other, "fixture must publish a different foreground host")
        assertNull(shadowOf(touched).nextStartedActivityForResult, "caller must be the first launch")
        assertNull(shadowOf(other).nextStartedActivityForResult)
        try {
            withForeignSingleton(other) {
                net.bible.android.view.activity.page.BibleJavascriptInterface(view).onKeyDown("CtrlKeyB")
                shadowOf(Looper.getMainLooper()).idle()
            }
            assertEquals(NavRoutes.CHOOSE_DICTIONARY_WORD,
                assertNotNull(shadowOf(touched).nextStartedActivityForResult).intent.getStringExtra(NavHostComposeActivity.EXTRA_ROUTE))
            assertNull(shadowOf(other).nextStartedActivityForResult)
        } finally {
            holder.deactivate(other)
            net.bible.android.view.activity.page.BibleView::class.java.getDeclaredField("pageTiltScroller").apply {
                isAccessible = true
                set(view, net.bible.android.view.activity.page.screen.PageTiltScroller(view, net.bible.android.control.page.PageTiltScrollControl()))
            }
            view.destroy()
        }
    }

    @Test fun pageManagerAutoOpenUsesKoinForegroundHostAndToleratesNoHost() {
        val owning = host()
        val foreground = host()
        val manager = owning.hostWindowRepository.activeWindow.pageManager
        val holder = net.bible.android.view.activity.base.CurrentActivityHolder
        val dictionary = assertNotNull(Books.installed().getBook("StrongsGreek"))
        val original = org.koin.core.context.GlobalContext.get().get<KeyChooserLauncher>()
        var portCalls = 0
        val recording = object : KeyChooserLauncher {
            override fun startKeyChooser(page: CurrentPage): Boolean {
                portCalls++
                return original.startKeyChooser(page)
            }
        }
        val override = org.koin.dsl.module { single<KeyChooserLauncher> { recording } }
        org.koin.core.context.loadKoinModules(override)
        holder.activate(foreground)
        try {
            manager.setCurrentDocument(dictionary)
            assertEquals(1, portCalls, "auto-open must resolve the Koin port")
            assertEquals(NavRoutes.CHOOSE_DICTIONARY_WORD, assertNotNull(shadowOf(foreground).nextStartedActivityForResult).intent.getStringExtra(NavHostComposeActivity.EXTRA_ROUTE))
            assertNull(shadowOf(owning).nextStartedActivityForResult)
            holder.deactivate(foreground)
            holder.forgetActivitiesForTest()
            manager.setCurrentDocument(assertNotNull(Books.installed().getBook("KJV")))
            manager.currentDictionary.doSetKey(null)
            manager.setCurrentDocument(dictionary)
            assertEquals(2, portCalls)
            assertNull(shadowOf(foreground).nextStartedActivityForResult)
            assertNull(shadowOf(owning).nextStartedActivityForResult)
        } finally {
            org.koin.core.context.loadKoinModules(org.koin.dsl.module { single<KeyChooserLauncher> { original } })
            holder.deactivate(foreground)
        }
    }

    @Test fun myDocumentRouteCarriesDocumentIdentityAndNames() {
        val activity = host()
        val document = net.bible.android.database.mydocument.MyDocument(name = "Chooser document", initials = "MyDoc_chooser")
        val book = net.bible.service.sword.mydocument.createMyDocumentBook(document)
        val page = activity.hostWindowRepository.activeWindow.pageManager.currentGeneralBook
        Books.installed().addBook(book)
        try {
            page.onlySetCurrentDocument(book)
            AndroidKeyChooserLauncher { activity }.startKeyChooser(page)
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(NavRoutes.myDocumentPages(document.id.toString(), document.initials, document.name),
                assertNotNull(shadowOf(activity).nextStartedActivityForResult).intent.getStringExtra(NavHostComposeActivity.EXTRA_ROUTE))
        } finally { Books.installed().removeBook(book) }
    }

    @Test fun studyPadWritebackReReadsReplacedOwningSettings() {
        val activity = host()
        val other = host()
        val repo = activity.hostWindowRepository
        val old = repo.workspaceSettings
        val oldMarker = net.bible.android.database.IdType()
        old.autoAssignPrimaryLabel = oldMarker
        val page = repo.activeWindow.pageManager.currentGeneralBook
        val factory = org.koin.java.KoinJavaComponent.get<FakeBookFactory>(FakeBookFactory::class.java)
        page.onlySetCurrentDocument(factory.journalDocument)
        AndroidKeyChooserLauncher { activity }.startKeyChooser(page)
        shadowOf(Looper.getMainLooper()).idle()
        val started = assertNotNull(shadowOf(activity).nextStartedActivityForResult)
        val replacement = net.bible.android.database.WorkspaceEntities.WorkspaceSettings.default
        repo.workspaceSettings = replacement
        val foreignBefore = other.hostWindowRepository.workspaceSettings.autoAssignPrimaryLabel
        val resultMarker = net.bible.android.database.IdType()
        val data = net.bible.android.view.activity.bookmark.ManageLabelsContract.ManageLabelsData(
            mode = net.bible.android.view.activity.bookmark.ManageLabelsContract.Mode.STUDYPAD,
            autoAssignPrimaryLabel = resultMarker)
        activity.onActivityResult(started.requestCode, Activity.RESULT_OK, Intent().putExtra("data", data.toJSON()))
        assertNull(replacement.autoAssignPrimaryLabel)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(resultMarker, replacement.autoAssignPrimaryLabel)
        assertEquals(oldMarker, old.autoAssignPrimaryLabel)
        assertEquals(foreignBefore, other.hostWindowRepository.workspaceSettings.autoAssignPrimaryLabel)
    }

    @Test fun destroyedHostDoesNotApplyAwaitedStudyPadResult() {
        val activity = host()
        val page = activity.hostWindowRepository.activeWindow.pageManager.currentGeneralBook
        val settings = activity.hostWindowRepository.workspaceSettings
        page.onlySetCurrentDocument(org.koin.java.KoinJavaComponent.get<FakeBookFactory>(FakeBookFactory::class.java).journalDocument)
        AndroidKeyChooserLauncher { activity }.startKeyChooser(page)
        shadowOf(Looper.getMainLooper()).idle()
        val started = assertNotNull(shadowOf(activity).nextStartedActivityForResult)
        val marker = net.bible.android.database.IdType()
        val before = settings.autoAssignPrimaryLabel
        controllers.first { it.get() === activity }.destroy()
        val result = net.bible.android.view.activity.bookmark.ManageLabelsContract.ManageLabelsData(
            mode = net.bible.android.view.activity.bookmark.ManageLabelsContract.Mode.STUDYPAD,
            autoAssignPrimaryLabel = marker)
        activity.onActivityResult(started.requestCode, Activity.RESULT_OK, Intent().putExtra("data", result.toJSON()))
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(before, settings.autoAssignPrimaryLabel)
    }

    @Test fun explicitHostIsUsedInsteadOfAnotherLiveHost() {
        val touched = host()
        val other = host()
        assertTrue(AndroidKeyChooserLauncher { touched }.startKeyChooser(touched.hostWindowRepository.activeWindow.pageManager.currentDictionary))
        assertEquals(NavRoutes.CHOOSE_DICTIONARY_WORD, assertNotNull(shadowOf(touched).nextStartedActivityForResult).intent.getStringExtra(NavHostComposeActivity.EXTRA_ROUTE))
        assertNull(shadowOf(other).nextStartedActivityForResult)
    }
}
