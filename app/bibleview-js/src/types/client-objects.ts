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

import {Nullable} from "@/types/common";

export type BookCategory = "BIBLE" | "COMMENTARY" | "GENERAL_BOOK"
export type V11N = string
export type Features = {
    readonly type?: Nullable<"hebrew-and-greek" | "hebrew" | "greek">,
    readonly keyName?: Nullable<string>
}

// ClientObjects.kt: OsisFragment
export type OsisFragment = {
    xml: string,
    originalXml?: string
    readonly key: string,
    readonly keyName: string,
    readonly v11n: V11N,
    readonly bookCategory: BookCategory,
    readonly bookInitials: string,
    readonly bookAbbreviation: string,
    readonly osisRef: string,
    readonly isNewTestament: boolean,
    readonly features: Features,
    readonly ordinalRange: number[],
    readonly language: string,
    readonly direction: "rtl" | "ltr",
}

export type NumberRange = [start: number, end: number]
export type OrdinalRange = NumberRange
export type OffsetRange = [start: number, end: Nullable<number>]
export type OrdinalOffset = [start: number, end: Nullable<number>]
export type CombinedRange = [start: OrdinalOffset, end: OrdinalOffset]

export type BookmarkToLabel = {
    readonly bookmarkId: IdType
    readonly labelId: IdType
    readonly orderNumber: number
    readonly indentLevel: number
    readonly expandContent: boolean
}

export type Bookmark = {
    readonly id: IdType
    readonly type: "bookmark"
    readonly ordinalRange: OrdinalRange
    readonly originalOrdinalRange: OrdinalRange
    readonly offsetRange: OffsetRange
    readonly labels: IdType[]
    readonly bookInitials: string
    readonly bookName: string
    readonly bookAbbreviation: string
    readonly createdAt: number
    readonly verseRange: string
    readonly verseRangeOnlyNumber: string
    readonly verseRangeAbbreviated: string
    readonly text: string
    readonly osisRef: string
    readonly v11n: string
    readonly fullText: string
    readonly bookmarkToLabels: BookmarkToLabel[]
    readonly osisFragment: OsisFragment | null
    readonly primaryLabelId: IdType
    lastUpdatedOn: number
    notes: Nullable<string>
    hasNote: boolean
    wholeVerse: boolean
}

export type StudyPadTextItem = {
    readonly id: IdType
    readonly type: "journal"
    readonly labelId: IdType
    text: string
    orderNumber: number
    indentLevel: number
    new?: boolean
}

export type StudyPadBookmarkItem = Bookmark & {
    orderNumber: number
    indentLevel: number
    expandContent: boolean
    bookmarkToLabel: BookmarkToLabel
}

export type StudyPadItem = StudyPadBookmarkItem | StudyPadTextItem

export type StudyPadItemOf<T> =
    T extends "journal" ? StudyPadTextItem : StudyPadBookmarkItem

export type BookmarkStyle = Readonly<{
    color: number
    isSpeak: boolean
    underline: boolean
    underlineWholeVerse: boolean
    markerStyle: boolean
    markerStyleWholeVerse: boolean
}>

export type Label = Readonly<{
    id: IdType
    name: string
    style: BookmarkStyle
    isRealLabel: boolean
}>

export type LabelAndStyle = Label & BookmarkStyle
