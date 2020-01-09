/**
 * WebView js functions for continuous scrolling up and down between chapters
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */
import {registerVersePositions} from "./bibleview";
import {stopScrolling} from "./scroll";

function infiniScroll(fnLoadTextAtTop, fnLoadTextAtEnd, initialId, minId, maxId,
                      insertAfterAtTop, insertBeforeAtBottom) {
    // up is very hard when still scrolling so make the margin tiny to cause scroll to stop before pre-filling up
    const UP_MARGIN = 2;
    const DOWN_MARGIN = 200;
    let currentPos = scrollPosition();

    let topId = initialId;
    let endId = initialId;

    let lastAddMoreTime = 0;
    let addMoreAtTopOnTouchUp = false;

    const scrollHandler = function () {
        const previousPos = currentPos;
        currentPos = scrollPosition();
        const scrollingUp = currentPos < previousPos;
        const scrollingDown = currentPos > previousPos;
        if (scrollingDown && currentPos >= ($('#bottomOfBibleText').offset().top - $(window).height()) - DOWN_MARGIN && Date.now() > lastAddMoreTime + 1000) {
            lastAddMoreTime = Date.now();
            addMoreAtEnd();
        } else if (scrollingUp && currentPos < UP_MARGIN && Date.now() > lastAddMoreTime + 1000) {
            lastAddMoreTime = Date.now();
            addMoreAtTop();
        }
        currentPos = scrollPosition();
    };

    // Could add start() and stop() methods
    $(window).scroll(scrollHandler);
    //$(window).unbind("scroll", scrollHandler);
    window.addEventListener('touchstart', touchstartListener, false);
    window.addEventListener('touchend', touchendListener, false);
    window.addEventListener("touchcancel", touchendListener, false);

    function addMoreAtEnd() {
        if (endId<maxId && !stillLoading) {
            stillLoading = true;
            const id = ++endId;
            const textId = 'insertedText' + id;
            // place marker for text which may take longer to load
            const placeMarker = '<div id="' + textId + '" class="page_section">&nbsp;</div>';
            $(insertBeforeAtBottom).before(placeMarker);

            fnLoadTextAtEnd(id, textId);
        }
    }

    function addMoreAtTop() {
        if (touchDown) {
            // adding at top is tricky and if the user is stil holding there seems no way to set the scroll position after insert
            addMoreAtTopOnTouchUp = true;
        } else if (topId>minId && !stillLoading) {
            stillLoading = true;
            const id = --topId;
            const textId = 'insertedText' + id;
            // place marker for text which may take longer to load
            const placeMarker = '<div id="' + textId + '" class="page_section">&nbsp;</div>';
            insertAtTop($(insertAfterAtTop), placeMarker);

            fnLoadTextAtTop(id, textId);
        }
    }

    function insertAtTop($afterComponent, text) {
        const priorHeight = bodyHeight();
        $afterComponent.after(text);
        const changeInHeight = bodyHeight() - priorHeight;
        const adjustedPosition = currentPos + changeInHeight;
        setScrollPosition(adjustedPosition);
    }

    function touchstartListener(event){
        touchDown = true;
    }
    function touchendListener(event){
        touchDown = false;
        if (textToBeInsertedAtTop && idToInsertTextAt) {
            const text = textToBeInsertedAtTop;
            const id = idToInsertTextAt;
            textToBeInsertedAtTop = null;
            idToInsertTextAt = null;
            insertThisTextAtTop(id, text);
        }
        if (addMoreAtTopOnTouchUp) {
            addMoreAtTopOnTouchUp = false;
            addMoreAtTop()
        }
    }
}

let stillLoading = false;
let touchDown = false;
let textToBeInsertedAtTop = null;
let idToInsertTextAt = null;

export function initializeInfiniScroll() {
    const chapterInfo = JSON.parse(jsInterface.getChapterInfo());
    if (chapterInfo.infinite_scroll) {
        infiniScroll(
            loadTextAtTop,
            loadTextAtEnd,
            chapterInfo.chapter,
            chapterInfo.first_chapter,
            chapterInfo.last_chapter,
            "#topOfBibleText",
            "#bottomOfBibleText");
    }
}

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
function loadTextAtTop(chapter, textId) {
    console.log("js:loadTextAtTop");
    jsInterface.requestMoreTextAtTop(chapter, textId);
}

function loadTextAtEnd(chapter, textId) {
    console.log("js:loadTextAtEnd");
    jsInterface.requestMoreTextAtEnd(chapter, textId);
}

/**
 * called from java after actual text has been retrieved to request text is inserted
 */
export function insertThisTextAtTop(textId, text) {
    if (touchDown) {
        textToBeInsertedAtTop = text;
        idToInsertTextAt = textId;
    } else {
        const priorHeight = bodyHeight();
        const origPosition = scrollPosition();

        const $divToInsertInto = $('#' + textId);
        $divToInsertInto.html(text);

        // do no try to get scrollPosition here becasue it has not settled
        const adjustedTop = origPosition - priorHeight + bodyHeight();
        setScrollPosition(adjustedTop);

        registerVersePositions();
        stillLoading = false;
    }
}

export function insertThisTextAtEnd(textId, text) {
    console.log("js:insertThisTextAtEnd into:"+textId);
    $('#' + textId).html(text);

    registerVersePositions();
    stillLoading = false;
}
