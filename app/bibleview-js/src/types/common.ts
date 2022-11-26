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

export type ReloadAddonsParams = {
    fontModuleNames: string[],
    featureModuleNames: string[],
    styleModuleNames: string[],
}

export type LogEntry = {
    msg: string,
    type: "ERROR" | "WARN"
}

export type JSONString = string
export type AsyncFunc = (callId: number) => void
export type JournalEntryType = "bookmark" | "journal" | "none"
export type Bookmark = any
export type JournalEntry = any
export type Config = any
export type ABDocument = any
