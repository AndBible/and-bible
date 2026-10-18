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


package net.bible.android.view.activity.page

import java.io.File
import net.bible.test.ProductionSources
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guard: [BibleJavascriptInterface] is only the Android adapter around the shared `ReadingBridge`
 * (`BibleBridge by readingBridge`) plus the Android-only `@JavascriptInterface` `getActiveLanguages`.
 * A bridge method that is overridden here again, or a reference to one of the controllers that the
 * shared families own, means behaviour crept back out of common code.
 */
class BibleJavascriptInterfaceIsAdapterTest {
    private val forbiddenReferences = Regex("""BookmarkJsActions|ProgressJsActions|SpeakControl|LinkControl""")
    private val overrideFun = Regex("""\boverride\s+fun\s+(\w+)""")

    /** [src] with block comments and whole-line `//` comments removed, so only code is scanned. */
    private fun codeOf(src: String): String =
        src.replace(Regex("""/\*[\s\S]*?\*/"""), "")
            .lineSequence().filterNot { it.trimStart().startsWith("//") }.joinToString("\n")

    /** The violations found in [src]; empty when it is a thin adapter. */
    private fun violations(src: String): List<String> {
        val code = codeOf(src)
        val overrides = overrideFun.findAll(code).map { "override fun ${it.groupValues[1]}" }.toList()
        val refs = forbiddenReferences.findAll(code).map { "reference ${it.value}" }.toList()
        return overrides + refs
    }

    @Test fun theAdapterOverridesNothingAndReachesNoSharedController() {
        val file = ProductionSources.resolve("src/main/java/net/bible/android/view/activity/page/BibleJavascriptInterface.kt")
        assertTrue("source not found: ${file.absolutePath}", file.exists() && file.length() > 0)
        assertEquals(emptyList<String>(), violations(file.readText()))
    }

    @Test fun theGuardSeesAPlantedOverrideAndAPlantedController() {
        val planted = """
            class BibleJavascriptInterface {
                private val speak: SpeakControl by inject()
                override fun shareVerse(a: String) {}
            }
        """.trimIndent()
        assertEquals(listOf("override fun shareVerse", "reference SpeakControl"), violations(planted))
    }

    @Test fun commentsAreNotCountedAsViolations() {
        val src = """
            /** Used to override fun shareVerse via SpeakControl. */
            // override fun helpDialog
            class X { fun getActiveLanguages() = "" }
        """.trimIndent()
        assertEquals(emptyList<String>(), violations(src))
    }
}
