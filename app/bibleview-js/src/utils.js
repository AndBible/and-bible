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

export function getVerseInfo(props) {
    if(!props.verseOrdinal) return null;
    const [book, chapter, verse] = props.osisID.split(".")
    return {ordinal: parseInt(props.verseOrdinal), osisID: props.osisID, book, chapter: parseInt(chapter), verse: parseInt(verse)}
}

export function setFrom(...args) {
    const set = new Set();
    args.forEach(v => set.add(v))
    return set;
}

export function addAll(set, ...args) {
    for(const a of args) {
        set.add(a);
    }
}

export function mapFrom(arr, keyFn, valueFn) {
    const map = new Map();
    arr.forEach(v => map.set(keyFn(v), valueFn(v)));
    return map;
}

export function arrayLeq([v11, v12], [v21, v22]) {
    if(v11 < v21) return true
    if(v11 === v21) return v12 <= v22;
    return false;
}

export function arrayGeq([v11, v12], [v21, v22]) {
    if(v11 > v21) return true
    if(v11 === v21) return v12 >= v22;
    return false;
}

export function arrayLe([v11, v12], [v21, v22]) {
    if(v11 < v21) return true
    if(v11 === v21) return v12 < v22;
    return false;
}

export function arrayGe([v11, v12], [v21, v22]) {
    if(v11 > v21) return true
    if(v11 === v21) return v12 > v22;
    return false;
}

export function arrayEq([v11, v12], [v21, v22]) {
    return (v11 === v21) && (v12 === v22)
}

export function findElemWithOsisID(elem) {
    if(elem === null) return;
    // This needs to be done unique for each OsisFragment (as there can be many).
    if(elem.dataset && elem.dataset.osisID) {
        return elem;
    }
    else if (elem.parentElement) {
        return elem.parentElement.closest('.osis') //findElemWithOsisID(elem.parentElement);
        //return findElemWithOsisID(elem.parentElement);
    }
}

export function findNodeAtOffset(elem, startOffset) {
    let offset = startOffset;
    do {
        for (const c of elem.childNodes) {
            if (c.nodeType === 1 && hasOsisContent(c)) { // element
                const textLength = contentLength(c);
                if (textLength >= offset) {
                    return findNodeAtOffset(c, offset);
                } else {
                    offset -= textLength;
                }
            } else if (c.nodeType === 3 && hasOsisContent(c.parentElement)) { // text
                if (c.length >= offset) {
                    return [c, offset];
                } else {
                    offset -= c.length;
                }
            }
        }
        elem = elem.nextSibling
    } while(elem)
}

export function contentLength(elem) {
    let length = 0;
    for(const c of elem.childNodes) {
        if(c.nodeType === 3 && hasOsisContent(c.parentNode)) {
            length += c.length;
        } else if(c.nodeType === 1) {
            length += contentLength(c)
        }
    }
    return length;
}

function hasOsisContent(element) {
    // something with content that should be counted in offset
    if(element === null) return false;
    return element.classList.contains("osis")
}

function hasParent(e, p) {
    if(p === null) return true
    while(e) {
        if(e === p) return true
        e = e.parentNode;
    }
    return false;
}

export function findNext(e, last, onlyOsis=false) {
    if(!hasParent(e, last)) return null
    if(e.nodeType === 3 && e === last) return last;
    let next;
    if(e.nodeType === 3) {
        next = e.previousSibling
    } else {
        if(!onlyOsis || (onlyOsis && e.classList.contains("osis"))) {
            next = e.lastChild || e.previousSibling;
        } else {
            next = e.previousSibling
        }
    }
    if(next) {
        if(next.nodeType === 1) {
            return findNext(next, last, onlyOsis);
        } else {
            return next;
        }
    }
    while (!next) {
        next = e.parentNode
        if(next === last) return next;

        let next2 = next.previousSibling
        if(next2) {
            if(next2.nodeType === 3) {
                return next2;
            } else {
                let next3
                if(!onlyOsis || (onlyOsis && next2.classList.contains("osis"))) {
                    next3 = next2.lastChild || next2.previousSibling;
                } else {
                    next3 = next2.previousSibling
                }
                if(next3) {
                    if (next3.nodeType === 3) {
                        return next3;
                    } else {
                        return findNext(next3, last, onlyOsis);
                    }
                } else {
                    return findNext(next2, last, onlyOsis);
                }
            }
        }
        e = next;
    }
    if(next === last) return next;
    return null;
}

function ordinalFromVerseElement(v) {
    return parseInt(v.id.split("-")[1]);
}

export function calculateOffsetToParent(node, parent, offset, start = true, {forceFromEnd = false} = {}) {
    let e = node;

    let offsetNow = offset;
    if(e.nodeType === 1) {
        if(!forceFromEnd) {
            if(parent === e) {
                return offset
            } else {
                e = findNext(e, parent, true);
            }
        } else {
            const next = e.lastChild
            if(next.nodeType === 3) {
                return calculateOffsetToParent(next, offsetNow + next.length, start)
            } else {
                return calculateOffsetToParent(next, offsetNow, start)
            }
        }
    } else {
        if(!hasOsisContent(e.parentNode)) {
            offsetNow = 0;
        }
    }

    for(
        e = findNext(e, parent, true);
        e !== parent;
        e = findNext(e, parent, true)
    ) {
        if (e.nodeType !== 3) throw new Error("Error!");
        if (hasOsisContent(e.parentNode)) {
            offsetNow += e.length;
        }
    }
    return offsetNow
}

export function findPreviousSiblingWithClass(node, cls) {
    let candidate = node;
    if(candidate.nodeType === 3) {
        node = node.parentNode;
        candidate = node;
    }
    const siblings = [];
    // TODO: SIMPLIFY
    if(candidate && !candidate.classList.contains(cls)) {
        siblings.push(candidate);
    }
    while(candidate && !candidate.classList.contains(cls)) {
        candidate = candidate.previousElementSibling;
        if(candidate && !candidate.classList.contains(cls)) {
            siblings.push(candidate);
        }
    }
    return {node, siblings, verseNode: candidate};
}

export function findParentsBeforeVerseSibling(node) {
    let candidate = findPreviousSiblingWithClass(node, "verse");
    if(candidate.verseNode) {
        return {
            parent: candidate.node,
            siblings: candidate.siblings,
            verseNode: candidate.verseNode
        };
    } else {
        return findParentsBeforeVerseSibling(node.parentNode)
    }
}

export function calculateOffsetToVerse(node, offset, start = true) {
    let parent;
    if(node.nodeType === 3) {
        parent = node.parentNode.closest(".verse");
    } else {
        parent = node.closest(".verse");
        node = node.firstChild || node.previousSibling
    }
    let offsetNow = 0;
    if(!parent) {
        const {verseNode, siblings} = findParentsBeforeVerseSibling(node)
        const lastSibling = siblings.shift();
        if(hasOsisContent(lastSibling)) {
            const t = findNext(node, lastSibling, true);
            if (t) offsetNow += calculateOffsetToParent(t, lastSibling, offset)
        }

        for(const s of siblings.filter(s => hasOsisContent(s))) {
            const t = findNext(s, s, true);
            if(t) offsetNow += calculateOffsetToParent(t, s, t.length)
        }
        parent = verseNode
        node = findNext(verseNode, verseNode, true)
        offset = node.length
    }

    offsetNow += calculateOffsetToParent(node, parent, offset, start);
    return {offset: offsetNow, ordinal: ordinalFromVerseElement(parent)}
}

export function findLegalPositionOld(node, offset, start) {
    let e = node;

    let offsetNow = offset;

    if(!start) {
        while (e.previousSibling !== null) {
            e = e.previousSibling
            if (e.nodeType === 3) {
                offsetNow += e.length;
            } else if (e.nodeType === 1) {
                offsetNow += contentLength(e);
            }
        }
    }

    if(!hasOsisContent(e.parentElement)) {
        return findLegalPosition(e.parentElement, offsetNow-1, start);
    }

    const elementCount = parseInt(e.parentElement.dataset.elementCount)
    const ordinal = parseInt(e.parentElement.dataset.ordinal)
    return {ordinal, elementCount, offset: offsetNow};
}

export function rangesOverlap(bookmarkRange, testRange, addRange = false) {
    let rs, re, bs, be;
    if(addRange) {
        rs = [testRange[0], 0];
        re = [testRange[1], 0];
        bs = [bookmarkRange[0], 0];
        be = [bookmarkRange[1], 0];
    } else {
        [rs, re] = testRange;
        [bs, be] = bookmarkRange;
    }

    // Same condition as in kotlin side BookmarksDao.bookmarksForVerseRange
    const ex1 = arrayLeq(rs, bs) && arrayLe(bs, re);
    const ex2 = arrayLe(rs, be) && arrayLeq(be, re);
    const ex3 = false; //arrayLe(bs, re) && arrayGe(rs, be);
    const ex4 = arrayLeq(bs, rs) && arrayLe(rs, be) && arrayLe(bs, re) && arrayLeq(re, be);

    return (ex1 || ex2 || ex3 || ex4)
}

