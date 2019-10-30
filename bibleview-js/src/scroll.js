let currentAnimation = null;
let stopAnimation = false;
export let toolbarOffset = 0;

export function setToolbarOffset(value, options) {
    console.log("setToolbarOffset", value, options);
    const opts = options || {};
    const diff = toolbarOffset - value;
    toolbarOffset = value;
    let delay = 500;
    if(opts.immediate) {
        delay = 0;
    }

    if(diff !== 0 && !opts.doNotScroll) {
        doScrolling(window.pageYOffset + diff, delay)
    }
}


export function jsonscroll() {
    jsInterface.onScroll(window.pageYOffset);
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
        }
    })
}

export function doScrollToSlowly(element, elementPosition, to) {
    // 25 pixels/100ms is the standard speed
    const speed = 25;
    const difference = to - elementPosition;
    if (difference === 0) return;
    const perTick = Math.max(Math.min(speed, difference), -speed);

    setTimeout(function() {
        // scrolling is sometimes delayed so keep track of scrollTop rather than calling element.scrollTop
        const newElementScrollTop = elementPosition + perTick;
        element.scrollTop = newElementScrollTop;
        doScrollTo(element, newElementScrollTop, to);
    }, 100);
}

export function scrollToVerse(toId, now, deltaParam) {
    console.log("scrollToVerse", toId, now, deltaParam);
    stopAnimation = true;
    let delta = toolbarOffset;
    if(deltaParam !== undefined) {
        delta = deltaParam;
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

export function stopScrolling() {
    stopAnimation = true;
}
