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


function findNodeAtOffsetRecursive(elem, startOffset) {
    let offset = startOffset;
    do {
        for (const c of elem.childNodes) {
            if (c.nodeType === Node.ELEMENT_NODE && hasOsisContent(c)) {
                const textLength = contentLength(c);
                if (textLength >= offset) {
                    return findNodeAtOffsetRecursive(c, offset);
                } else {
                    offset -= textLength;
                }
            } else if (c.nodeType === Node.TEXT_NODE && hasOsisContent(c.parentElement)) {
                if (c.length >= offset) {
                    return [c, offset];
                } else {
                    offset -= c.length;
                }
            }
        }
        elem = elem.nextSibling
    } while(elem)
    return [null, null]
}

export function findNodeAtOffset(elem, startOffset) {
    return findNodeAtOffsetRecursive(elem, startOffset)
}

export function contentLength(elem) {
    let length = 0;
    for(const c of elem.childNodes) {
        if(c.nodeType === Node.TEXT_NODE && hasOsisContent(c.parentNode)) {
            length += c.length;
        } else if(c.nodeType === Node.ELEMENT_NODE) {
            length += contentLength(c)
        }
    }
    return length;
}

function hasOsisContent(element) {
    // something with content that should be counted in offset
    if(element === null) return false;
    if(element.nodeType === Node.DOCUMENT_TYPE_NODE) return true;
    if(element.nodeType !== Node.ELEMENT_NODE) element = element.parentElement;
    return !element.closest(".skip-offset")
}

function hasParent(e, p) {
    if(p === null) return true
    while(e) {
        if(e === p) return true
        e = e.parentNode;
    }
    return false;
}

export function* walkBackText(e, onlyOsis = false) {
    let next = e
    const osisCheck = e => !onlyOsis || (onlyOsis && hasOsisContent(e))
    do {
        if([Node.TEXT_NODE, Node.COMMENT_NODE].includes(next.nodeType)) {
            if(next.nodeType === Node.TEXT_NODE && osisCheck(next)) {
                yield next;
            }
            let next2 = next.previousSibling;
            if(next2) {
                next = next2;
            } else {
                while (!next2) {
                    next = next.parentNode;
                    if (next) {
                        let next3 = next.previousSibling;
                        if (next3) {
                            next2 = next3;
                        }
                    }
                }
                next = next2
            }
        } else if(next.nodeType === Node.ELEMENT_NODE) {
            do {
                let next2 = next.lastChild;
                if (next2) {
                    while (next2 && !osisCheck(next2)) {
                        next2 = next2.previousSibling;
                    }
                    if (next2) next = next2;
                }
                if (!next2) {
                    let next3;
                    next2 = next;
                    do {
                        next3 = next2.previousSibling;
                        next2 = next2.parentElement;
                    } while (!next3);
                    next = next3;
                }
            } while(!osisCheck(next))
        } else if(next.nodeType === Node.DOCUMENT_TYPE_NODE) {
            next = null;
        } else {
            throw Error(`Unsupported ${next.nodeType}`);
        }
    } while(next);
}

export function findNext(e, last, onlyOsis=false) {
    const iterator = walkBackText(e, onlyOsis);
    const osisCheck = e => !onlyOsis || (onlyOsis && hasOsisContent(e))
    if(e.nodeType === Node.TEXT_NODE && osisCheck(e)) {
        iterator.next();
    }

    const next = iterator.next().value;
    if(!hasParent(next, last)) return null
    return next;
}

export function textLength(element) {
    const lastElem = lastTextNode(element);
    return calculateOffsetToParent(lastElem, element, lastElem.length)
}

function ordinalFromVerseElement(v) {
    return parseInt(v.id.split("-")[1]);
}

export function calculateOffsetToParent(node, parent, offset, {forceFromEnd = false} = {}) {
    let e = node;

    let offsetNow = offset;
    if(e.nodeType === Node.ELEMENT_NODE) {
        if(!forceFromEnd) {
            if(parent === e) {
                return offset
            } else {
                e = findNext(e, parent, true);
            }
        } else {
            const next = e.lastChild
            if(next.nodeType === Node.TEXT_NODE) {
                return calculateOffsetToParent(next, offsetNow + next.length)
            } else {
                return calculateOffsetToParent(next, offsetNow)
            }
        }
    } else if (e.nodeType === Node.TEXT_NODE) {
        if(!hasOsisContent(e.parentNode)) {
            offsetNow = 0;
        }
    }  else if(e.nodeType === Node.COMMENT_NODE) {
        offsetNow = 0;
    } else throw new Error(`Unknown node type ${e.nodeType}`);
    const iter = walkBackText(e, true);
    if(hasOsisContent(e)) {
        iter.next();
    }
    for(e of iter) {
        if(!hasParent(e, parent)) break;
        if (e.nodeType !== Node.TEXT_NODE) {
            throw new Error(`Error! ${e} ${e.nodeType}`);
        }
        if (hasOsisContent(e.parentNode)) {
            offsetNow += e.length;
        }
    }
    return offsetNow
}

export class ReachedRootError extends Error {}

export function findPreviousSiblingWithClass(node, cls) {
    let candidate = node;
    if(candidate.nodeType === Node.TEXT_NODE) {
        node = node.parentNode;
        candidate = node;
    }
    if(candidate.nodeType === Node.DOCUMENT_NODE) {
        throw new ReachedRootError();
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

export function calculateOffsetToVerse(node, offset) {
    let parent;
    if([Node.TEXT_NODE, Node.COMMENT_NODE].includes(node.nodeType)) {
        parent = node.parentNode.closest(".verse");
    } else if(node.nodeType === Node.ELEMENT_NODE){
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
            else offsetNow += offset;
        }

        for(const s of siblings.filter(s => hasOsisContent(s))) {
            const t = findNext(s, s, true);
            if(t) offsetNow += calculateOffsetToParent(t, s, t.length)
        }
        parent = verseNode
        node = findNext(verseNode, verseNode, true)
        offset = node.length
    }

    offsetNow += calculateOffsetToParent(node, parent, offset);
    return {offset: offsetNow, ordinal: ordinalFromVerseElement(parent)}
}

export function lastTextNode(elem) {
    return walkBackText(elem, true).next().value;
}
