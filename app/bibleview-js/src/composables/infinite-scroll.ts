/*
 * Copyright (c) 2020-2026 Martin Denham, Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
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
import {filterNotNull, setupWindowEventListener, waitNextAnimationFrame} from "@/utils";
import {UseAndroid} from "@/composables/android";
import {AnyDocument, isOsisDocument} from "@/types/documents";
import {Nullable} from "@/types/common";
import {BookCategory} from "@/types/client-objects";
import {Config} from "@/composables/config";

const maxConsecutiveEmptyLoads = 3; // Safety limit

const enabledCategories: Set<BookCategory> = new Set(["BIBLE", "GENERAL_BOOK", "COMMENTARY"]);

/**
 * Whether the first document supports adjacent-chapter/block navigation (Bible, commentary, or
 * general book). AI documents are single-page generated content and are excluded. Both the manual
 * chapter controls and infinite scroll derive from this same contract.
 */
export function supportsChapterNavigation(documents: AnyDocument[]): boolean {
    if (documents.length === 0) return false;
    const doc = documents[0];
    if (isOsisDocument(doc)) {
        if (doc.isAiDocument) return false;
        return enabledCategories.has(doc.bookCategory);
    }
    return doc.type === "bible";
}

export function useInfiniteScroll(
    {requestPreviousChapter, requestNextChapter}: UseAndroid,
    bibleViewDocuments: AnyDocument[],
    config: Config,
) {
    let currentPos: number;
    let addMoreAtTopOnTouchUp = false;
    let bottomElem: HTMLElement;
    let touchDown = false;
    let textToBeInsertedAtTop: Nullable<AnyDocument[]> = null;
    let isProcessing = false;
    let reachedStart = false;
    const addChaptersToTop: Promise<Nullable<AnyDocument>>[] = [];
    const addChaptersToEnd: Promise<Nullable<AnyDocument>>[] = [];
    const reachedEnd = ref(false);
    let consecutiveEmptyLoads = 0;

    console.log("inf: Queues", {addChaptersToTop, addChaptersToEnd});

    let clearDocumentCount = 0;

    function documentsCleared() {
        addChaptersToTop.splice(0);
        addChaptersToEnd.splice(0);
        clearDocumentCount++;
        reachedEnd.value = false;
        consecutiveEmptyLoads = 0;
        reachedStart = false;
    }

    function needsMoreContent(): boolean {
        const viewportHeight = window.innerHeight;
        const currentScrollY = scrollPosition();
        
        // Calculate actual content height excluding the bottom padding
        const actualContentHeight = bottomElem ? bottomElem.offsetTop : bodyHeight();
        
        // Calculate remaining scrollable height from current position
        const remainingScrollableHeight = actualContentHeight - currentScrollY - viewportHeight;
        const minimumHeight = viewportHeight * 0.1; // 10% of viewport height as buffer
        return remainingScrollableHeight < minimumHeight;
    }

    async function processQueues() {
        if(isProcessing) return;
        console.log("inf: processQueues")
        isProcessing = true;
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
                if(clearCountStart !== clearDocumentCount) {
                    console.log("inf: Document cleared in between, stopping")
                    return;
                }
                
                let contentAdded = false;
                if(endChaps.length > 0) {
                    const validEndChaps = filterNotNull(endChaps);
                    if(validEndChaps.length > 0) {
                        console.log("inf: Displaying received chapters at end")
                        insertThisTextAtEnd(...validEndChaps);
                        contentAdded = true;
                        await nextTick();
                    } else {
                        reachedEnd.value = true;
                        console.log("inf: Reached end of content")
                    }
                }
                if(topChaps.length > 0) {
                    const validTopChaps = filterNotNull(topChaps);
                    if(validTopChaps.length > 0) {
                        console.log("inf: Displaying received chapters at top")
                        await insertThisTextAtTop(validTopChaps);
                        contentAdded = true;
                        await nextTick();
                    } else {
                        reachedStart = true;
                        console.log("inf: Reached start of content")
                    }
                }
                
                // Track consecutive empty loads to prevent infinite loops
                if (!contentAdded) {
                    consecutiveEmptyLoads++;
                    // When infinite scroll is disabled (manual mode), set reachedEnd immediately
                    // When enabled (auto mode), use safety limit of 3 consecutive empty loads
                    const limit = config.infiniteScroll ? maxConsecutiveEmptyLoads : 1;
                    if (consecutiveEmptyLoads >= limit) {
                        console.log("inf: No more content available, stopping");
                        reachedEnd.value = true;
                        break;
                    }
                } else {
                    consecutiveEmptyLoads = 0;
                }
                
            } while ((addChaptersToEnd.length > 0 || addChaptersToTop.length > 0))
        } finally {
            isProcessing = false;
            console.log("inf: finally isProcessing = false")
        }
    }

    const loadingAtEnd = ref(false);
    const loadingAtTop = ref(false);

    function loadTextAtTop() {
        loadingAtTop.value = true;
        addChaptersToTop.push(requestPreviousChapter().finally(() => { loadingAtTop.value = false; }));
        processQueues();
    }

    async function loadTextAtEnd() {
        loadingAtEnd.value = true;
        addChaptersToEnd.push(requestNextChapter().finally(() => { loadingAtEnd.value = false; }));
        await processQueues();
        await waitNextAnimationFrame();

        if (isEnabled.value && needsMoreContent() && !isProcessing && !reachedEnd.value) {
            await loadTextAtEnd();
        }
    }

    const
        documentSupportsChapterNavigation = computed(() => supportsChapterNavigation(bibleViewDocuments)),
        // Whether infinite scroll is currently active (enabled in settings and supported by document)
        isEnabled = computed(() =>
            config.infiniteScroll && documentSupportsChapterNavigation.value
        ),
        UP_MARGIN = 2,
        DOWN_MARGIN = 200,
        bodyHeight = () => document.body.scrollHeight,
        scrollPosition = () => window.pageYOffset,
        setScrollPosition = (offset: number) => window.scrollTo(0, offset),
        addMoreAtEnd = () => {
            if (!isEnabled.value || isProcessing || reachedEnd.value) return;
            loadTextAtEnd();
        },
        addMoreAtTop = () => {
            if (!isEnabled.value || isProcessing || reachedStart) return;
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

    return {
        documentsCleared,
        loadingAtEnd,
        loadingAtTop,
        loadTextAtTop,
        loadTextAtEnd,
        documentSupportsChapterNavigation,
        infiniteScrollIsEnabled: isEnabled,
        reachedEnd,
    };
}
