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
package net.bible.sharedlogic.addons

import net.bible.service.sword.AndBibleAddonFilter
import net.bible.service.sword.csvprompt.addCsvPromptBook
import net.bible.service.sword.ttf.addTtfBook
import net.bible.sharedcore.platform.ANDBIBLE_VERSION_CODE
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toOkioPath
import org.crosswire.jsword.book.Books
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * #3970: the add-on modules the app makes itself from an imported `.ttf` or prompt `.csv` carry an Android
 * `AndBibleMinimumVersion` (892), and the platform's add-on filter drops them unless `AppEnvironment.versionNumber`
 * is on that scale. iOS reported CFBundleVersion (1), so an imported font was registered in JSword but never reached
 * the font chooser. iOS now reports [ANDBIBLE_VERSION_CODE]; this checks the whole path with the real filter,
 * the real synthetic modules and the real scanner.
 */
class AddonVersionGateTest {
    private lateinit var dir: Path
    private val initials = listOf("TTF_GateFont", "Prompts_GatePack")

    @BeforeTest fun setUp() {
        dir = Files.createTempDirectory("addon-gate").toOkioPath()
        val ttf = dir / "ttf"
        val prompts = dir / "prompts"
        FileSystem.SYSTEM.createDirectories(ttf)
        FileSystem.SYSTEM.createDirectories(prompts)
        FileSystem.SYSTEM.write(ttf / "GateFont.ttf") { writeUtf8("not really a font") }
        FileSystem.SYSTEM.write(prompts / "GatePack.csv") { writeUtf8("title,prompt\n") }
        addTtfBook(ttf / "GateFont.ttf")
        addCsvPromptBook(prompts / "GatePack.csv")
    }

    @AfterTest fun tearDown() {
        initials.forEach { id -> Books.installed().getBook(id)?.let { Books.installed().removeBook(it) } }
        FileSystem.SYSTEM.deleteRecursively(dir)
    }

    private fun scanner(versionNumber: Long) =
        SharedAddonScanner({ Books.installed().getBooks(AndBibleAddonFilter { versionNumber }) })

    @Test fun theBuildVersionCodeAdmitsTheAppsOwnFontAndPromptModules() {
        val scanner = scanner(ANDBIBLE_VERSION_CODE)
        assertEquals(listOf("GateFont"), scanner.fonts().filter { it.module == "TTF_GateFont" }.map { it.name })
        assertTrue(scanner.promptPacks().any { it.moduleName == "Prompts_GatePack" })
    }

    @Test fun aBundleBuildNumberHidesThem() {
        // The pre-fix iOS value: CFBundleVersion = CURRENT_PROJECT_VERSION = 1.
        val scanner = scanner(1)
        assertTrue(scanner.fonts().none { it.module == "TTF_GateFont" })
        assertTrue(scanner.promptPacks().none { it.moduleName == "Prompts_GatePack" })
    }

    @Test fun theBuildVersionCodeIsOnTheAndroidScale() {
        // Background-image modules ask for the highest minimum the app writes itself.
        assertTrue(ANDBIBLE_VERSION_CODE >= 1112, "ANDBIBLE_VERSION_CODE=$ANDBIBLE_VERSION_CODE")
    }
}
