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

export type BackgroundImageColors = {
    dayBackgroundImage: string | null,
    nightBackgroundImage: string | null,
    dayBackgroundImageOpacity: number,
    nightBackgroundImageOpacity: number,
}

export type BackgroundImageContext = {
    nightMode: boolean,
    monochromeMode: boolean,
    einkMode: boolean,
}

export type BackgroundImageLayerResult = { url: string, opacity: number }

/**
 * Compute the background-image layer for the current display mode, or null when no image
 * should be shown. Images are intentionally hidden in monochrome and e-ink modes.
 */
export function backgroundImageLayer(
    colors: BackgroundImageColors,
    ctx: BackgroundImageContext,
): BackgroundImageLayerResult | null {
    if (ctx.monochromeMode || ctx.einkMode) return null;
    const initials = ctx.nightMode ? colors.nightBackgroundImage : colors.dayBackgroundImage;
    if (!initials) return null;
    const opacityPercent = ctx.nightMode ? colors.nightBackgroundImageOpacity : colors.dayBackgroundImageOpacity;
    const opacity = (opacityPercent ?? 100) / 100;
    return {url: `/background/${encodeURIComponent(initials)}`, opacity};
}
