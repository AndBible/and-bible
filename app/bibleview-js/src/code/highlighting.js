/*
 * Copyright (c) 2020 Martin Denham, Tuomas Airaksinen and the And Bible contributors.
 *
 * This file is part of And Bible (http://github.com/AndBible/and-bible).
 *
 * And Bible is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software Foundation,
 * either version 3 of the License, or (at your option) any later version.
 *
 * And Bible is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with And Bible.
 * If not, see http://www.gnu.org/licenses/.
 */

import {doScrolling} from "./scroll";
import $ from "jquery"
import "@/libs/jquery.nearest"
import "@/libs/jquery.longpress"

/**
 * Monitor verse selection via long press
 */
export function enableVerseLongTouchSelectionMode() {
    console.log("Enabling verse long touch selection mode");
    // Enable special selection for Bibles
    $(document).longpress( tapholdHandler );
}

// Let's monkeypatch this so that jquery.nearest does not crash
window.document.getClientRects = () => [];

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
    console.log("Enabling verse touch selection");
    // Enable special selection for Bibles
    $(document).bind("touchstart", touchHandler );
}

export function disableVerseTouchSelection() {
    console.log("Disabling verse touch selection");
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
export function highlightVerse(chapterVerse, start, offset) {
    const $verseSpan = $('#' + escapeSelector(chapterVerse));
    if(start && $verseSpan[0].offsetTop < window.pageYOffset + offset) {
        doScrolling($verseSpan[0].offsetTop - offset, 250);
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
