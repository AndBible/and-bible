import "libs/jquery.longpress"
import "jquery-nearest"

import {hideContent, updateLocation, scrollToVerse, setToolbarOffset, setupContent} from "./scroll";
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
import {sleep, whenReady} from "./utils";

window.addEventListener("DOMContentLoaded",  async (event) => {
    console.log("js-side load!", event.timeStamp, event);
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
    jsonscroll: updateLocation,
    scrollToVerse,
    whenReady,
    setupContent,
    hideContent
};
