import {doScrolling, toolbarOffset} from "./scroll";


/**
 * Monitor verse selection via long press
 */
export function enableVerseLongTouchSelectionMode() {
    jsInterface.log("Enabling verse long touch selection mode");
    // Enable special selection for Bibles
    $(document).longpress( tapholdHandler );
}

/** Handle taphold to start verse selection */
function tapholdHandler(event) {
    const $target = $(event.target);
    if ($target.hasClass("verse")) {
        selected($target);
    } else {
        const point = {'x': event.pageX, 'y': event.pageY};
        const $elemSet = $('.verse');
        const $closestToPoint = $.nearest(point, $elemSet).filter(":first");

        selected($closestToPoint)
    }
}

/** Handle touch to extend verse selection */
function touchHandler(event) {
    let $target = $(event.target);
    if (!$target.hasClass("verse")) {
        const point = {'x': event.pageX, 'y': event.pageY};
        const $elemSet = $('.verse');
        const $closestToPoint = $.nearest(point, $elemSet).filter(":first");

        $target = $closestToPoint
    }

    const chapterVerse = $target.attr('id');
    jsInterface.verseTouch(chapterVerse);
}


export function enableVerseTouchSelection() {
    jsInterface.log("Enabling verse touch selection");
    // Enable special selection for Bibles
    $(document).bind("touchstart", touchHandler );
}

export function disableVerseTouchSelection() {
    jsInterface.log("Disabling verse touch selection");
    $(document).unbind("touchstart", touchHandler );
}


function selected($elem) {
    if ($elem.hasClass("verse")) {
        const chapterVerse = $elem.attr('id');
        jsInterface.verseLongPress(chapterVerse);
    }
}

function escapeSelector(selectr) {
    return (selectr+"").replace(".", "\\.")
}

/**
 * Called by VerseActionModelMediator to highlight a verse
 */
export function highlightVerse(chapterVerse, start) {
    const $verseSpan = $('#' + escapeSelector(chapterVerse));
    if(start && $verseSpan[0].offsetTop < window.pageYOffset + toolbarOffset) {
        doScrolling($verseSpan[0].offsetTop - toolbarOffset, 250);
    }
    $verseSpan.addClass("selected")
}

/**
 * Called by VerseActionModelMediator to unhighlight a verse
 */
export function unhighlightVerse(chapterVerse) {
    const $verseSpan = $('#' + escapeSelector(chapterVerse));
    $verseSpan.removeClass("selected")
}

/**
 * Called by VerseActionModelMediator to unhighlight a verse
 */
export function clearVerseHighlight() {
    const $verseSpan = $('.selected');
    $verseSpan.removeClass("selected")
}
