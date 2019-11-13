import "libs/jquery.longpress"
import "jquery-nearest"

import {jsonscroll, scrollToVerse, setToolbarOffset} from "./scroll";
import {initializeInfiniScroll, insertThisTextAtEnd, insertThisTextAtTop} from "./infinite-scroll";
import {
    registerVersePositions,
    initialize
} from "./bibleview";
import {
    clearVerseHighlight, disableVerseTouchSelection,
    enableVerseLongTouchSelectionMode,
    enableVerseTouchSelection,
    highlightVerse,
    unhighlightVerse
} from "./highlighting";
import {addWaiter, Deferred, whenReady} from "./utils";

const isReady = new Deferred();
addWaiter(isReady);

$(document).ready( async () => {
    initialize();
    initializeInfiniScroll();
    setToolbarOffset(jsInterface.getToolbarOffset(), {doNotScroll: true});
    await scrollToVerse(jsInterface.getCurrentChapterVerseId(), true);
    isReady.resolve();
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
};
