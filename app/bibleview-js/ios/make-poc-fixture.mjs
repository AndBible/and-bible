// Builds the I0 PoC document (a BibleDocumentType, see src/types/documents.ts) from the OSIS test chapter.
// Run: node ios/make-poc-fixture.mjs   (from app/bibleview-js)
import {readFileSync, writeFileSync} from "node:fs";

const xml = readFileSync("src/__tests__/testdata/eph.2-kjva.xml", "utf8");
const ordinals = [...xml.matchAll(/verseOrdinal="(\d+)"/g)].map(m => Number(m[1]));
const ordinalRange = [Math.min(...ordinals), Math.max(...ordinals)];
const common = {
    key: "KJVA:Eph.2", v11n: "KJVA", bookCategory: "BIBLE", bookInitials: "KJVA",
    bookAbbreviation: "Eph", osisRef: "Eph.2", ordinalRange,
};
const doc = {
    id: "poc-eph2", type: "bible", ...common,
    bookName: "King James Version with Apocrypha", annotateRef: "", genericBookmarks: [],
    readingProgress: null, isNativeHtml: false, isMyDocument: false, isAiDocument: false,
    myDocumentPageId: null, sourcePromptId: null, sourcePromptName: null, sourceModelName: null,
    aiDocMarkers: [], bookmarks: [], bibleBookName: "Ephesians", addChapter: false, chapterNumber: 2,
    originalOrdinalRange: ordinalRange,
    osisFragment: {
        ...common, xml, keyName: "Ephesians 2", isNewTestament: true, features: {},
        language: "en", direction: "ltr", hasStrongs: true,
    },
};
writeFileSync("ios/poc-document.json", JSON.stringify(doc, null, 2) + "\n");
