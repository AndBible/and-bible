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

package net.bible.service.installzip

import android.net.Uri
import java.io.File

typealias JobId = String

/** Where an archive comes from, captured at enqueue with metadata read cheaply from the URI. */
data class InstallSource(
    val uri: Uri,
    val action: String?,       // Intent.ACTION_VIEW / ACTION_SEND / null (picker)
    val displayName: String?,
    val mimeType: String?,
)

enum class SqliteBookType { MYBIBLE, MYSWORD, ESWORD }

/** Result of inspecting the *local* (acquired) file; carries everything commit needs. */
sealed class InstallPlan {
    /** SWORD module zip. [existingFiles] non-empty ⇒ an Overwrite decision is required. */
    data class SwordZip(val existingFiles: List<String>, val totalEntries: Int) : InstallPlan()
    /** A zip whose content is actually an EPUB (has META-INF/container.xml). Routed through the
     *  same upgrade-confirmation gate as [Epub] -- see [needsUpgradeConfirm]. */
    data class EpubFromZip(val displayName: String, val needsUpgradeConfirm: Boolean) : InstallPlan()
    data class Epub(val displayName: String, val needsUpgradeConfirm: Boolean) : InstallPlan()
    data class Sqlite(val type: SqliteBookType, val displayName: String, val overwriteName: String?) : InstallPlan()
    data class Ttf(val displayName: String, val overwriteName: String?) : InstallPlan()
    data class Csv(val displayName: String, val overwriteName: String?) : InstallPlan()
    data class BackgroundImage(val fileName: String, val overwriteName: String?) : InstallPlan()
    data class StudyPad(val statsText: String, val unzipFolder: File) : InstallPlan()
    /** Not a recognizable module. [filename] used for the error message. */
    data class Invalid(val filename: String) : InstallPlan()
}

sealed class DecisionRequest {
    data class Overwrite(val files: List<String>) : DecisionRequest()
    data class StudyPadImport(val statsText: String) : DecisionRequest()
    object EpubUpgrade : DecisionRequest()
}

sealed class InstallPhase {
    object Queued : InstallPhase()
    data class Acquiring(val percent: Int) : InstallPhase()
    object Inspecting : InstallPhase()
    data class AwaitingDecision(val request: DecisionRequest) : InstallPhase()
    data class Committing(val percent: Int) : InstallPhase()
    object Done : InstallPhase()
    /** [messageKey] is an R.string id; [arg] optional format arg (e.g. filename). */
    data class Error(val messageKey: Int, val arg: String?) : InstallPhase()
    object Cancelled : InstallPhase()
}

data class InstallJobState(val jobId: JobId, val displayName: String?, val phase: InstallPhase)

sealed class InstallOutcome {
    object Ok : InstallOutcome()
    object Cancelled : InstallOutcome()
    object NothingEnqueued : InstallOutcome()
    data class Error(val messageKey: Int, val arg: String?) : InstallOutcome()
}
