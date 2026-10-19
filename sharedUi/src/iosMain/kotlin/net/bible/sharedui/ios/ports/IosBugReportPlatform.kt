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

import net.bible.service.llm.agent.RawLlmLog
import net.bible.sharedui.app.ai.BugReportPlatform
import net.bible.sharedui.ios.IosPending

/** Pending (stream F). */
@kotlin.native.HiddenFromObjC
class IosBugReportPlatform : BugReportPlatform {
    override suspend fun reportRecord(recordId: String) {
        IosPending.log("BugReportPlatform.reportRecord")
    }

    override suspend fun reportRawLog(rawLog: RawLlmLog) {
        IosPending.log("BugReportPlatform.reportRawLog")
    }
}
