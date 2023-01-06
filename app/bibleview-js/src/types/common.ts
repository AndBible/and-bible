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

import Color from "color";
import {OrdinalRange} from "@/types/client-objects";
import {Ref} from "vue";
import {EventVerseInfo} from "@/utils";

export type ReloadAddonsParams = {
    readonly fontModuleNames: string[],
    readonly featureModuleNames: string[],
    readonly styleModuleNames: string[],
}

export type LogEntry = {
    msg: string,
    type: "ERROR" | "WARN"
    count: number
}

export type JSONString = string
export type AsyncFunc = (callId: number) => void
export type JournalEntryType = "bookmark" | "journal" | "none"

export type ColorInt = number
export type ColorString = string
export type ColorParam = Color | ColorString | ArrayLike<number> | ColorInt | { [key: string]: any };
export type BibleDocumentInfo = {
    bibleBookName: string,
    bookInitials: string,
    ordinalRange: OrdinalRange,
    originalOrdinalRange: OrdinalRange,
    v11n: string
}

export type VerseInfo = {
    ordinal: number
    osisID: string
    book: string
    chapter: number
    verse: number
    v11n?: string
    showStack: Ref<boolean>[]
}

export type FootNoteCount = {
    getFootNoteCount: () => number
}

export type AreYouSureButton = {
  title: string
  class: "warning"
  result: any
}

export type SelectionInfo = EventVerseInfo & {
    startOrdinal: number
    endOrdinal: number
}

export type Nullable<T> = T | null;
export type Optional<T> = Nullable<T> | undefined;