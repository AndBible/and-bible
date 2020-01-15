import {enableVerseLongTouchSelectionMode} from "./highlighting";
import {registerVersePositions} from "./bibleview";

let currentScrollAnimation = null;
let contentReady = false;
export let toolbarOffset = 0;

export function setToolbarOffset(value, {doNotScroll = false, immediate = false} = {}) {
    console.log("setToolbarOffset", value, doNotScroll, immediate);
    const diff = toolbarOffset - value;
    toolbarOffset = value;
    const delay = immediate ? 0 : 500;

    if(diff !== 0 && !doNotScroll) {
        doScrolling(window.pageYOffset + diff, delay)
    }
}

export function updateLocation() {
    if(currentScrollAnimation == null) {
        jsInterface.onScroll(window.pageYOffset);
    }
}

export function stopScrolling() {
    if(currentScrollAnimation != null) {
        window.cancelAnimationFrame(currentScrollAnimation);
        currentScrollAnimation = null;
        console.log("Animation ends");
    }
}

export function doScrolling(elementY, duration) {
    console.log("doScrolling", elementY, duration);
    stopScrolling();
    const startingY = window.pageYOffset;
    const diff = elementY - startingY;
    let start;

    if(duration === 0) {
        window.scrollTo(0, elementY);
        return;
    }

    // Bootstrap our animation - it will get called right before next frame shall be rendered.
    console.log("Animation starts");
    currentScrollAnimation = window.requestAnimationFrame(function step(timestamp) {
        if (!start) start = timestamp;
        // Elapsed milliseconds since start of scrolling.
        const time = timestamp - start;
        // Get percent of completion in range [0, 1].
        const percent = Math.min(time / duration, 1);

        window.scrollTo(0, startingY + diff * percent);

        // Proceed with animation as long as we wanted it to.
        if (time < duration) {
            currentScrollAnimation = window.requestAnimationFrame(step);
        }
        else {
            updateLocation();
        }
    })
}

export async function scrollToVerse(toId, now, delta = toolbarOffset) {
    console.log("scrollToVerse", toId, now, delta);
    stopScrolling();
    if(delta !== toolbarOffset) {
        toolbarOffset = delta;
    }

    const toElement = document.getElementById(toId) || document.getElementById("topOfBibleText");

    if (toElement != null) {
        const diff = toElement.offsetTop - window.pageYOffset;
        if(Math.abs(diff) > 800 / window.devicePixelRatio) {
            now = true;
        }
        console.log("Scrolling to", toElement, toElement.offsetTop - delta);
        if(now===true) {
            window.scrollTo(0, toElement.offsetTop - delta);
        }
        else {
            doScrolling(toElement.offsetTop - delta, 1000);
        }
    }
}

export function setupContent({jumpToChapterVerse, jumpToYOffsetRatio, toolBarOffset} = {}) {
    console.log("setupContent", jumpToChapterVerse, jumpToYOffsetRatio, toolBarOffset);
    const doScroll = jumpToYOffsetRatio != null && jumpToYOffsetRatio > 0;
    setToolbarOffset(toolBarOffset, {immediate: true, doNotScroll: !doScroll});
    if(jumpToChapterVerse != null) {
        scrollToVerse(jumpToChapterVerse, true);
        enableVerseLongTouchSelectionMode();
    } else if(doScroll) {
        console.log("jumpToYOffsetRatio", jumpToYOffsetRatio);
        const
            contentHeight = document.documentElement.scrollHeight,
            y = contentHeight * jumpToYOffsetRatio / window.devicePixelRatio;
        doScrolling(y, 0)
    } else {
        console.log("scrolling to beginning of document (now)");
        scrollToVerse(null, true);
    }
    // requestAnimationFrame should make sure that contentReady is set only after
    // initial scrolling has been performed so that we don't get onScroll during initialization
    // in Java side.
    requestAnimationFrame(() => {
        $("#content").css('visibility', 'visible');
        registerVersePositions();
        contentReady = true;
        jsInterface.setContentReady();
    });
    console.log("setVisible OK");
}
export function hideContent() {
    $("#content").css('visibility', 'hidden');
}

