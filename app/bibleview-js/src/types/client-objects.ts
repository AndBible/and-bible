/*
 * Copyright (c) 2022 Martin Denham, Tuomas Airaksinen and the AndBible contributors.
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

export type BookCategory = "BIBLE"|"COMMENTARY"|"GENERAL_BOOK"
export type V11N = string
export type Features = {
    type?: "hebrew-and-greek" | "hebrew" | "greek" | null,
    keyName?: string | null
}

// ClientObjects.kt: OsisFragment
export type OsisFragment = {
    xml: string,
    key: string,
    keyName: string,
    v11n: V11N,
    bookCategory: BookCategory,
    bookInitials: string,
    bookAbbreviation: string,
    osisRef: string,
    isNewTestament: boolean,
    features: Features,
    ordinalRange: number[],
    language: string,
    direction: "rtl" | "ltr",
}

export type NumberRange = [number, number]
export type OrdinalRange = NumberRange
export type OffsetRange = [number, number|null]
export type OrdinalOffset = [number, number | null]
export type CombinedRange = [OrdinalOffset, OrdinalOffset]
export type BookmarkToLabel = any

export type Bookmark = {
    id: number
    ordinalRange: OrdinalRange
    originalOrdinalRange: OrdinalRange
    offsetRange: OffsetRange
    labels: number[]
    bookInitials: string
    bookName: string
    bookAbbreviation: string
    createdAt: number
    lastUpdatedOn: number
    notes: string | null
    hasNote: boolean
    verseRange: string
    verseRangeOnlyNumber: string
    verseRangeAbbreviated: string
    text: string
    osisRef: string
    v11n: string
    fullText: string
    bookmarkToLabels: BookmarkToLabel[]
    osisFragment: OsisFragment
    type: "bookmark"
    primaryLabelId: number
    wholeVerse: boolean
}

export type JournalEntry = any

export type BookmarkStyle = {
    color: number
    isSpeak: boolean
    underline: boolean
    underlineWholeVerse: boolean
    markerStyle: boolean
    markerStyleWholeVerse: boolean
}

export type Label = {
    id: number
    name: string
    style: BookmarkStyle
    isRealLabel: boolean
}

export type LabelAndStyle = Label & BookmarkStyle
