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
package net.bible.sharedui.desktop.update

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopBetaBuildTest {
    private fun beta(kind: String?, properties: Map<String, String> = emptyMap(), env: Map<String, String> = emptyMap()) =
        DesktopChannel.isBetaBuild(
            properties = { key -> if (key == "andbible.releaseKind") kind else properties[key] },
            environment = { env[it] },
        )

    @Test fun betaAndTestPackagesAreBeta() {
        assertTrue(beta("beta"))
        assertTrue(beta("test"))
    }

    @Test fun productionPackagesAndDevRunsAreNot() {
        assertFalse(beta("production"))
        assertFalse(beta("dev"))
        assertFalse(beta(null))
    }

    @Test fun devRunsCanBeForcedToBeta() {
        assertTrue(beta("dev", properties = mapOf(DesktopChannel.FORCE_BETA_PROPERTY to "true")))
        assertTrue(beta(null, env = mapOf(DesktopChannel.FORCE_BETA_ENV to "1")))
        assertFalse(beta("dev", properties = mapOf(DesktopChannel.FORCE_BETA_PROPERTY to "false")))
        assertFalse(beta("dev", env = mapOf(DesktopChannel.FORCE_BETA_ENV to "0")))
    }
}
