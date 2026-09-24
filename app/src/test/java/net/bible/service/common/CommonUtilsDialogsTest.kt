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

import android.os.Looper
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlin.time.Duration.Companion.seconds
import net.bible.android.BibleApplication.Companion.application
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.activity.R
import net.bible.android.view.activity.discrete.CalculatorComposeActivity
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
 * through `AppDialogController` instead of building platform `AlertDialog`s. `showHelp` is out of
 * scope (run 3).
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class CommonUtilsDialogsTest {
    private val controllers = mutableListOf<ActivityController<*>>()
    private val dialogs: AppDialogController get() = KoinJavaComponent.get(AppDialogController::class.java)
    private val originalLocale: Locale = Locale.getDefault()

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

    @After
    fun tearDown() {
        controllers.forEach { runCatching { it.pause().stop().destroy() } }
        controllers.clear()
        dialogs.cancelAll()
        Locale.setDefault(originalLocale)
        CommonUtils.settings.removeString("poor-translations-dismissed")
        CommonUtils.settings.removeString("poor-translations-dismissed-version")
    }

    private fun activity() =
        Robolectric.buildActivity(CalculatorComposeActivity::class.java).also { controllers += it }.setup().get()

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
        Locale.setDefault(Locale("xx"))
        val activity = activity()
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
        Locale.setDefault(Locale("xx"))
        val activity = activity()
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
        Locale.setDefault(Locale("xx"))
        val activity = activity()
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
    fun requestNotificationPermissionDoesNotPromptWhenNoRationaleNeeded() = runTest(timeout = 10.seconds) {
        val activity = activity()
        CommonUtils.requestNotificationPermission(activity)
        idle()
        assertNull(dialogs.pending.value)
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
