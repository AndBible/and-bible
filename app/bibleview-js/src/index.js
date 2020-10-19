import "libs/jquery.longpress"
import "jquery-nearest"

import {scrollToVerse, setDisplaySettings, setToolbarOffset, setupContent} from "./scroll";
import {initializeInfiniScroll, insertThisTextAtEnd, insertThisTextAtTop} from "./infinite-scroll";
import {
    registerVersePositions,
    initializeListeners,
    setEditMode
} from "./bibleview";
import {
    clearVerseHighlight, disableVerseTouchSelection,
    enableVerseLongTouchSelectionMode,
    enableVerseTouchSelection,
    highlightVerse,
    unhighlightVerse
} from "./highlighting";

function initialize(settings) {
    initializeListeners();
    initializeInfiniScroll();
    setupContent(settings)
}

const origConsole = window.console;

// Override normal console, so that argument values also propagate to Android logcat
const myConsole = {
    _msg(s, args) {
        return `${s} ${args}`
    },
    log(s, ...args) {
        origConsole.log(this._msg(s, args))
    },
    error(s, ...args) {
        origConsole.error(this._msg(s, args))
    },
    warn(s, ...args) {
        origConsole.warn(this._msg(s, args))
    }
}

window.console = myConsole;

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
    setDisplaySettings,
    initialize,

    setEditMode
};
