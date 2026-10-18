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

package net.bible.sharedcore.http

import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.UserAgent
import io.ktor.client.plugins.pluginOrNull
import net.bible.sharedcore.llm.http.LlmTimeouts
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class IosHttpClientsTest {
    private val http = IosHttpClients()

    @Test fun downloadClientRunsOnTheDarwinEngine() {
        assertTrue(http.downloadClient.engine::class.simpleName.orEmpty().contains("Darwin"))
    }

    @Test fun downloadClientHasTimeoutsButNoUserAgentOverride() {
        assertNotNull(http.downloadClient.pluginOrNull(HttpTimeout))
        assertNull(http.downloadClient.pluginOrNull(UserAgent), "C5: the CFNetwork default User-Agent must stay")
    }

    @Test fun llmClientIsCachedPerEqualTimeouts() {
        val a = http.clientFor(LlmTimeouts(1_000, 2_000, 3_000))
        assertSame(a, http.clientFor(LlmTimeouts(1_000, 2_000, 3_000)))
        assertNotSame(a, http.clientFor(LlmTimeouts(1_000, 2_000, 4_000)))
        assertNotSame(a, http.downloadClient)
    }

    @Test fun llmClientsUseDarwinWithTimeoutsAndNoUserAgent() {
        val c = http.clientFor(LlmTimeouts.CHAT)
        assertTrue(c.engine::class.simpleName.orEmpty().contains("Darwin"))
        assertNotNull(c.pluginOrNull(HttpTimeout))
        assertNull(c.pluginOrNull(UserAgent))
        assertEquals(c, http.clientFor(LlmTimeouts(120_000, 90_000, 120_000)))
    }
}
