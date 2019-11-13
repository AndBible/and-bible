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

let loaded = false;
console.error("IMPORT LEVEL", performance.now());
window.addEventListener("DOMContentLoaded",  (event) => {
    console.log("js-side load!", event.timeStamp, event);
    if(!loaded) {
        loaded = true;
    } else {
        console.error("Already loaded??!");
        return;
    }
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
