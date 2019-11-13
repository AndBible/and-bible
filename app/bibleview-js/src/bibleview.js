/**
 * WebView js functions for moving to verse, selecting verses
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */

import {jsonscroll, stopScrolling} from "./scroll";
let lineHeight = 0;

export function registerVersePositions() {
    console.log("Registering verse positions", lineHeight);
    jsInterface.clearVersePositionCache();

    const verseTags = getVerseElements();
    console.log("Num verses found:"+verseTags.length);
    for (let i=0; i<verseTags.length; i++) {
        const verseTag = verseTags[i];
        // send position of each verse to java to allow calculation of current verse after each scroll
        jsInterface.registerVersePosition(verseTag.id, verseTag.offsetTop
            + Math.max(0, verseTag.offsetHeight - 2*lineHeight));
    }
}

function getVerseElements() {
    return getElementsByClass("verse", document.body, "span")
}

function getElementsByClass( searchClass, domNode, tagName) {
    if (domNode == null) domNode = document;
    if (tagName == null) tagName = '*';
    const matches = [];

    const tagMatches = domNode.getElementsByTagName(tagName);
    console.log("Num spans found:"+tagMatches.length);

    const searchClassPlusSpace = " " + searchClass + " ";
    for(let i=0; i<tagMatches.length; i++) {
        const tagClassPlusSpace = " " + tagMatches[i].className + " ";
        if (tagClassPlusSpace.indexOf(searchClassPlusSpace) !== -1)
            matches.push(tagMatches[i]);
    }
    return matches;
}

export function initialize() {
    console.log("JS onLoad start");
    lineHeight = parseFloat(window.getComputedStyle(document.body).getPropertyValue('line-height'));
    registerVersePositions();
    $(document).bind("touchstart", event => stopScrolling());
    window.addEventListener("scroll", event => jsonscroll());
    console.log("JS onload finish");
    jsInterface.triggerJumpToOffset();
}

