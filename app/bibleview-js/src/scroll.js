import {addWaiter, Deferred, waitForWaiters} from "./utils";
import {enableVerseLongTouchSelectionMode} from "./highlighting";

let currentAnimation = null;
let stopAnimation = false;
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


export function jsonscroll() {
    if(currentAnimation == null) {
        jsInterface.onScroll(window.pageYOffset);
    }
}

export function doScrolling(elementY, duration) {
    console.log("doScrolling", elementY, duration);
    stopAnimation = false;
    const startingY = window.pageYOffset;
    const diff = elementY - startingY;
    let start;
    if(currentAnimation) {
        window.cancelAnimationFrame(currentAnimation);
    }

    if(duration === 0) {
        window.scrollTo(0, elementY);
        return;
    }

    // Bootstrap our animation - it will get called right before next frame shall be rendered.
    currentAnimation = window.requestAnimationFrame(function step(timestamp) {
        if (!start) start = timestamp;
        // Elapsed milliseconds since start of scrolling.
        const time = timestamp - start;
        // Get percent of completion in range [0, 1].
        const percent = Math.min(time / duration, 1);

        window.scrollTo(0, startingY + diff * percent);

        // Proceed with animation as long as we wanted it to.
        if (time < duration && stopAnimation === false) {
            currentAnimation = window.requestAnimationFrame(step);
        }
        else {
            currentAnimation = null;
            jsonscroll();
        }
    })
}

export async function scrollToVerse(toId, now, delta = toolbarOffset) {
    console.log("scrollToVerse", toId, now, delta);
    stopAnimation = true;
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

export const isReady = new Deferred();
addWaiter(isReady);

export function setupContent({isBible = false} = {}) {
    $("#content").css('visibility', 'visible');
    setToolbarOffset(jsInterface.getToolbarOffset(), {immediate: true});
    if(isBible) {
        enableVerseLongTouchSelectionMode();
    } else {
        scrollToVerse(null, true);
    }
    isReady.resolve();
    console.log("setVisible OK");
}

export function stopScrolling() {
    stopAnimation = true;
}
