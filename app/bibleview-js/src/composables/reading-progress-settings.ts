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

import {reactive} from "vue";
import {setupEventBusListener} from "@/eventbus";
import {ReadingProgressSettings} from "@/types/documents";

const defaultSettings: ReadingProgressSettings = {
    autoMarkMemorized: true,
    memorizeTypeFullWords: false,
    memorizeWordVisibility: 'light',
    memorizeErrorHeatmap: true,
    memorizeScrambleHideUsed: false,
    memorizeIncludeReference: true,
};

export function useReadingProgressSettings(
    initial: ReadingProgressSettings | undefined,
    android: { setReadingProgressSettings: (settings: ReadingProgressSettings) => void },
) {
    const settings = reactive<ReadingProgressSettings>({...defaultSettings, ...initial});

    setupEventBusListener("update_reading_progress_settings",
        (updated: ReadingProgressSettings) => {
            Object.assign(settings, updated);
        }
    );

    function updateSettings(partial: Partial<ReadingProgressSettings>) {
        Object.assign(settings, partial);
        android.setReadingProgressSettings({...settings});
    }

    return {settings, updateSettings};
}
