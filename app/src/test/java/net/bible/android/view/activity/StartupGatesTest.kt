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
package net.bible.android.view.activity

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * #4112 review: [StartupActivity]'s gates. The calculator (discrete flavour, `show_calculator`) comes before the
 * databases are opened, so the damaged-data question never shows before the PIN; the discrete crash check follows.
 */
class StartupGatesTest {
    private val calls = mutableListOf<String>()

    private fun gates(webView: Boolean = true, calculator: Boolean = true, databases: Boolean = true) = runBlocking {
        runStartupGates(
            webView = { calls += "webView"; webView },
            calculator = { calls += "calculator"; calculator },
            databases = { calls += "databases"; databases },
            discreteCrashCheck = { calls += "crashCheck" },
        )
    }

    @Test fun theCalculatorComesBeforeTheDatabases() {
        assertTrue(gates())
        assertEquals(listOf("webView", "calculator", "databases", "crashCheck"), calls)
    }

    @Test fun aCancelledCalculatorNeverOpensTheDatabases() {
        assertFalse(gates(calculator = false))
        assertEquals(listOf("webView", "calculator"), calls)
    }

    @Test fun closingOnDamagedDataEndsTheStart() {
        assertFalse(gates(databases = false))
        assertEquals(listOf("webView", "calculator", "databases"), calls)
    }

    @Test fun anOldWebViewClosedEndsBeforeEverything() {
        assertFalse(gates(webView = false))
        assertEquals(listOf("webView"), calls)
    }
}
