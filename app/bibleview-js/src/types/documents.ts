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

import {
    BookCategory,
    BibleBookmark,
    Label,
    OrdinalRange,
    OsisFragment,
    StudyPadTextItem,
    BaseBookmark,
    GenericBookmark,
    BibleBookmarkToLabel,
    GenericBookmarkToLabel
} from "@/types/client-objects";
import {Nullable} from "@/types/common";

export type BibleViewDocumentType = "multi" | "osis" | "error" | "bible" | "notes" | "journal" | "memorize"|"none"

export interface BaseDocument {
    id: string
    type: BibleViewDocumentType
}

export interface MultiFragmentDocument extends BaseDocument {
    type: "multi"
    osisFragments: OsisFragment[]
    compare: boolean
}


interface BaseOsisDocument extends BaseDocument {
    osisFragment: OsisFragment
    bookInitials: string
    bookCategory: BookCategory
    bookAbbreviation: string
    bookName: string
    key: string
    v11n: string
    osisRef: string
    annotateRef: string
    genericBookmarks: GenericBookmark[]
    ordinalRange: OrdinalRange
    isEpub: boolean
}

export interface OsisDocument extends BaseOsisDocument {
    type: "osis",
    highlightedOrdinalRange: Nullable<OrdinalRange>
}

export interface ErrorDocument extends BaseDocument {
    type: "error"
    errorMessage: string
    severity: "NORMAL" | "WARNING" | "ERROR"
}

export interface BibleDocumentType extends BaseOsisDocument {
    type: "bible"
    bookmarks: BibleBookmark[]
    bibleBookName: string
    addChapter: boolean
    chapterNumber: number
    originalOrdinalRange: OrdinalRange
}

export interface MyNotesDocument extends BaseDocument {
    type: "notes"
    bookmarks: BibleBookmark[]
    verseRange: string
    ordinalRange: OrdinalRange
}

export interface StudyPadDocument extends BaseDocument {
    type: "journal"
    bookmarks: BaseBookmark[]
    genericBookmarks: GenericBookmark[]
    bookmarkToLabels: BibleBookmarkToLabel[]
    genericBookmarkToLabels: GenericBookmarkToLabel[]
    journalTextEntries: StudyPadTextItem[]
    label: Label
}

export type AnyDocument =
    StudyPadDocument
    | MyNotesDocument
    | BibleDocumentType
    | ErrorDocument
    | OsisDocument
    | MultiFragmentDocument

export type DocumentOfType<T extends BibleViewDocumentType> =
    T extends "journal" ? StudyPadDocument :
        T extends "notes" ? MyNotesDocument :
            T extends "bible" ? BibleDocumentType :
                T extends "error" ? ErrorDocument :
                    T extends "osis" ? OsisDocument :
                        T extends "multi" ? MultiFragmentDocument :
                            T extends "memorize" ? MemorizeDocument :
                                BaseDocument


// types for MemorizeDocument
export type MemorizeTextItem = {
    key: string;
    text: string;
}

export enum MemorizeStateModeEnum {
    BLUR = 'blur',
    SCRAMBLE = 'scramble'
}

export type MemorizeStateMode = MemorizeStateModeEnum[keyof MemorizeStateModeEnum];
export type MemorizeModeConfig = any

export type MemorizeState = {
    mode: MemorizeStateMode
    modeConfig: MemorizeModeConfig
}

export type DocumentState = {
    memorize: MemorizeState
}

export interface MemorizeDocument extends BaseDocument{
    type: "memorize"
    title: string
    texts: MemorizeTextItem[]
    state?: DocumentState
}

export function isOsisDocument(t: AnyDocument): t is OsisDocument {
    return t.type === "osis";
}