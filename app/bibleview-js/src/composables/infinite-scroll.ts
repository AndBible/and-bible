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

import {computed, nextTick, onMounted, ref, watch} from "vue";
import {filterNotNull, setupWindowEventListener} from "@/utils";
import {UseAndroid} from "@/composables/android";
import {AnyDocument, isOsisDocument} from "@/types/documents";
import {Nullable} from "@/types/common";
import {BookCategory} from "@/types/client-objects";
import {UseScroll} from "@/composables/scroll";

export function useInfiniteScroll(
    {requestPreviousChapter, requestNextChapter}: UseAndroid,
    {scrollYAtStart}: UseScroll,
    bibleViewDocuments: AnyDocument[],
) {
    const enabledCategories: Set<BookCategory> = new Set(["BIBLE", "GENERAL_BOOK"]);
    let currentPos: number;
    let addMoreAtTopOnTouchUp = false;
    let bottomElem: HTMLElement;
    let touchDown = false;
    let textToBeInsertedAtTop: Nullable<AnyDocument[]> = null;
    let isProcessing = false;
    const addChaptersToTop: Promise<Nullable<AnyDocument>>[] = [];
    const addChaptersToEnd: Promise<Nullable<AnyDocument>>[] = [];

    console.log("inf: Queues", {addChaptersToTop, addChaptersToEnd});

    let clearDocumentCount = 0;

    function documentsCleared() {
        addChaptersToTop.splice(0);
        addChaptersToEnd.splice(0);
        clearDocumentCount ++;
    }

    async function processQueues() {
        if(isProcessing) return;
        console.log("inf: processQueues")
        isProcessing = true;
        // noinspection UnnecessaryLocalVariableJS
        const clearCountStart = clearDocumentCount;
        try {
            do {
                const endPromises =addChaptersToEnd.splice(0);
                const topPromises = addChaptersToTop.splice(0);
                console.log("inf: Waiting for chapters", {endPromises, topPromises});
                const [endChaps, topChaps] = await Promise.all([
                    Promise.all(endPromises),
                    Promise.all(topPromises)
                ]);
                console.log("inf: Received chapters")
                if(clearCountStart > clearDocumentCount) {
                    console.log("inf: Document cleared in between, stopping")
                    return;
                }
                if(endChaps.length > 0) {
                    console.log("inf: Displaying received chapters at end")
                    insertThisTextAtEnd(...filterNotNull(endChaps));
                    await nextTick();
                }
                if(topChaps.length > 0) {
                    console.log("inf: Displaying received chapters at top")
                    await insertThisTextAtTop(filterNotNull(topChaps));
                    await nextTick();
                }
            } while ((addChaptersToEnd.length > 0 || addChaptersToTop.length > 0))
        } finally {
            isProcessing = false;
            console.log("inf: finally isProcessing = false")
        }
    }

    function loadTextAtTop() {
        addChaptersToTop.push(requestPreviousChapter())
        processQueues();
    }

    function loadTextAtEnd() {
        addChaptersToEnd.push(requestNextChapter())
        processQueues();
    }

    const
        isEnabled = computed(() => {
           if(bibleViewDocuments.length === 0) return false;
           const doc = bibleViewDocuments[0];
           if(isOsisDocument(doc)) {
                return enabledCategories.has(doc.bookCategory)
           } else {
               return doc.type === "bible";
           }
        }),
        UP_MARGIN = 2,
        DOWN_MARGIN = 200,
        bodyHeight = () => document.body.scrollHeight,
        scrollPosition = () => window.pageYOffset,
        setScrollPosition = (offset: number) => window.scrollTo(0, offset),
        addMoreAtEnd = () => {
            if (!isEnabled.value || isProcessing) return;
            return loadTextAtEnd();
        },
        addMoreAtTop = () => {
            if (!isEnabled.value || isProcessing) return;
            if (touchDown) {
                // adding at top is tricky and if the user is still holding there seems no way to set the scroll position after insert
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
            textToBeInsertedAtTop = null;
        }
        if (addMoreAtTopOnTouchUp) {
            addMoreAtTopOnTouchUp = false;
            addMoreAtTop()
        }
    }

    async function insertThisTextAtTop(docs: AnyDocument[]) {
        if (touchDown) {
            textToBeInsertedAtTop = docs;
        } else {
            const priorHeight = bodyHeight();
            const origPosition = scrollPosition();

            if (docs) {
                docs.reverse();
                bibleViewDocuments.unshift(...docs);
            }
            await nextTick();

            // do no try to get scrollPosition here because it has not settled
            const adjustedTop = origPosition - priorHeight + bodyHeight();
            scrollYAtStart.value += adjustedTop;
            setScrollPosition(adjustedTop);
        }
    }

    function insertThisTextAtEnd(...docs: AnyDocument[]) {
        if (docs) bibleViewDocuments.push(...docs);
    }

    function scrollHandler() {
        const previousPos = currentPos;
        currentPos = scrollPosition();
        const scrollingUp = currentPos < previousPos;
        const scrollingDown = currentPos > previousPos;
        if (scrollingDown && currentPos >= (bottomElem.offsetTop - window.innerHeight) - DOWN_MARGIN) {
            addMoreAtEnd();
        } else if (scrollingUp && currentPos < UP_MARGIN) {
            addMoreAtTop();
        }
        currentPos = scrollPosition();
    }

    setupWindowEventListener('scroll', scrollHandler);
    watch(isEnabled, enabled => {
        if(enabled) scrollHandler();
    })
    setupWindowEventListener('touchstart', touchstartListener, false);
    setupWindowEventListener('touchend', touchendListener, false);
    setupWindowEventListener("touchcancel", touchendListener, false);

    onMounted(() => {
        currentPos = scrollPosition();
        bottomElem = document.getElementById("bottom")!;
    });

    return {documentsCleared};
}
