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
package net.bible.sharedui.assembly

import kotlin.test.Test
import kotlin.test.assertEquals

class AssemblyFailureClassifierTest {
    @Test
    fun aSyncTypeInTheCauseChainIsUnexpected() {
        // The sync bindings joined the iOS start (phase 2): an unbound sync port is a real assembly bug now.
        for (port in listOf("CloudAdapterFactory", "SyncSettingsPlatform", "SyncScheduler")) {
            val chain = "Could not create instance for X | No definition found for type 'net.bible.service.cloudsync.$port'"
            assertEquals(AssemblyFailureKind.UNEXPECTED, classifyAssemblyFailure(chain), port)
        }
        val starter = "No definition found for type 'net.bible.sharedcore.cloud.DocumentSyncStarter'"
        assertEquals(AssemblyFailureKind.UNEXPECTED, classifyAssemblyFailure(starter))
    }

    @Test
    fun anEmptyWorkspaceListIsAPhase2Dependent() {
        assertEquals(AssemblyFailureKind.PHASE_2_DEPENDENT, classifyAssemblyFailure("Could not create TextToSpeechServiceManager | index: 0, size: 0"))
    }

    @Test
    fun theEmptyListMessageUnderAnUnrelatedDefinitionIsUnexpected() {
        assertEquals(AssemblyFailureKind.UNEXPECTED, classifyAssemblyFailure("Could not create instance for FooService | index: 0, size: 0"))
        assertEquals(AssemblyFailureKind.UNEXPECTED, classifyAssemblyFailure("index: 0, size: 0"))
    }

    @Test
    fun anyOtherFailureIsUnexpected() {
        assertEquals(AssemblyFailureKind.UNEXPECTED, classifyAssemblyFailure("Could not create instance for Foo | NullPointerException: boom"))
        assertEquals(AssemblyFailureKind.UNEXPECTED, classifyAssemblyFailure(""))
        // A similar-looking port name (a different package) is not excused.
        assertEquals(AssemblyFailureKind.UNEXPECTED, classifyAssemblyFailure("No definition found for type 'other.CloudAdapterFactoryX'"))
    }
}
