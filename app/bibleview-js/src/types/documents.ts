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
    Bookmark,
    BookmarkToLabel,
    Label,
    OrdinalRange,
    OsisFragment,
    StudyPadTextItem
} from "@/types/client-objects";

type DocumentType = "multi" | "osis" | "error" | "bible" | "notes" | "journal"

export type BaseDocument = {
    id: string
    type: DocumentType
}

export type MultiFragmentDocument = {
    id: string
    type: "multi"
    osisFragments: OsisFragment[]
    compare: boolean
}

type BaseOsisDocument = BaseDocument & {
    osisFragment: OsisFragment
    bookInitials: string
    bookCategory: BookCategory
    bookAbbreviation: string
    bookName: string
    key: string
    v11n: string
}

export type OsisDocument = BaseOsisDocument & {
    type: "osis"
}

export type ErrorDocument = BaseDocument & {
    type: "error"
    errorMessage: string
    severity: "NORMAL" | "WARNING" | "ERROR"
}

export type BibleDocumentType = BaseOsisDocument & {
    type: "bible"
    bookmarks: Bookmark[]
    bibleBookName: string
    ordinalRange: OrdinalRange
    addChapter: boolean
    chapterNumber: number
    originalOrdinalRange: OrdinalRange
}

export type MyNotesDocument = BaseDocument & {
    type: "notes"
    bookmarks: Bookmark[]
    verseRange: string
}

export type StudyPadDocument = BaseDocument & {
    type: "journal"
    bookmarks: Bookmark[]
    bookmarkToLabels: BookmarkToLabel[]
    journalTextEntries: StudyPadTextItem[]
    label: Label
}

export type AnyDocument = StudyPadDocument|MyNotesDocument|BibleDocumentType|ErrorDocument|OsisDocument|MultiFragmentDocument

export type DocumentOfType<T extends DocumentType> =
    T extends "journal" ? StudyPadDocument:
        T extends "notes" ? MyNotesDocument:
            T extends "bible"? BibleDocumentType:
                T extends "error"? ErrorDocument:
                    T extends "osis"? OsisDocument:
                        T extends "multi"? MultiFragmentDocument:
                           BaseDocument