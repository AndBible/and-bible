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
type BcvObject = {
    osis: () => string
}

type BcvParserType = {
    include_apocrypha: (enabled: boolean) => void
    parse: (text: string) => BcvObject
    new(): BcvParserType
}

type BcvModule = {
    bcv_parser?: BcvParserType
}

declare module '*.scss?inline' {
    const content: string;
    export default content;
}

declare module 'bible-passage-reference-parser/js/en_bcv_parser.min' {
    export const bcv_parser: BcvParserType;
}

type UntranslatedStrings = {
    chapterNum: string
    verseNum: string,
    multiDocumentLink: string
}

type TranslatedStrings = {
    openMyNotes: string
    openStudyPad: string
    noteText: string
    noteTextWithoutType: string
    crossReferenceText: string
    findAllOccurrences: string
    reportError: string
    errorTitle: string
    warningTitle: string
    normalTitle: string
    footnoteTypeStudy: string
    footnoteTypeExplanation: string
    footnoteTypeVariant: string
    footnoteTypeAlternative: string
    footnoteTypeTranslation: string
    clearLog: string
    editTextPlaceholder: string
    inputPlaceholder: string
    inputReference: string
    invalidReference: string
    bookmarkAccurate: string
    bookmarkInaccurate: string
    defaultBook: string
    ok: string
    yes: string
    ambiguousSelection: string
    cancel: string
    removeBookmarkConfirmationTitle: string
    removeBookmarkConfirmation: string
    createdAt: string
    lastUpdatedOn: string
    strongsAndMorph: string
    externalLink: string
    referenceLink: string
    openFootnote: string
    noMyNotesTitle: string
    noMyNotesDescription: string
    emptyStudyPad: string
    doYouWantToDeleteEntry: string
    removeStudyPadConfirmationTitle: string
    dragHelp: string
    openAll: string
    editBookmarkPlaceholder: string
    onlyLabel: string
    wholeBookmark: string
    assignLabelsMenuEntry1: string
    favouriteLabels: string
    recentLabels: string
    bookmarkLabels: string
    refParserHelp: string
    openDownloads: string
    verseShare: string
    verseCompare: string
    verseNote: string
    verseMyNotes: string
    verseShareLong: string
    verseCompareLong: string
    verseNoteLong: string
    addBookmark: string
}

type Strings = TranslatedStrings & UntranslatedStrings

declare module '@/lang/*.yaml' {
    const content: TranslatedStrings
    export default content;
}