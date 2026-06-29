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

import {DocumentReadingProgress} from "@/types/documents";

/** Percentage (0..100) of the way from unitStart to unitEnd, or null for a degenerate unit. */
export function computePercent(ordinal: number, unitStart: number, unitEnd: number): number | null {
    if (unitEnd <= unitStart) return null;
    const p = (ordinal - unitStart) / (unitEnd - unitStart);
    return Math.min(1, Math.max(0, p)) * 100;
}

/** Empirically-measured characters per screenful, or null if inputs are not measurable yet. */
export function estimateCharsPerPage(textLength: number, scrollHeight: number, pageHeight: number): number | null {
    if (textLength <= 0 || scrollHeight <= 0 || pageHeight <= 0) return null;
    return textLength * pageHeight / scrollHeight;
}

export function computeTotalPages(charCount: number, charsPerPage: number): number {
    return Math.max(1, Math.round(charCount / charsPerPage));
}

export function computeCurrentPage(percent: number, totalPages: number): number {
    return Math.min(totalPages, Math.max(1, Math.round((percent / 100) * totalPages)));
}

export function layoutSignature(parts: (number | string)[]): string {
    return parts.join("|");
}

export type ProgressDoc = {
    readingProgress?: DocumentReadingProgress | null;
    ordinalRange?: number[];
    osisRef?: string;
};

/**
 * Resolve the document the reader is currently in (the one we report progress for).
 * Matches by osisRef (currentKey) first — required for EPUB, whose anchor ordinals
 * restart per spine item so ordinal containment is ambiguous across loaded fragments.
 * Falls back to ordinal containment (correct for Bible, whose ordinals are absolute),
 * then to the first document that has progress metadata.
 */
export function resolveReadingProgress(
    documents: ProgressDoc[],
    currentVerse: number | null,
    currentKey: string,
): ProgressDoc | null {
    const withRp = documents.filter(d => !!d.readingProgress);
    if (withRp.length === 0) return null;
    if (currentKey) {
        const byKey = withRp.find(d => d.osisRef === currentKey);
        if (byKey) return byKey;
    }
    if (currentVerse !== null) {
        const byOrdinal = withRp.find(d =>
            !!d.ordinalRange && currentVerse >= d.ordinalRange[0] && currentVerse <= d.ordinalRange[1]);
        if (byOrdinal) return byOrdinal;
    }
    return withRp[0];
}
