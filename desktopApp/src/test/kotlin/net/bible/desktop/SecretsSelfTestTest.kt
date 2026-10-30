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

import net.bible.sharedcore.platform.desktop.secrets.KeyringBackend
import net.bible.sharedcore.platform.desktop.secrets.SessionMemoryBackend
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SecretsSelfTestTest {
    private val lines = mutableListOf<String>()
    private val mem = SessionMemoryBackend()
    private fun run(vararg a: String, enabled: Boolean = true, backend: KeyringBackend = mem) =
        SecretsSelfTest.run(a.toList(), enabled, { backend }) { lines += it }

    @Test fun saveThenReadGivesValue() {
        assertEquals(0, run("--selftest-secrets", "save", "hunter2"))
        assertEquals(0, run("--selftest-secrets", "read"))
        assertEquals(listOf("selftest-secrets save: ok", "selftest-secrets read: value=hunter2"), lines)
    }

    @Test fun deleteThenReadIsAbsent() {
        run("--selftest-secrets", "save", "x")
        assertEquals(0, run("--selftest-secrets", "delete"))
        assertEquals(0, run("--selftest-secrets", "read"))
        assertEquals("selftest-secrets read: absent", lines.last())
    }

    @Test fun unknownActionIsError() {
        assertEquals(1, run("--selftest-secrets", "frobnicate"))
        assertEquals(true, lines.single().startsWith("selftest-secrets frobnicate: error:"))
    }

    @Test fun backendFailureIsError() {
        val failing = object : KeyringBackend {
            override fun get(service: String, account: String): String? = error("boom")
            override fun set(service: String, account: String, value: String) = error("boom")
            override fun delete(service: String, account: String) = error("boom")
        }
        assertEquals(1, run("--selftest-secrets", "read", backend = failing))
        assertEquals("selftest-secrets read: error: boom", lines.single())
    }

    @Test fun notHandledWithoutPropertyOrFlag() {
        assertNull(run("--selftest-secrets", "read", enabled = false))
        assertNull(run("other", "read"))
        assertNull(SecretsSelfTest.run(emptyList(), true, { mem }) { lines += it })
        assertEquals(emptyList(), lines)
    }
}
