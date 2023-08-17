/*
 * Copyright (c) 2020-2022 Martin Denham, Tuomas Airaksinen and the AndBible contributors.
 *
 * This file is part of AndBible: Bible Study (http://github.com/AndBible/and-bible).
 *
 * AndBible is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software Foundation,
 * either version 3 of the License, or (at your option) any later version.
 *
 * AndBible is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with AndBible.
 * If not, see http://www.gnu.org/licenses/.
 */

/**
 * WebView js functions for continuous scrolling up and down between chapters
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */

import {nextTick, onMounted} from "vue";
import {setupWindowEventListener} from "@/utils";
import {UseAndroid} from "@/composables/android";
import {AnyDocument, BibleViewDocumentType} from "@/types/documents";
import {Nullable} from "@/types/common";

export function useInfiniteScroll(
    {requestPreviousChapter, requestNextChapter}: UseAndroid,
    documents: AnyDocument[]
) {
    const enabledTypes: Set<BibleViewDocumentType> = new Set(["bible", "osis"]);
    let
        currentPos: number,
        lastAddMoreTime = 0,
        addMoreAtTopOnTouchUp = false,
        bottomElem: HTMLElement,
        touchDown = false,
        textToBeInsertedAtTop: Nullable<AnyDocument> = null;

    const
        UP_MARGIN = 2,
        DOWN_MARGIN = 200,
        bodyHeight = () => document.body.scrollHeight,
        scrollPosition = () => window.pageYOffset,
        setScrollPosition = (offset: number) => window.scrollTo(0, offset),
        loadTextAtTop = async () => insertThisTextAtTop(await requestPreviousChapter()),
        loadTextAtEnd = async () => insertThisTextAtEnd(await requestNextChapter()),
        addMoreAtEnd = () => {
            if (!enabledTypes.has(documents[0].type)) return;
            return loadTextAtEnd();
        },
        addMoreAtTop = () => {
            if (!enabledTypes.has(documents[0].type)) return;
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

    async function insertThisTextAtTop(document: AnyDocument) {
        if (touchDown) {
            textToBeInsertedAtTop = document;
        } else {
            const priorHeight = bodyHeight();
            const origPosition = scrollPosition();

            if (document) documents.unshift({...document});
            await nextTick();

            // do no try to get scrollPosition here because it has not settled
            const adjustedTop = origPosition - priorHeight + bodyHeight();
            setScrollPosition(adjustedTop);
        }
    }

    function insertThisTextAtEnd(document: AnyDocument) {
        if (document) documents.push({...document});
    }

    function scrollHandler() {
        const previousPos = currentPos;
        currentPos = scrollPosition();
        const scrollingUp = currentPos < previousPos;
        const scrollingDown = currentPos > previousPos;
        if (scrollingDown
            && currentPos >= (bottomElem.offsetTop - window.innerHeight) - DOWN_MARGIN
            && Date.now() > lastAddMoreTime + 1000) {
            lastAddMoreTime = Date.now();
            addMoreAtEnd();
        } else if (scrollingUp && currentPos < UP_MARGIN && Date.now() > lastAddMoreTime + 1000) {
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
        bottomElem = document.getElementById("bottom")!;
    });
}
