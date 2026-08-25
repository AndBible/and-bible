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
package net.bible.android.view.activity.settings

import android.content.res.XmlResourceParser
import net.bible.android.TEST_SDK
import net.bible.android.activity.R
import net.bible.sharedui.settingsDrawableRes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.xmlpull.v1.XmlPullParser

private const val ANDROID_NS = "http://schemas.android.com/apk/res/android"

/**
 * Keys classic builds but the Compose port deliberately does not, so parity cannot be required for
 * them. `sync_enable_readingplans` is hidden at runtime by classic itself (`SyncSettings.kt`) and is
 * absent from `SyncCategoryKeys.DISPLAY`.
 */
private val NOT_PORTED = setOf("sync_enable_readingplans")

/**
 * Pins [settingsDrawableRes] to the classic preference XML by READING that XML at test time, rather
 * than to a hand-copied table (which is what `TextDisplaySettingsIconsTest` must do, its classic
 * source being Kotlin). A future edit to `settings.xml` — a new preference, a changed icon — fails
 * here instead of silently shipping a Compose screen that has drifted from classic.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class, sdk = [TEST_SDK])
class SettingsIconParityTest {

    private data class ClassicRow(val key: String, val iconResId: Int)

    private fun classicRows(xmlRes: Int): List<ClassicRow> {
        val parser: XmlResourceParser = RuntimeEnvironment.getApplication().resources.getXml(xmlRes)
        val rows = mutableListOf<ClassicRow>()
        var ev = parser.eventType
        while (ev != XmlPullParser.END_DOCUMENT) {
            if (ev == XmlPullParser.START_TAG) {
                val key = parser.getAttributeValue(ANDROID_NS, "key")
                if (key != null) {
                    rows += ClassicRow(key, parser.getAttributeResourceValue(ANDROID_NS, "icon", 0))
                }
            }
            ev = parser.next()
        }
        parser.close()
        return rows
    }

    /**
     * @param expectedIconRows how many ported rows this screen has an icon for. Asserted exactly, so
     * a parser that silently yields nothing fails loudly instead of passing vacuously.
     */
    private fun assertParity(xmlRes: Int, expectedIconRows: Int) {
        val res = RuntimeEnvironment.getApplication().resources
        val name = { id: Int -> if (id == 0) "none" else res.getResourceEntryName(id) }
        val rows = classicRows(xmlRes).filterNot { it.key in NOT_PORTED }
        val withIcon = rows.filter { it.iconResId != 0 }
        assertEquals("icon-bearing row count in ${res.getResourceEntryName(xmlRes)}", expectedIconRows, withIcon.size)

        val wrong = withIcon.mapNotNull { row ->
            val mapped = settingsDrawableRes(row.key)
            if (mapped == row.iconResId) null
            else "${row.key}: classic=${name(row.iconResId)} table=${mapped?.let(name) ?: "none"}"
        }
        assertTrue("settingsDrawableRes disagrees with classic: $wrong", wrong.isEmpty())

        val extra = rows.filter { it.iconResId == 0 && settingsDrawableRes(it.key) != null }.map { it.key }
        assertTrue("rows classic leaves iconless must stay iconless: $extra", extra.isEmpty())
    }

    @Test fun syncSettingsIconsMatchClassic() = assertParity(R.xml.sync_settings, expectedIconRows = 18)

    @Test fun appSettingsIconsMatchClassic() = assertParity(R.xml.settings, expectedIconRows = 41)

    @Test fun readingProgressSettingsIconsMatchClassic() =
        assertParity(R.xml.reading_progress_settings, expectedIconRows = 6)
}
