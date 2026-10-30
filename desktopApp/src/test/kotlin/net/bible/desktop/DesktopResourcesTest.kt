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
package net.bible.desktop

import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** The raw resources the real app reads from the classpath are packaged with it (copied from app/src/main/res/raw). */
class DesktopResourcesTest {
    private fun text(name: String): String {
        val stream = assertNotNull(javaClass.classLoader.getResourceAsStream(name), "$name is not on the app classpath")
        return stream.use { it.readBytes().decodeToString() }
    }

    @Test fun repositoriesSiteMapIsPackaged() {
        assertTrue(text("repositories.txt").contains("CrossWire"), "repositories.txt does not list CrossWire")
    }

    @Test fun bundledReadingPlansArePackaged() {
        val plans = net.bible.sharedcore.platform.desktop.misc.DesktopReadingPlanSource(javaClass.classLoader)
        val codes = plans.builtInPlanCodes()
        assertTrue(codes.size == 7, "Expected seven bundled reading plans: $codes")
        codes.forEach { assertTrue(!plans.openBuiltInPlan(it).isNullOrBlank(), it) }
    }

    @Test fun licenseAndPromptsArePackaged() {
        assertTrue(text("license.html").isNotBlank())
        assertTrue(text("llm_agent_system_prompt.md").isNotBlank())
        assertTrue(text("llm_text_transformation_system_prompt.md").isNotBlank())
    }
}
