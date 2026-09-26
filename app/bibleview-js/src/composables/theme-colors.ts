/*
 * Copyright (c) 2026 Martin Denham, Tuomas Airaksinen and the AndBible contributors.
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

import Color from "color";

interface ThemeColorFields {
    dayLinkColor?: number | null;
    nightLinkColor?: number | null;
    dayVerseNumberColor?: number | null;
    nightVerseNumberColor?: number | null;
    dayHeadingColor?: number | null;
    nightHeadingColor?: number | null;
}

interface ModeFlags { nightMode: boolean; monochromeMode: boolean; }

export interface ResolvedAccentColors {
    linkColor: string | null;
    verseNumberColor: string | null;
    headingColor: string | null;
}

function css(value: number | null | undefined): string | null {
    return (value === null || value === undefined) ? null : Color(value).hsl().string();
}

/**
 * Resolves theme accent colors for the active day/night mode. Returns null for any
 * slot that is unset or when monochrome (e-ink) mode is active, so the caller can
 * omit the CSS variable and let existing defaults/derivations apply.
 */
export function resolveThemeAccentColors(
    colors: ThemeColorFields,
    {nightMode, monochromeMode}: ModeFlags,
): ResolvedAccentColors {
    if (monochromeMode) return {linkColor: null, verseNumberColor: null, headingColor: null};
    return {
        linkColor: css(nightMode ? colors.nightLinkColor : colors.dayLinkColor),
        verseNumberColor: css(nightMode ? colors.nightVerseNumberColor : colors.dayVerseNumberColor),
        headingColor: css(nightMode ? colors.nightHeadingColor : colors.dayHeadingColor),
    };
}
