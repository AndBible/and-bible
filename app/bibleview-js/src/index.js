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

import {Deferred} from "./utils";
const isReady = new Deferred();

async function whenReady(fnc){
    await isReady.wait();
    fnc();
}

$(document).ready( () => {
        initialize();
        initializeInfiniScroll();
        isReady.resolve();
    }
);

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
    whenReady
};
