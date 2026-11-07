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
package net.bible.sharedcore.platform

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IosBetaBuildTest {
    private val testFlight = IosBetaBuild.TESTFLIGHT_RECEIPT

    @Test
    fun testFlightReleaseInstallIsBeta() {
        assertTrue(IosBetaBuild.isBeta(testFlight, isSimulator = false, isDebugBinary = false, arguments = emptyList()))
    }

    @Test
    fun appStoreInstallIsNotBeta() {
        assertFalse(IosBetaBuild.isBeta("receipt", isSimulator = false, isDebugBinary = false, arguments = emptyList()))
        assertFalse(IosBetaBuild.isBeta(null, isSimulator = false, isDebugBinary = false, arguments = emptyList()))
    }

    @Test
    fun simulatorAndDebugRunsAreNotBetaEvenWithTheSandboxReceiptName() {
        assertFalse(IosBetaBuild.isBeta(testFlight, isSimulator = true, isDebugBinary = true, arguments = emptyList()))
        assertFalse(IosBetaBuild.isBeta(testFlight, isSimulator = false, isDebugBinary = true, arguments = emptyList()))
        assertFalse(IosBetaBuild.isBeta(testFlight, isSimulator = true, isDebugBinary = false, arguments = emptyList()))
    }

    @Test
    fun launchArgumentForcesBetaOnSimulatorAndDebugRuns() {
        val args = listOf("-resetData", IosBetaBuild.FORCE_BETA_ARG)
        assertTrue(IosBetaBuild.isBeta(null, isSimulator = true, isDebugBinary = true, arguments = args))
        assertTrue(IosBetaBuild.isBeta(null, isSimulator = false, isDebugBinary = true, arguments = args))
    }

    @Test
    fun launchArgumentIsIgnoredByReleaseDeviceBuilds() {
        val args = listOf(IosBetaBuild.FORCE_BETA_ARG)
        assertFalse(IosBetaBuild.isBeta("receipt", isSimulator = false, isDebugBinary = false, arguments = args))
    }
}
