package net.bible.sharedui.ios.ports

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import net.bible.android.control.download.DownloadControl
import net.bible.android.view.activity.navigation.DocRowMapper
import net.bible.sharedcore.platform.UserNotifier
import net.bible.sharedui.assembly.InMemorySecretStore
import net.bible.sharedui.assembly.IosAppConfig
import net.bible.sharedui.assembly.IosAppStartup
import net.bible.sharedui.assembly.IosAssemblyTest
import net.bible.sharedui.assembly.iosStringTable
import org.crosswire.jsword.book.Books
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import org.koin.mp.KoinPlatform
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertSame

class IosChooserHostPlatformTest {
    @Test fun docRowsMatchAndroidMapping() {
        IosAppStartup.start(IosAppConfig(root = IosAssemblyTest.sharedRoot, uiLanguage = "en",
            repositories = org.crosswire.common.util.PropertyMap(), secretStore = InMemorySecretStore()))
        try {
            runBlocking { IosAppStartup.openDatabases() }
            val koin = KoinPlatform.getKoin()
            val book = assertNotNull(Books.installed().getBook("K4RawText"))
            val books = listOf(book)
            val platform = IosChooserHostPlatform(koin, CoroutineScope(Dispatchers.Unconfined), { IosAppStartup.stringTable })
            assertEquals(listOf(DocRowMapper(koin.get<DownloadControl>(), books).toDocRow(book)), platform.docRows(books))
        } finally { runBlocking { IosAppStartup.resetForTest() } }
    }

    @Test fun errorsUseCurrentLanguageAndOriginalCause() {
        val messages = mutableListOf<String>()
        val causes = mutableListOf<Throwable?>()
        val app = koinApplication { modules(module { single<UserNotifier> { object : UserNotifier {
            override fun showInfo(message: String, long: Boolean) = error("unexpected info")
            override fun showError(message: String, cause: Throwable?) { messages += message; causes += cause }
        } } }) }
        try {
            var table = iosStringTable("en")
            val platform = IosChooserHostPlatform(app.koin, CoroutineScope(Dispatchers.Unconfined), { table })
            val cause = IllegalStateException("chooser failure")
            platform.showError(cause)
            table = iosStringTable("fi")
            platform.showError(cause)
            assertEquals(listOf(iosStringTable("en").text("error_occurred"), table.text("error_occurred")), messages)
            assertSame(cause, causes[0])
            assertSame(cause, causes[1])
        } finally { app.close() }
    }
}
