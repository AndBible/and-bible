package net.bible.android.control.search

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.nhaarman.mockitokotlin2.mock
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.control.buildCoreModule
import net.bible.sharedcore.platform.AppSettings
import net.bible.sharedcore.search.BibleSearchService
import net.bible.sharedcore.search.StyledRun
import net.bible.sharedcore.search.StyledText
import fi.sykero.kdom.Element
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class BibleSearchServiceImplTest {
    @Test fun `runtime graph resolves Android highlighter and search service persists through injected settings`() {
        val values = mutableMapOf<String, String?>()
        val settings = object : AppSettings by mock<AppSettings>() {
            override fun setString(key: String, value: String?) { values[key] = value }
        }
        val app = koinApplication {
            androidContext(ApplicationProvider.getApplicationContext<Context>())
            modules(buildCoreModule(), module { single<AppSettings> { settings } })
        }
        try {
            val highlighter = app.koin.get<SearchTextHighlighter>()
            assertSame(SearchHighlighter, highlighter)
            val osis = Element("div").addContent(Element("verse").setText("God loves"))
            assertEquals(StyledText(listOf(StyledRun("God", highlight = true), StyledRun(" loves"))),
                highlighter.highlight(osis, listOf("God"), emptyList()))
            val service = app.koin.get<BibleSearchService>()
            assertSame(app.koin.get<BibleSearchServiceImpl>(), service)
            service.persistSelection(listOf("KJV", "ESV2011"), false)
            service.persistSelection(listOf("NASB"), true)
            assertEquals(mapOf(SearchControl.SEARCH_TRANSLATIONS_PREF to "KJV,ESV2011",
                SearchControl.STRONGS_SEARCH_TRANSLATIONS_PREF to "NASB"), values)
            service.persistSelection(emptyList(), false)
            assertEquals("", values[SearchControl.SEARCH_TRANSLATIONS_PREF])
            assertEquals("NASB", values[SearchControl.STRONGS_SEARCH_TRANSLATIONS_PREF])
        } finally {
            app.close()
        }
    }
}
