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

import {Optional} from "@/types/common";

type TextNodeAndOffset = [node: Text | null, offset: number | null]

function findNodeAtOffsetRecursive(element: ChildNode, startOffset: number): TextNodeAndOffset {
    let offset = startOffset;
    let elem: ChildNode | null = element
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
                const t = c as Text
                if (t.length >= offset) {
                    return [t, offset];
                } else {
                    offset -= t.length;
                }
            }
        }
        elem = elem.nextSibling
    } while (elem)
    return [null, null]
}

export function findNodeAtOffset(elem: ChildNode, startOffset: number): TextNodeAndOffset {
    return findNodeAtOffsetRecursive(elem, startOffset)
}

export function contentLength(elem: Node): number {
    let length = 0;
    for (const c of elem.childNodes) {
        if (c.nodeType === Node.TEXT_NODE && hasOsisContent(c.parentNode)) {
            length += (c as Text).length;
        } else if (c.nodeType === Node.ELEMENT_NODE) {
            length += contentLength(c)
        }
    }
    return length;
}

function hasOsisContent(element: Optional<Node>): boolean {
    // something with content that should be counted in offset
    if (!element) return false;
    if (element.nodeType === Node.DOCUMENT_TYPE_NODE) return true;
    if (element.nodeType !== Node.ELEMENT_NODE) element = element.parentElement!;
    return !(<Element>element).closest(".skip-offset")
}

function isParent(elem: Node | null, parent: Node | null): boolean {
    if (parent === null) return true
    while (elem) {
        if (elem === parent) return true
        elem = elem.parentNode;
    }
    return false;
}

export function* walkBackText(e: Node, onlyOsis = false): Generator<Text> {
    let next: Node | null = e
    const osisCheck = (e: Node) => !onlyOsis || (onlyOsis && hasOsisContent(e))

    do {
        if ([Node.TEXT_NODE, Node.COMMENT_NODE].includes(next.nodeType)) {
            if (next.nodeType === Node.TEXT_NODE && osisCheck(next)) {
                yield next as Text;
            }
            let next2: Node | null = next.previousSibling;
            if (next2) {
                next = next2;
            } else {
                while (!next2) {
                    next = next!.parentNode;
                    if (next) {
                        const next3: ChildNode | null = next.previousSibling;
                        if (next3) {
                            next2 = next3;
                        }
                    }
                }
                next = next2
            }
        } else if (next.nodeType === Node.ELEMENT_NODE) {
            do {
                let next2: Node | null = next.lastChild;
                if (next2) {
                    while (next2 && !osisCheck(next2)) {
                        next2 = next2.previousSibling;
                    }
                    if (next2) next = next2;
                }
                if (!next2) {
                    let next3: Node | null;
                    next2 = next;
                    do {
                        next3 = next2!.previousSibling;
                        next2 = next2!.parentElement;
                    } while (!next3);
                    next = next3;
                }
            } while (!osisCheck(next))
        } else if (next.nodeType === Node.DOCUMENT_TYPE_NODE) {
            next = null;
        } else {
            throw Error(`Unsupported ${next.nodeType}`);
        }
    } while (next);
}

export function findNext(e: Node, last: Node, onlyOsis = false): Text | null {
    const iterator = walkBackText(e, onlyOsis);
    const osisCheck = (e: Node) => !onlyOsis || (onlyOsis && hasOsisContent(e))
    if (e.nodeType === Node.TEXT_NODE && osisCheck(e)) {
        iterator.next();
    }

    const next = iterator.next().value;
    if (!isParent(next, last)) {
        return null;
    }
    return next;
}

export function textLength(element: Element) {
    const lastElem = lastTextNode(element);
    return calculateOffsetToParent(lastElem, element, lastElem.length)
}

function ordinalFromVerseElement(v: Element) {
    return parseInt(v.id.split("-")[1]);
}

export function calculateOffsetToParent(
    node: Node,
    parent: Element,
    offset: number,
): number {
    let e = node;

    let offsetNow = offset;
    if (e.nodeType === Node.ELEMENT_NODE) {
        if (parent === e) {
            return offset
        } else {
            e = findNext(e, parent, true)!;
        }
    } else if (e.nodeType === Node.TEXT_NODE) {
        if (!hasOsisContent(e.parentNode)) {
            offsetNow = 0;
        }
    } else if (e.nodeType === Node.COMMENT_NODE) {
        offsetNow = 0;
    } else throw new Error(`Unknown node type ${e.nodeType}`);
    const iter = walkBackText(e, true);
    if (hasOsisContent(e)) {
        iter.next();
    }
    for (e of iter) {
        if (!isParent(e, parent)) break;
        if (e.nodeType !== Node.TEXT_NODE) {
            throw new Error(`Error! ${e} ${e.nodeType}`);
        }
        const t = e as Text
        if (hasOsisContent(t.parentNode)) {
            offsetNow += t.length;
        }
    }
    return offsetNow
}

export class ReachedRootError extends Error {
}

type NodeAndSiblings = { node: Node, siblings: Element[], verseNode: Element | null }
type ParentAndSiblings = { parent: Node, siblings: Element[], verseNode: Element }

export function findPreviousSiblingWithClass(node: Node, cls: string): NodeAndSiblings {
    let candidate: Node = node;
    if (candidate.nodeType === Node.TEXT_NODE) {
        node = node.parentElement!;
        candidate = node;
    }
    if (candidate.nodeType === Node.DOCUMENT_NODE) {
        throw new ReachedRootError();
    }

    let candidateElement = candidate as Element | null

    const siblings: Element[] = [];
    // TODO: SIMPLIFY
    if (candidateElement && !candidateElement.classList.contains(cls)) {
        siblings.push(candidateElement);
    }
    while (candidateElement && !candidateElement.classList.contains(cls)) {
        candidateElement = candidateElement.previousElementSibling;
        if (candidateElement && !candidateElement.classList.contains(cls)) {
            siblings.push(candidateElement);
        }
    }
    return {node, siblings, verseNode: candidateElement};
}

export function findParentsBeforeVerseSibling(node: Node): ParentAndSiblings {
    const candidate = findPreviousSiblingWithClass(node, "verse");
    if (candidate.verseNode) {
        return {
            parent: candidate.node,
            siblings: candidate.siblings,
            verseNode: candidate.verseNode
        };
    } else {
        return findParentsBeforeVerseSibling(node.parentNode!)
    }
}

export function calculateOffsetToVerse(node: Node, offset: number) {
    let parent: Element | null = null;
    if ([Node.TEXT_NODE, Node.COMMENT_NODE].includes(node.nodeType)) {
        parent = node.parentElement!.closest(".verse");
    } else if (node.nodeType === Node.ELEMENT_NODE) {
        parent = (node as Element).closest(".verse");
        node = (node.firstChild || node.previousSibling)!
    }
    let offsetNow = 0;
    if (!parent) {
        const {verseNode, siblings} = findParentsBeforeVerseSibling(node)
        const lastSibling = siblings.shift();
        if (lastSibling && hasOsisContent(lastSibling)) {
            const t = findNext(node, lastSibling, true);
            if (t) offsetNow += calculateOffsetToParent(t, lastSibling, offset)
            else offsetNow += offset;
        }

        for (const s of siblings.filter(s => hasOsisContent(s))) {
            const t = findNext(s, s, true);
            if (t) offsetNow += calculateOffsetToParent(t, s, t.length)
        }
        parent = verseNode
        const txt = findNext(verseNode, verseNode, true)!
        offset = txt.length
        node = txt
    }

    offsetNow += calculateOffsetToParent(node, parent, offset);
    return {offset: offsetNow, ordinal: ordinalFromVerseElement(parent)}
}

export function lastTextNode(elem: Node): Text {
    return walkBackText(elem, true).next().value;
}
