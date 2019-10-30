import {jsonscroll, scrollToVerse, setToolbarOffset} from "./scroll";
import {insertThisTextAtEnd, insertThisTextAtTop} from "./infinite-scroll";
import {
    registerVersePositions,
} from "./bibleview";
import {
    clearVerseHighlight, disableVerseTouchSelection,
    enableVerseLongTouchSelectionMode,
    enableVerseTouchSelection,
    highlightVerse,
    unhighlightVerse
} from "./highlighting";

window.andbible = {
    //bibleview
    registerVersePositions,

    // infinit scroll
    insertThisTextAtEnd,
    insertThisTextAtTop,

    // highlighting
    highlightVerse,
    unhighlightVerse,
    clearVerseHighlight,
    enableVerseLongTouchSelectionMode,
    enableVerseTouchSelection,
    disableVerseTouchSelection,

    // scroll
    setToolbarOffset,
    jsonscroll,
    scrollToVerse,


};
