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

import {nextTick, onMounted} from "@vue/runtime-core";
import {setupWindowEventListener} from "@/utils";

export function useInfiniteScroll(config, android, osisFragments) {
    let
        currentPos,
        lastAddMoreTime = 0,
        addMoreAtTopOnTouchUp = false,
        bottomElem,
        touchDown = false,
        textToBeInsertedAtTop = null;

    const
        UP_MARGIN = 2,
        DOWN_MARGIN = 200,
        bodyHeight = () => document.body.scrollHeight,
        scrollPosition = () => window.pageYOffset,
        setScrollPosition = offset => window.scrollTo(0, offset),
        loadTextAtTop = async () => insertThisTextAtTop(await android.requestMoreTextAtTop()),
        loadTextAtEnd = async () => insertThisTextAtEnd(await android.requestMoreTextAtEnd()),
        addMoreAtEnd = () => loadTextAtEnd(),
        addMoreAtTop = () => {
            if (touchDown) {
                // adding at top is tricky and if the user is stil holding there seems no way to set the scroll position after insert
                addMoreAtTopOnTouchUp = true;
            } else {
                loadTextAtTop();
            }
        },

        touchstartListener = () => touchDown = true;

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

    async function insertThisTextAtTop(osisFragment) {
        if (touchDown) {
            textToBeInsertedAtTop = osisFragment;
        } else {
            const priorHeight = bodyHeight();
            const origPosition = scrollPosition();

            if(osisFragment) osisFragments.unshift({...osisFragment, showTransition: false});
            await nextTick();

            // do no try to get scrollPosition here because it has not settled
            const adjustedTop = origPosition - priorHeight + bodyHeight();
            setScrollPosition(adjustedTop);
        }
    }

    function insertThisTextAtEnd(osisFragment) {
        if(osisFragment) osisFragments.push({...osisFragment});
    }

    function scrollHandler() {
        const previousPos = currentPos;
        currentPos = scrollPosition();
        const scrollingUp = currentPos < previousPos;
        const scrollingDown = currentPos > previousPos;
        if (scrollingDown
            && currentPos >= (bottomElem.offsetTop - window.innerHeight) - DOWN_MARGIN
            && Date.now() > lastAddMoreTime + 1000)
        {
            lastAddMoreTime = Date.now();
            addMoreAtEnd();
        } else if (scrollingUp && currentPos < UP_MARGIN && Date.now() > lastAddMoreTime + 1000)
        {
            lastAddMoreTime = Date.now();
            addMoreAtTop();
        }
        currentPos = scrollPosition();
    }

    setupWindowEventListener('scroll', scrollHandler)
    setupWindowEventListener('touchstart', touchstartListener, false);
    setupWindowEventListener('touchend', touchendListener, false);
    setupWindowEventListener("touchcancel", touchendListener, false);

    onMounted(() => {
        currentPos = scrollPosition();
        bottomElem = document.getElementById("bottom");
    });
}
