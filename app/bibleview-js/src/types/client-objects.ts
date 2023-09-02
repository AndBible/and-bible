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
import {isGenericBookmark} from "@/composables/bookmarks";

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
export type OrdinalAndOffsetRange = {
    ordinalRange: OrdinalRange,
    offsetRange: OffsetRange
}
export type OrdinalOffset = [start: number, end: Nullable<number>]
export type CombinedRange = [start: OrdinalOffset, end: OrdinalOffset]

export type BaseBookmarkToLabel = {
    readonly type: "BibleBookmarkToLabel" | "GenericBookmarkToLabel"
    readonly bookmarkId: IdType
    readonly labelId: IdType
    orderNumber: number
    readonly indentLevel: number
    readonly expandContent: boolean
}

export type BibleBookmarkToLabel = BaseBookmarkToLabel & {
    readonly type: "BibleBookmarkToLabel"
}

export type GenericBookmarkToLabel = BaseBookmarkToLabel & {
    readonly type: "GenericBookmarkToLabel"
}

export type BaseBookmark = {
    readonly id: IdType
    readonly type: "bookmark" | "generic-bookmark"
    readonly hashCode: number
    readonly ordinalRange: OrdinalRange
    readonly offsetRange: Nullable<OffsetRange>
    readonly labels: IdType[]
    readonly bookInitials: string
    readonly bookName: string
    readonly bookAbbreviation: string
    readonly createdAt: number
    readonly text: string
    readonly fullText: string
    readonly bookmarkToLabels: BaseBookmarkToLabel[]
    readonly primaryLabelId: IdType
    lastUpdatedOn: number
    notes: Nullable<string>
    hasNote: boolean
    wholeVerse: boolean
}

export type BibleBookmark = BaseBookmark & {
    readonly type: "bookmark"
    readonly osisRef: string
    readonly originalOrdinalRange: OrdinalRange
    readonly verseRange: string
    readonly verseRangeOnlyNumber: string
    readonly verseRangeAbbreviated: string
    readonly v11n: string
    readonly osisFragment: OsisFragment | null
    readonly bookmarkToLabels: BibleBookmarkToLabel[]
}

export type GenericBookmark = BaseBookmark & {
    readonly type: "generic-bookmark"
    readonly key: string
    readonly keyName: string
    readonly bookmarkToLabels: GenericBookmarkToLabel[]
    readonly highlightedText: string
}

export type StudyPadTextItem = {
    readonly id: IdType
    readonly hashCode: number
    readonly type: "journal"
    readonly labelId: IdType
    text: string
    orderNumber: number
    indentLevel: number
    new?: boolean
}

export type BaseStudyPadBookmarkItem = BaseBookmark & {
    orderNumber: number
    indentLevel: number
    expandContent: boolean
    bookmarkToLabel: BaseBookmarkToLabel
}


export type StudyPadBibleBookmarkItem = BaseStudyPadBookmarkItem & BibleBookmark & {
    bookmarkToLabel: BibleBookmarkToLabel
}

export type StudyPadGenericBookmarkItem = BaseStudyPadBookmarkItem & GenericBookmark & {
    bookmarkToLabel: GenericBookmarkToLabel
}

export type StudyPadItem = BaseStudyPadBookmarkItem | StudyPadTextItem

export type BookmarkStyle = Readonly<{
    color: number
    isSpeak: boolean
    underline: boolean
    underlineWholeVerse: boolean
    markerStyle: boolean
    markerStyleWholeVerse: boolean
    hideStyle: boolean
    hideStyleWholeVerse: boolean
}>

export type Label = Readonly<{
    id: IdType
    name: string
    style: BookmarkStyle
    isRealLabel: boolean
}>

export type LabelAndStyle = Label & BookmarkStyle

export type BookmarkOrdinalKey = string

export function getBookmarkOrdinalKey(b: BaseBookmark, ordinal: number): BookmarkOrdinalKey {
    if(isGenericBookmark(b)) {
        return `${b.key}-${ordinal}`
    } else {
        return `BIBLE-${ordinal}`
    }
}