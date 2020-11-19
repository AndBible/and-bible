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

/**
 * WebView js functions for continuous scrolling up and down between chapters
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */
//import {registerVersePositions} from "./bibleview";
import $ from "jquery"
import {onMounted, onUnmounted} from "@vue/runtime-core";
import {nextTick} from "@/code/utils";

export function useInfiniteScroll(config, android, osisFragments) {
    const UP_MARGIN = 2;
    const DOWN_MARGIN = 200;
    let currentPos = scrollPosition();

    let lastAddMoreTime = 0;
    let addMoreAtTopOnTouchUp = false;

    function scrollHandler() {
        const previousPos = currentPos;
        currentPos = scrollPosition();
        const scrollingUp = currentPos < previousPos;
        const scrollingDown = currentPos > previousPos;
        if (scrollingDown && currentPos >= ($('#bottom').offset().top - $(window).height()) - DOWN_MARGIN && Date.now() > lastAddMoreTime + 1000) {
            lastAddMoreTime = Date.now();
            addMoreAtEnd();
        } else if (scrollingUp && currentPos < UP_MARGIN && Date.now() > lastAddMoreTime + 1000) {
            lastAddMoreTime = Date.now();
            addMoreAtTop();
        }
        currentPos = scrollPosition();
    }

    onMounted(() => {
        window.addEventListener("scroll", scrollHandler)
        window.addEventListener('touchstart', touchstartListener, false);
        window.addEventListener('touchend', touchendListener, false);
        window.addEventListener("touchcancel", touchendListener, false);
    });

    onUnmounted(() => {
        window.removeEventListener("scroll", scrollHandler)
        window.removeEventListener('touchstart', touchstartListener, false);
        window.removeEventListener('touchend', touchendListener, false);
        window.removeEventListener("touchcancel", touchendListener, false);
    });

    function addMoreAtEnd() {
        loadTextAtEnd();
    }

    function addMoreAtTop() {
        if (touchDown) {
            // adding at top is tricky and if the user is stil holding there seems no way to set the scroll position after insert
            addMoreAtTopOnTouchUp = true;
        } else {
            loadTextAtTop();
        }
    }

    function touchstartListener() {
        touchDown = true;
    }

    function touchendListener() {
        touchDown = false;
        if (textToBeInsertedAtTop) {
            insertThisTextAtTop(textToBeInsertedAtTop);
        }
        if (addMoreAtTopOnTouchUp) {
            addMoreAtTopOnTouchUp = false;
            addMoreAtTop()
        }
    }

    let touchDown = false;
    let textToBeInsertedAtTop = null;

    function bodyHeight() {
        return document.body.scrollHeight;
    }

    function scrollPosition() {
        return window.pageYOffset;
    }

    function setScrollPosition(offset) {
        // Android 6 fails with window.scrollTop = offset but jquery works
        $(window).scrollTop(offset);
    }

    /**
     * Ask java to get more text to be loaded into page
     */
    function loadTextAtTop() {
        console.log("js:loadTextAtTop");
        android.requestMoreTextAtTop();
    }

    function loadTextAtEnd() {
        console.log("js:loadTextAtEnd");
        android.requestMoreTextAtEnd();
    }

    async function insertThisTextAtTop(osisFragment) {
        if (touchDown) {
            textToBeInsertedAtTop = osisFragment;
        } else {
            const priorHeight = bodyHeight();
            const origPosition = scrollPosition();

            osisFragments.unshift(osisFragment);
            await nextTick();

            // do no try to get scrollPosition here because it has not settled
            const adjustedTop = origPosition - priorHeight + bodyHeight();
            setScrollPosition(adjustedTop);
        }
    }

    function insertThisTextAtEnd(osisFragment) {
        osisFragments.push(osisFragment);
    }
    window.bibleView = {
        ...window.bibleView,
        insertThisTextAtTop,
        insertThisTextAtEnd
    }
}
