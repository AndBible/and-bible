package net.bible.android.control.document

import net.bible.android.control.page.window.WindowControl
import net.bible.android.database.SwordDocumentInfoDao
import net.bible.sharedcore.platform.AppCoroutineScope
import net.bible.sharedcore.platform.AppSettings
import net.bible.sharedcore.platform.CoreStrings
import net.bible.sharedcore.platform.UserNotifier
import org.crosswire.jsword.book.BookException
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.Mockito

/** [DocumentControl.enableManualInstallFolder] runs the injected platform step and reports a [BookException] to the user. */
class DocumentControlManualInstallTest {
    private val errors = mutableListOf<String>()
    private val notifier = object : UserNotifier {
        override fun showInfo(message: String, long: Boolean) = Unit
        override fun showError(message: String, cause: Throwable?) { errors += message }
    }
    private val strings = Mockito.mock(CoreStrings::class.java).also {
        Mockito.`when`(it.errorOccurred).thenReturn("FAKE error occurred")
    }

    private fun control(enable: () -> Unit) = DocumentControl(
        fakeBookFactory = net.bible.service.download.FakeBookFactory(Mockito.mock(CoreStrings::class.java)),
        Mockito.mock(WindowControl::class.java), Mockito.mock(AppSettings::class.java), notifier, strings,
        AppCoroutineScope(), deleteFiles = DocumentFileDeleter { }, backupDaoProvider = { Mockito.mock(SwordDocumentInfoDao::class.java) },
        enableManualInstallFolder = enable,
    )

    @Test fun enableManualInstallFolderShowsErrorOnBookException() {
        control { throw BookException("boom") }.enableManualInstallFolder()
        assertEquals(listOf("FAKE error occurred"), errors)
    }

    @Test fun enableManualInstallFolderRunsTheStepAndStaysSilentOnSuccess() {
        var calls = 0
        control { calls++ }.enableManualInstallFolder()
        assertEquals(1, calls)
        assertEquals(emptyList<String>(), errors)
    }
}
