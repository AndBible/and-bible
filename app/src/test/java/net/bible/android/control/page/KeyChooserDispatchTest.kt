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

    @Test fun explicitHostIsUsedInsteadOfAnotherLiveHost() {
        val touched = host()
        val other = host()
        assertTrue(AndroidKeyChooserLauncher { touched }.startKeyChooser(touched.hostWindowRepository.activeWindow.pageManager.currentDictionary))
        assertEquals(NavRoutes.CHOOSE_DICTIONARY_WORD, assertNotNull(shadowOf(touched).nextStartedActivityForResult).intent.getStringExtra(NavHostComposeActivity.EXTRA_ROUTE))
        assertNull(shadowOf(other).nextStartedActivityForResult)
    }
}
