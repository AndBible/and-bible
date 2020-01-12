import "libs/jquery.longpress"
import "jquery-nearest"

import {scrollToVerse, setToolbarOffset, setupContent} from "./scroll";
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

function initialize() {
    console.log("js-side load!", event.timeStamp, event);
    initializeListeners();
    initializeInfiniScroll();
    jsInterface.triggerJumpToOffset();
    // this will eventually call back setupContent
}

let initialized = false;

window.addEventListener("DOMContentLoaded",  async (event) => {
    if(!initialized) {
        initialize();
        initialized = true;
    }
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
    scrollToVerse,
    setupContent,
};
