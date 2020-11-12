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
 * WebView js functions for moving to verse, selecting verses
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */

import {updateLocation, stopScrolling} from "./scroll";
import $ from "jquery"

export function registerVersePositions(now = false) {
    // Register verse positions after next screen refresh ensuring that css / font is rendered correctly.
    const fn = () => {
        const lineHeight = parseFloat(window.getComputedStyle(document.body).getPropertyValue('line-height'));
        console.log("Registering verse positions", lineHeight);
        jsInterface.clearVersePositionCache();

        const verseTags = getVerseElements();
        console.log("Num verses found:" + verseTags.length);
        // send position of each verse to java to allow calculation of current verse after each scroll
        for (let i = 0; i < verseTags.length; i++) {
            const verseTag = verseTags[i];

            const location = verseTag.offsetTop - 0.8*lineHeight;
            jsInterface.registerVersePosition(verseTag.id, location);
        }
        updateLocation()
    }
    if(now) {fn()} else setTimeout(fn);
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

export function initializeListeners() {
    $(document).bind("touchstart", event => stopScrolling());
    window.addEventListener("scroll", event => updateLocation());
}

