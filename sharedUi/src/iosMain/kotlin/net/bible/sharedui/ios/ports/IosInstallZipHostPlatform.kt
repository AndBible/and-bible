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
@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package net.bible.sharedui.ios.ports

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import net.bible.service.installzip.InstallJobState
import net.bible.service.installzip.JobId
import net.bible.sharedui.app.installzip.InstallZipHostPlatform
import net.bible.sharedui.app.ports.DocumentRef
import net.bible.sharedui.installzip.InstallUiState
import net.bible.sharedui.ios.IosPending

/**
 * Pending (stream F): no jobs, nothing to install. [mapPhase] answers an empty [InstallUiState.Error] because
 * [InstallUiState] has no neutral state; it is unreachable while [jobs] stays empty.
 */
@kotlin.native.HiddenFromObjC
class IosInstallZipHostPlatform : InstallZipHostPlatform {
    override val jobs: StateFlow<List<InstallJobState>> = MutableStateFlow<List<InstallJobState>>(emptyList()).asStateFlow()

    override suspend fun isStudyPadExport(uri: DocumentRef): Boolean {
        IosPending.log("InstallZipHostPlatform.isStudyPadExport")
        return false
    }

    override fun displayName(uri: DocumentRef): String? {
        IosPending.log("InstallZipHostPlatform.displayName")
        return null
    }

    override fun formatsText(): String {
        IosPending.log("InstallZipHostPlatform.formatsText")
        return ""
    }

    override fun resolveDecision(jobId: JobId, proceed: Boolean) {
        IosPending.log("InstallZipHostPlatform.resolveDecision")
    }

    override fun mapPhase(job: InstallJobState): InstallUiState {
        IosPending.log("InstallZipHostPlatform.mapPhase")
        return InstallUiState.Error("")
    }

    override fun requestNotificationPermission() {
        IosPending.log("InstallZipHostPlatform.requestNotificationPermission")
    }

    override fun noFileManager() {
        IosPending.log("InstallZipHostPlatform.noFileManager")
    }

    override fun initialiseIfStartedUninitialised() {
        IosPending.log("InstallZipHostPlatform.initialiseIfStartedUninitialised")
    }
}
