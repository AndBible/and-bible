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

package net.bible.sharedui.reading.session

import kotlinx.serialization.serializer
import net.bible.android.database.IdType
import net.bible.android.database.WorkspaceEntities
import net.bible.android.database.json
import net.bible.sharedcore.bibleview.BibleViewEvents
import net.bible.sharedcore.bibleview.JsCall
import net.bible.sharedcore.bibleview.JsExpr

/**
 * Everything the `set_config` command reads, gathered by the platform (Android reads its
 * `CommonUtils.settings` and window state; iOS will read its own). [themeColors] is the already
 * rendered colour-roles JSON (or the literal `null`), produced by the platform's palette code.
 */
data class ConfigInputs(
    val displaySettings: WorkspaceEntities.TextDisplaySettings,
    val isActive: Boolean,
    val isBottomWindow: Boolean,
    val hasActiveIndicator: Boolean,
    val nightMode: Boolean,
    val showErrorBox: Boolean,
    val favouriteLabels: List<IdType>,
    val recentLabels: List<IdType>,
    val studyPadCursors: Map<IdType, Int>,
    val autoAssignLabels: List<IdType>,
    val hideCompareDocuments: Set<String>,
    val limitAmbiguousModalSize: Boolean,
    val windowDisplayId: String,
    val disableBibleModalButtons: Set<String>,
    val disableGenericModalButtons: Set<String>,
    val monochromeMode: Boolean,
    val einkMode: Boolean,
    val colorEinkMode: Boolean,
    val pureMonochromeMode: Boolean,
    val disableAnimations: Boolean,
    val fontSizeMultiplier: Float,
    val enabledExperimentalFeatures: List<String>,
    val themeColors: String,
    val disableClickToEdit: Boolean,
    val llmConfigured: Boolean,
    val notesContentType: String,
)

/** Builds the `set_config` JS call; pure, so equal inputs give the identical script. */
object ConfigCommand {
    fun build(initial: Boolean, inputs: ConfigInputs): JsCall = inputs.run {
        val favouriteLabelsJson = json.encodeToString(serializer(), favouriteLabels)
        val recentLabelsJson = json.encodeToString(serializer(), recentLabels)
        val studyPadCursorsJson = json.encodeToString(serializer(), studyPadCursors)
        val autoAssignLabelsJson = json.encodeToString(serializer(), autoAssignLabels)
        val hideCompareDocumentsJson = json.encodeToString(serializer(), hideCompareDocuments)
        val limitAmbiguousModalSizeJson = json.encodeToString(serializer(), limitAmbiguousModalSize)
        val disableBibleModalButtonsJson = json.encodeToString(serializer(), disableBibleModalButtons)
        val disableGenericModalButtonsJson = json.encodeToString(serializer(), disableGenericModalButtons)
        val enabledExperimentalFeaturesJson = json.encodeToString(serializer(), enabledExperimentalFeatures)
        BibleViewEvents.setConfig(JsExpr("""
                {
                    config: ${displaySettings.toJson()},
                    appSettings: {
                        activeWindow: $isActive,
                        isBottomWindow: $isBottomWindow,
                        hasActiveIndicator: $hasActiveIndicator,
                        nightMode: $nightMode,
                        errorBox: $showErrorBox,
                        favouriteLabels: $favouriteLabelsJson,
                        recentLabels: $recentLabelsJson,
                        studyPadCursors: $studyPadCursorsJson,
                        autoAssignLabels: $autoAssignLabelsJson,
                        hideCompareDocuments: $hideCompareDocumentsJson,
                        limitAmbiguousModalSize: $limitAmbiguousModalSizeJson,
                        windowId: '$windowDisplayId',
                        disableBibleModalButtons: $disableBibleModalButtonsJson, 
                        disableGenericModalButtons: $disableGenericModalButtonsJson, 
                        monochromeMode: $monochromeMode,
                        einkMode: $einkMode,
                        colorEinkMode: $colorEinkMode,
                        pureMonochromeMode: $pureMonochromeMode,
                        disableAnimations: $disableAnimations,
                        fontSizeMultiplier: $fontSizeMultiplier,
                        enabledExperimentalFeatures: $enabledExperimentalFeaturesJson,
                        themeColors: $themeColors,
                        disableClickToEdit:  $disableClickToEdit,
                        llmConfigured: $llmConfigured,
                        notesContentType: "$notesContentType",
                    },
                    initial: $initial,
                    }
                """))
    }
}
