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
package net.bible.service.common

import net.bible.android.AppDialogControllerResetRule
import android.Manifest
import android.os.Looper
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.yield
import kotlin.time.Duration.Companion.seconds
import net.bible.android.BibleApplication.Companion.application
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.activity.R
import net.bible.android.view.activity.base.ActivityBase
import net.bible.android.view.activity.discrete.CalculatorComposeActivity
import net.bible.android.view.activity.page.buyDevelopmentLink
import net.bible.sharedcore.ui.dialog.AppDialogController
import net.bible.sharedcore.ui.dialog.AppDialogRequest
import net.bible.sharedcore.ui.dialog.AppDialogResult
import org.crosswire.jsword.book.BookCategory
import org.crosswire.jsword.book.basic.AbstractBook
import org.crosswire.jsword.book.basic.DefaultBookMetaData
import org.crosswire.jsword.book.sword.processing.RawTextToXmlProcessor
import org.crosswire.jsword.passage.Key
import org.jdom2.Content
import org.junit.After
import org.junit.Rule
import org.junit.Before
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.java.KoinJavaComponent
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import java.util.Locale

/**
 * Task 9: `CommonUtils`' generic dialog helpers (`unlockDocument`, `showAbout`, `showHelpDialog`,
 * `checkPoorTranslations`, `requestNotificationPermission`, `documentUpgradeConfirmation`) go
 * through `AppDialogController` instead of building platform `AlertDialog`s. `showHelp` was out of
 * scope for Task 9 (run 3) -- it is covered here now, ported by Task 28 onto
 * `AppDialogRequest.Notice`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class CommonUtilsDialogsTest {
    private val controllers = mutableListOf<ActivityController<*>>()
    private val dialogs: AppDialogController get() = KoinJavaComponent.get(AppDialogController::class.java)
    @get:Rule val dialogReset = AppDialogControllerResetRule()
    private val originalLocale: Locale = Locale.getDefault()
    // Only the two `requestNotificationPermission*` tests below hop through `withContext
    // (Dispatchers.Main)`; the rest of this file never touches Main. Binding it here regardless
    // (one fresh instance per @Test, per JUnit4's default) is harmless for the other tests and lets
    // those two share the SAME scheduler their own `runTest(testDispatcher, ...)` drives, with Main
    // reset only in @After -- i.e. after every child either test launched has actually finished, not
    // from inside the test body's own `finally` (the shape that deadlocked
    // ErrorReportControlTest.selectingReportStartsBugReportReportBug: resetting Main while a
    // cancelled child was still mid-cancellation raced it onto the real, blocked Robolectric main
    // thread).
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUpMain() {
        Dispatchers.setMain(testDispatcher)
    }

    /**
     * A never-really-unlockable Book: `DefaultBookMetaData.unlock` always returns false. Only
     * `getOsis` is truly abstract in `AbstractBook`; the rest below are unused by the functions
     * under test and stubbed out.
     */
    private class FakeLockedBook(initials: String) : AbstractBook(
        DefaultBookMetaData(null, "Fake Locked Book", BookCategory.OTHER).apply { setInitials(initials) },
        null,
    ) {
        override fun getOsis(key: Key?, noOpRawTextProcessor: RawTextToXmlProcessor?): List<Content> = emptyList()
        override fun getGlobalKeyList(): Key = throw UnsupportedOperationException()
        override fun getValidKey(name: String?): Key = throw UnsupportedOperationException()
        override fun getKey(name: String?): Key = throw UnsupportedOperationException()
        override fun createEmptyKeyList(): Key = throw UnsupportedOperationException()
        override fun getOsisIterator(key: Key?, allowGenTitles: Boolean, noOpRawTextProcessor: Boolean): MutableIterator<Content> =
            throw UnsupportedOperationException()
        override fun contains(key: Key?): Boolean = false
        override fun getRawText(key: Key?): String = throw UnsupportedOperationException()
        override fun isWritable(): Boolean = false
        override fun setRawText(key: Key?, text: String?) = throw UnsupportedOperationException()
        override fun setAliasKey(alias: Key?, source: Key?) = throw UnsupportedOperationException()
    }

    /**
     * Records which thread called each Activity permission method, so a test can prove
     * `requestNotificationPermission` touches the Activity on main even when it is *started* from a
     * background dispatcher (fix round 1: `SpeakControl.prepareForSpeaking` calls it from
     * `GlobalScope.launch {}`, i.e. `Dispatchers.Default`).
     */
    private class ThreadRecordingActivity : ActivityBase() {
        val checkSelfPermissionThreads = mutableListOf<Thread>()
        val rationaleThreads = mutableListOf<Thread>()

        override fun checkSelfPermission(permission: String): Int {
            checkSelfPermissionThreads += Thread.currentThread()
            return super.checkSelfPermission(permission)
        }

        override fun shouldShowRequestPermissionRationale(permission: String): Boolean {
            rationaleThreads += Thread.currentThread()
            return super.shouldShowRequestPermissionRationale(permission)
        }
        // Activity.requestPermissions(String[], Int) is final on this SDK -- cannot be overridden to
        // record its thread. The test instead confirms the call happened via
        // ShadowActivity.getLastRequestedPermission() and relies on the two recordings above (both
        // BEFORE the dialog is even shown) to prove the fix: they fail under the pre-fix code because
        // the whole function ran off-main at that point.
    }

    @After
    fun tearDown() {
        controllers.forEach { runCatching { it.pause().stop().destroy() } }
        controllers.clear()
        dialogs.cancelAll()
        Locale.setDefault(originalLocale)
        androidx.preference.PreferenceManager.getDefaultSharedPreferences(application)
            .edit().remove("locale_pref").commit()
        CommonUtils.settings.removeString("poor-translations-dismissed")
        CommonUtils.settings.removeString("poor-translations-dismissed-version")
        Dispatchers.resetMain()
    }

    private fun activity() =
        Robolectric.buildActivity(CalculatorComposeActivity::class.java).also { controllers += it }.setup().get()

    /**
     * Establishes the UI locale the way production does: through the `locale_pref` preference, which
     * `ActivityBase.attachBaseContext` -> `LocaleHelper.localized` turns into `Locale.setDefault`
     * (fix batch 5 A3). A bare `Locale.setDefault` before building the activity is reset to the system locale.
     */
    private fun activityWithUiLocale(tag: String): ActivityBase {
        androidx.preference.PreferenceManager.getDefaultSharedPreferences(application)
            .edit().putString("locale_pref", tag).commit()
        return activity().also { assertEquals(tag, Locale.getDefault().toLanguageTag()) }
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    /** Lets a suspended `dialogs.await(...)` caller reach its next line on the test dispatcher. */
    private suspend fun advance() = yield()

    private suspend fun respondHead(result: AppDialogResult) {
        dialogs.respond(dialogs.pending.value!!.id, result)
        advance()
    }

    // -- unlockDocument --

    @Test
    fun unlockDocumentRaisesATextInputWithTodaysFields() = runTest(timeout = 10.seconds) {
        val activity = activity()
        val book = FakeLockedBook("LOCKED")
        val result = async { CommonUtils.unlockDocument(activity, book) }
        advance()
        val head = dialogs.pending.value!!.request as AppDialogRequest.TextInput
        assertEquals(application.getString(R.string.give_passphrase_for_module, "LOCKED"), head.title)
        assertNull(head.message)
        assertEquals("", head.initial)
        assertEquals(application.getString(R.string.okay), head.confirmText)
        assertEquals(application.getString(R.string.cancel), head.dismissText)
        assertEquals(application.getString(R.string.show_unlock_info), head.neutralText)
        assertFalse(head.cancellable)
        respondHead(AppDialogResult.Cancel)
        respondHead(AppDialogResult.Cancel) // "try again?" -> no
        assertEquals(false, result.await())
    }

    @Test
    fun unlockDocumentWrongPassphraseAsksTryAgain() = runTest(timeout = 10.seconds) {
        val activity = activity()
        val book = FakeLockedBook("LOCKED")
        val result = async { CommonUtils.unlockDocument(activity, book) }
        advance()
        respondHead(AppDialogResult.Text("wrong"))
        val retry = dialogs.pending.value!!.request as AppDialogRequest.Confirm
        assertEquals(application.getString(R.string.try_again_passphrase), retry.title)
        assertNull(retry.message)
        assertEquals(application.getString(R.string.yes), retry.confirmText)
        assertEquals(application.getString(R.string.no), retry.dismissText)
        assertFalse(retry.cancellable)
        respondHead(AppDialogResult.Cancel)
        assertEquals(false, result.await())
    }

    @Test
    fun unlockNeutralShowsAboutThenAsksAgain() = runTest(timeout = 10.seconds) {
        val activity = activity()
        val book = FakeLockedBook("LOCKED")
        val result = async { CommonUtils.unlockDocument(activity, book) }
        advance()
        respondHead(AppDialogResult.Neutral) // passphrase dialog
        assertTrue(dialogs.pending.value!!.request is AppDialogRequest.Message) // showAbout
        respondHead(AppDialogResult.Ok)
        assertTrue(dialogs.pending.value!!.request is AppDialogRequest.TextInput) // asked again
        respondHead(AppDialogResult.Cancel)
        assertTrue(dialogs.pending.value!!.request is AppDialogRequest.Confirm) // try again?
        respondHead(AppDialogResult.Cancel)
        assertEquals(false, result.await())
    }

    // -- showAbout --

    @Test
    fun showAboutRaisesANonCancellableMessageAndReturnsOnOk() = runTest(timeout = 10.seconds) {
        val activity = activity()
        val book = FakeLockedBook("LOCKED")
        val done = async { CommonUtils.showAbout(activity, book) }
        advance()
        val head = dialogs.pending.value!!.request as AppDialogRequest.Message
        assertNull(head.title)
        assertTrue(head.message.contains("Fake Locked Book"))
        assertEquals(application.getString(R.string.okay), head.confirmText)
        assertFalse(head.cancellable)
        respondHead(AppDialogResult.Ok)
        done.await()
    }

    // -- showHelpDialog --

    @Test
    fun showHelpDialogPostsACancellableMessage() {
        val activity = activity()
        CommonUtils.showHelpDialog(activity, R.string.help, R.string.help_search_text2, "search.html")
        idle()
        val head = dialogs.pending.value!!.request as AppDialogRequest.Message
        assertEquals(application.getString(R.string.help), head.title)
        assertTrue(head.message.startsWith(application.getString(R.string.help_search_text2)))
        assertTrue(head.message.contains("search.html"))
        assertEquals(application.getString(R.string.okay), head.confirmText)
        assertTrue(head.cancellable)
    }

    // -- showHelp --

    /**
     * Task 28: ported from an `AlertDialog.Builder` to `dialogs.post(AppDialogRequest.Notice(...))`
     * -- fire-and-forget (no caller waits on the single OK button), same block order as the brief:
     * the per-item help text, the full-docs link, the sponsor `IconLine`, and (only when
     * [showVersion]) the version line. `filterItems` narrows to one item so the assertions on
     * `blocks[0]` don't have to match every help topic's text.
     */
    @Test
    fun showHelpPostsANoticeWithTodaysBlocksAndAnOkOnlyButton() {
        val activity = activity()
        CommonUtils.showHelp(activity, filterItems = listOf(R.string.help_workspaces_title), showVersion = false)
        idle()

        val request = dialogs.pending.value!!.request as AppDialogRequest.Notice
        assertEquals(application.getString(R.string.help), request.title)
        assertTrue(request.showTitleLogo)
        assertEquals(3, request.blocks.size)

        val helpItems = request.blocks[0] as AppDialogRequest.NoticeBlock.Html
        assertTrue(helpItems.html.contains(application.getString(R.string.help_workspaces_title)))
        assertTrue(helpItems.html.contains(application.getString(R.string.help_workspaces_text)))

        val fullDocs = request.blocks[1] as AppDialogRequest.NoticeBlock.Html
        assertTrue(fullDocs.html.contains("https://andbible.org/docs/"))

        val sponsor = request.blocks[2] as AppDialogRequest.NoticeBlock.IconLine
        assertEquals(AppDialogRequest.NoticeIcon.Money, sponsor.icon)
        assertTrue(sponsor.html.contains(application.getString(R.string.buy_development)))
        assertTrue(sponsor.html.contains(buyDevelopmentLink))

        assertEquals(application.getString(android.R.string.ok), request.confirmText)
        assertNull("only one button today", request.dismissText)
        assertNull("only one button today", request.neutralText)
    }

    @Test
    fun showHelpWithShowVersionAppendsAVersionBlock() {
        val activity = activity()
        CommonUtils.showHelp(activity, filterItems = listOf(R.string.help_workspaces_title), showVersion = true)
        idle()

        val request = dialogs.pending.value!!.request as AppDialogRequest.Notice
        assertEquals(4, request.blocks.size)
        val versionBlock = request.blocks[3] as AppDialogRequest.NoticeBlock.Html
        assertTrue(versionBlock.html.contains(CommonUtils.applicationVersionName))
    }

    // -- checkPoorTranslations --

    @Test
    fun checkPoorTranslationsSkipsForAGoodLanguage() = runTest(timeout = 10.seconds) {
        Locale.setDefault(Locale.ENGLISH)
        val activity = activity()
        assertEquals(true, CommonUtils.checkPoorTranslations(activity))
        assertNull(dialogs.pending.value)
    }

    @Test
    fun checkPoorTranslationsProceedAnywayReturnsTrue() = runTest(timeout = 10.seconds) {
        val activity = activityWithUiLocale("xx")
        val result = async { CommonUtils.checkPoorTranslations(activity) }
        advance()
        val head = dialogs.pending.value!!.request as AppDialogRequest.Options
        assertNull(head.title)
        assertFalse(head.cancellable)
        assertEquals(3, head.options.size)
        val labels = head.options.map { it.label }
        assertEquals(
            listOf(
                application.getString(R.string.proceed_anyway),
                application.getString(R.string.beta_notice_dismiss_until_update),
                application.getString(R.string.close),
            ),
            labels,
        )
        respondHead(AppDialogResult.Selected(head.options[0].value))
        assertEquals(true, result.await())
    }

    @Test
    fun checkPoorTranslationsDismissUntilUpdateSetsPrefsAndReturnsTrue() = runTest(timeout = 10.seconds) {
        val activity = activityWithUiLocale("xx")
        val result = async { CommonUtils.checkPoorTranslations(activity) }
        advance()
        val head = dialogs.pending.value!!.request as AppDialogRequest.Options
        respondHead(AppDialogResult.Selected(head.options[1].value))
        assertEquals(true, result.await())
        assertEquals(Locale("xx").toLanguageTag(), CommonUtils.settings.getString("poor-translations-dismissed"))
        assertEquals(CommonUtils.mainVersion, CommonUtils.settings.getString("poor-translations-dismissed-version"))
    }

    @Test
    fun checkPoorTranslationsCloseFinishesActivityAndReturnsFalse() = runTest(timeout = 10.seconds) {
        val activity = activityWithUiLocale("xx")
        val result = async { CommonUtils.checkPoorTranslations(activity) }
        advance()
        val head = dialogs.pending.value!!.request as AppDialogRequest.Options
        respondHead(AppDialogResult.Selected(head.options[2].value))
        assertEquals(false, result.await())
        assertTrue(activity.isFinishing)
    }

    // -- requestNotificationPermission --
    // SDK 33 (TEST_SDK) is >= TIRAMISU; Robolectric grants POST_NOTIFICATIONS by default and
    // shouldShowRequestPermissionRationale() defaults to false, so no dialog is raised in this
    // configuration -- covered instead by a direct branch check that it never posts a request
    // when the permission is already granted / rationale is not needed.
    @Test
    fun requestNotificationPermissionDoesNotPromptWhenNoRationaleNeeded() = runTest(testDispatcher, timeout = 10.seconds) {
        val activity = activity()
        // Fix round 1 restored requestNotificationPermission's withContext(Dispatchers.Main). Called
        // directly (not via async) as below, that dispatch is a genuine cross-dispatcher post to the
        // AMBIENT Dispatchers.Main -- the real Robolectric-Looper-backed one when unset -- which
        // deadlocks runTest's own single-threaded event loop exactly like the original deadlock this
        // task hit (nothing can call idle() to drain it while this call is suspended waiting for it).
        // Binding Main (in @Before, to this same testDispatcher) to the scheduler this runTest itself
        // uses keeps the switch on the same cooperative scheduler runTest already drains itself, so
        // no manual pumping is needed at all.
        CommonUtils.requestNotificationPermission(activity)
        assertNull(dialogs.pending.value)
    }

    /**
     * Fix round 1 regression test: `SpeakControl.prepareForSpeaking` starts
     * `requestNotificationPermission` from `GlobalScope.launch {}` (`Dispatchers.Default`), not from
     * main. Every Activity call the function makes (`checkSelfPermission`,
     * `shouldShowRequestPermissionRationale`, `requestPermissions`) must still land on the main
     * thread. Drives the background coroutine with a bounded polling loop -- never blocks the
     * Robolectric main thread inside `runTest` while the code under test needs `Dispatchers.Main`
     * (the deadlock hit earlier in this task).
     */
    @Test
    fun requestNotificationPermissionTouchesTheActivityOnMainEvenFromABackgroundDispatcher() = runTest(testDispatcher, timeout = 10.seconds) {
        val activity = Robolectric.buildActivity(ThreadRecordingActivity::class.java).also { controllers += it }.setup().get()
        shadowOf(activity.packageManager).setShouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS, true)
        val mainThread = Thread.currentThread()

        // Dispatchers.Main is already routed (in @Before) to THIS runTest's own virtual
        // testDispatcher/scheduler instead of a real Robolectric Looper/Handler. A real Handler-based
        // Dispatchers.Main here (this is the only test in the file that genuinely posts to Main from a
        // real GlobalScope(Dispatchers.Default) background thread) left Dispatchers.Main's cached
        // resolution pointing at a Looper instance that the NEXT Robolectric test's fresh main-Looper
        // identity never drains again -- confirmed by two earlier versions of this test (one with a
        // real Handler dispatcher, one that also waited for the background job to finish) both hanging
        // the very next test in this class forever. Binding to the test scheduler keeps everything on
        // kotlinx-coroutines-test's own virtual machinery, the same mechanism `advance()`/`yield()`
        // already use safely everywhere else in this file, and never touches Robolectric's Looper at
        // all. Main is reset in @After, once every child (including the GlobalScope job below) has
        // actually finished -- not from a `finally` here, which is exactly the shape that deadlocked
        // ErrorReportControlTest.selectingReportStartsBugReportReportBug.
        val job = GlobalScope.launch(Dispatchers.Default) {
            CommonUtils.requestNotificationPermission(activity)
        }

        var waited = 0
        while (dialogs.pending.value == null && waited < 200) {
            advanceUntilIdle()
            Thread.sleep(10)
            waited++
        }
        assertTrue("the rationale dialog never arrived", dialogs.pending.value != null)

        // The two Activity calls made before the dialog is even shown must already be on main --
        // this is where the pre-fix code (no withContext(Dispatchers.Main)) fails: both are
        // recorded on the GlobalScope.Default background thread instead.
        assertTrue(activity.checkSelfPermissionThreads.isNotEmpty())
        assertTrue(activity.checkSelfPermissionThreads.all { it === mainThread })
        assertTrue(activity.rationaleThreads.isNotEmpty())
        assertTrue(activity.rationaleThreads.all { it === mainThread })

        val head = dialogs.pending.value!!.request as AppDialogRequest.Confirm
        assertEquals(activity.getString(R.string.permission_required), head.title)
        dialogs.respond(dialogs.pending.value!!.id, AppDialogResult.Ok)

        waited = 0
        while (shadowOf(activity).lastRequestedPermission == null && waited < 200) {
            advanceUntilIdle()
            Thread.sleep(10)
            waited++
        }
        // Functional round-trip proof: answering the (main-thread-raised) dialog actually reached
        // requestPermissions -- Activity.requestPermissions is final on this SDK, so its own
        // calling thread cannot be recorded directly, but by this point in the (fixed) function
        // every preceding Activity call was already confirmed to run on main above.
        assertEquals(
            Manifest.permission.POST_NOTIFICATIONS,
            shadowOf(activity).lastRequestedPermission?.requestedPermissions?.firstOrNull(),
        )

        // Make sure the background job has FULLY finished before this test method returns -- a
        // still-running real background thread left alive into the next test risks the same kind
        // of cross-test corruption the Handler-based attempt above hit.
        waited = 0
        while (!job.isCompleted && waited < 200) {
            advanceUntilIdle()
            Thread.sleep(10)
            waited++
        }
        assertTrue("background job never completed", job.isCompleted)
    }

    // -- documentUpgradeConfirmation --

    @Test
    fun documentUpgradeConfirmationRaisesANonCancellableConfirmWithTodaysTexts() = runTest(timeout = 10.seconds) {
        val activity = activity()
        val result = async { CommonUtils.documentUpgradeConfirmation(activity) }
        advance()
        val head = dialogs.pending.value!!.request as AppDialogRequest.Confirm
        assertEquals(application.getString(R.string.bookmark_warning), head.title)
        assertEquals(application.getString(R.string.yes), head.confirmText)
        assertEquals(application.getString(R.string.cancel), head.dismissText)
        assertFalse(head.cancellable)
        assertTrue(head.message!!.contains(application.getString(R.string.bookmark_warning2)))
        assertTrue(head.message!!.contains(application.getString(R.string.bookmark_warning3)))
        assertTrue(head.message!!.contains(application.getString(R.string.bookmark_warning4)))
        respondHead(AppDialogResult.Ok)
        assertEquals(true, result.await())
    }

    @Test
    fun documentUpgradeConfirmationCancelReturnsFalse() = runTest(timeout = 10.seconds) {
        val activity = activity()
        val result = async { CommonUtils.documentUpgradeConfirmation(activity) }
        advance()
        respondHead(AppDialogResult.Cancel)
        assertEquals(false, result.await())
    }
}
