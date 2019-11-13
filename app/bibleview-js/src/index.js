import "libs/jquery.longpress"
import "jquery-nearest"

import {jsonscroll, scrollToVerse, setToolbarOffset, setupContent} from "./scroll";
import {initializeInfiniScroll, insertThisTextAtEnd, insertThisTextAtTop} from "./infinite-scroll";
import {
    registerVersePositions,
    initializeListeners
} from "./bibleview";
import {
    clearVerseHighlight, disableVerseTouchSelection,
    enableVerseLongTouchSelectionMode,
    enableVerseTouchSelection,
    highlightVerse,
    unhighlightVerse
} from "./highlighting";
import {whenReady} from "./utils";

$(window).on( "load", async () => {
    console.log("js-side load!");
    registerVersePositions();
    initializeListeners();
    initializeInfiniScroll();
    jsInterface.triggerJumpToOffset();
    // this will eventually call back setupContent
});

window.andbible = {
    registerVersePositions,

    insertThisTextAtEnd,
    insertThisTextAtTop,

    highlightVerse,
    unhighlightVerse,
    clearVerseHighlight,
    enableVerseLongTouchSelectionMode,
    enableVerseTouchSelection,
    disableVerseTouchSelection,

    setToolbarOffset,
    jsonscroll,
    scrollToVerse,
    whenReady,
    setupContent
};
