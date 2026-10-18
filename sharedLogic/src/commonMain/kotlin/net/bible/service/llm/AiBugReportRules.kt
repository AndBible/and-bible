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
package net.bible.service.llm

import net.bible.service.llm.agent.RawLlmLog

/**
 * The platform-free decisions behind AI bug reporting. The reporting host itself
 * ([net.bible.android.control.report.AiBugReport]) stays Android-only.
 */
object AiBugReportRules {
    /** Whether bug reporting is available for a given model (supported models only). */
    fun isReportAvailable(modelName: String): Boolean = LlmProvider.isModelSupported(modelName)

    /** Resolve model name from an in-memory RawLlmLog's iteration data. */
    fun resolveModelNameFromRawLog(rawLog: RawLlmLog): String =
        rawLog.usageByIteration.values.lastOrNull()?.model ?: ""
}
