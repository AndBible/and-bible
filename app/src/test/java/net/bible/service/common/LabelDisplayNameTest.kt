package net.bible.service.common

import java.util.Locale
import net.bible.android.BibleApplication
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.activity.R
import net.bible.android.database.bookmarks.AI_LABEL_NAME
import net.bible.android.database.bookmarks.BookmarkEntities
import net.bible.android.database.bookmarks.PARAGRAH_BREAK_LABEL_NAME
import net.bible.android.database.bookmarks.SPEAK_LABEL_NAME
import net.bible.android.database.bookmarks.UNLABELED_NAME
import net.bible.android.platform.AndroidLabelNameStrings
import net.bible.sharedlogic.platform.LabelNameStrings
import net.bible.test.KoinFactorySnapshot
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.Koin
import org.koin.core.context.GlobalContext
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK], qualifiers = "en")
class LabelDisplayNameTest {
    private val app get() = BibleApplication.application
    private val savedDefault = Locale.getDefault()

    // The process-wide Koin keeps the Application of the JVM's first test (startKoin runs once), whose
    // resources do not follow this test's locale_pref: bind a port built from the current Application.
    private lateinit var koin: Koin
    private lateinit var stringsSnapshot: KoinFactorySnapshot

    @Before fun bindCurrentApplicationStrings() {
        koin = GlobalContext.get()
        stringsSnapshot = KoinFactorySnapshot.of<LabelNameStrings>(koin)
        koin.loadModules(listOf(module { single<LabelNameStrings> { AndroidLabelNameStrings(app) } }), allowOverride = true)
    }

    private fun setPref(v: String?) = CommonUtils.realSharedPreferences.edit().apply {
        if (v == null) remove("locale_pref") else putString("locale_pref", v)
    }.commit()

    @After fun reset() {
        try { setPref(null) } finally { Locale.setDefault(savedDefault); stringsSnapshot.restore() }
    }

    private fun label(name: String) = BookmarkEntities.Label(name = name)

    @Test fun specialLabelsShowTheirResourceTexts() {
        assertEquals(app.getString(R.string.speak), label(SPEAK_LABEL_NAME).displayName)
        assertEquals(app.getString(R.string.label_unlabelled), label(UNLABELED_NAME).displayName)
        assertEquals(app.getString(R.string.add_paragraph_break), label(PARAGRAH_BREAK_LABEL_NAME).displayName)
        assertEquals(app.getString(R.string.ai_label), label(AI_LABEL_NAME).displayName)
    }

    @Test fun aUserLabelShowsItsName() {
        assertEquals("My label", label("My label").displayName)
    }

    @Test fun specialTextsFollowTheAppLocale() {
        val enSpeak = label(SPEAK_LABEL_NAME).displayName
        setPref("fi")
        assertEquals("Puhe", label(SPEAK_LABEL_NAME).displayName)
        assertEquals("Ei kirjanmerkkiryhmää", label(UNLABELED_NAME).displayName)
        assertEquals("Kappalejako", label(PARAGRAH_BREAK_LABEL_NAME).displayName)
        assertEquals("Tekoäly", label(AI_LABEL_NAME).displayName)
        assertEquals("My label", label("My label").displayName)
        setPref(null)
        assertEquals(enSpeak, label(SPEAK_LABEL_NAME).displayName)
        assertNotEquals("Puhe", label(SPEAK_LABEL_NAME).displayName)
    }
}
