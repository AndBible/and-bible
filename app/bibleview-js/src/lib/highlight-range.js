/*
 * Copyright (c) 2021 Martin Denham, Tuomas Airaksinen and the And Bible contributors.
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
 * Modified from https://github.com/Treora/dom-highlight-range
 * (which, at the time of adoption, is public domain)
 */

export function highlightRange(range, tagName = 'mark', attributes = {}) {
    if (range.collapsed) return;

    // First put all nodes in an array (splits start and end nodes if needed)
    const nodes = textNodesInRange(range);

    // Highlight each node
    const highlightElements = [];
    for (const nodeIdx in nodes) {
        const highlightElement = wrapNodeInHighlight(nodes[nodeIdx], tagName, attributes);
        if(highlightElement) {
            highlightElements.push(highlightElement);
        }
    }

    // Return a function that cleans up the highlightElements.
    function removeHighlights() {
        // Remove each of the created highlightElements.
        for (const highlightIdx in highlightElements) {
            removeHighlight(highlightElements[highlightIdx]);
        }
    }
    return {undo: removeHighlights, highlightElements};
}

// Return an array of the text nodes in the range. Split the start and end nodes if required.
function textNodesInRange(range) {
    // If the start or end node is a text node and only partly in the range, split it.
    if (range.startContainer.nodeType === Node.TEXT_NODE && range.startOffset > 0) {
        const endOffset = range.endOffset; // (this may get lost when the splitting the node)
        const createdNode = range.startContainer.splitText(range.startOffset);
        if (range.endContainer === range.startContainer) {
            // If the end was in the same container, it will now be in the newly created node.
            range.setEnd(createdNode, endOffset - range.startOffset);
        }
        range.setStart(createdNode, 0);
    }
    if (
        range.endContainer.nodeType === Node.TEXT_NODE
        && range.endOffset < range.endContainer.length
    ) {
        range.endContainer.splitText(range.endOffset);
    }

    // Collect the text nodes.
    const walker = range.startContainer.ownerDocument.createTreeWalker(
        range.commonAncestorContainer,
        NodeFilter.SHOW_TEXT,
        node => range.intersectsNode(node) ? NodeFilter.FILTER_ACCEPT : NodeFilter.FILTER_REJECT,
    );
    walker.currentNode = range.startContainer;

    // // Optimise by skipping nodes that are explicitly outside the range.
    // const NodeTypesWithCharacterOffset = [
    //  Node.TEXT_NODE,
    //  Node.PROCESSING_INSTRUCTION_NODE,
    //  Node.COMMENT_NODE,
    // ];
    // if (!NodeTypesWithCharacterOffset.includes(range.startContainer.nodeType)) {
    //   if (range.startOffset < range.startContainer.childNodes.length) {
    //     walker.currentNode = range.startContainer.childNodes[range.startOffset];
    //   } else {
    //     walker.nextSibling(); // TODO verify this is correct.
    //   }
    // }

    const nodes = [];
    if (walker.currentNode.nodeType === Node.TEXT_NODE)
        nodes.push(walker.currentNode);
    while (walker.nextNode() && range.comparePoint(walker.currentNode, 0) !== 1)
        nodes.push(walker.currentNode);
    return nodes;
}

// Replace [node] with <tagName ...attributes>[node]</tagName>
function wrapNodeInHighlight(node, tagName, attributes) {
    if(node.textContent.length === 0) {
        node.remove();
        return null;
    }
    const highlightElement = node.ownerDocument.createElement(tagName);
    Object.keys(attributes).forEach(key => {
        highlightElement.setAttribute(key, attributes[key]);
    });
    const tempRange = node.ownerDocument.createRange();
    tempRange.selectNode(node);
    tempRange.surroundContents(highlightElement);
    return highlightElement;
}

// Remove a highlight element created with wrapNodeInHighlight.
function removeHighlight(highlightElement) {
    if (highlightElement.childNodes.length === 1) {
        if(highlightElement.firstChild.textContent.length === 0) {
            highlightElement.remove();
        } else {
            highlightElement.parentNode.replaceChild(highlightElement.firstChild, highlightElement);
        }
    } else {
        // If the highlight somehow contains multiple nodes now, move them all.
        while (highlightElement.firstChild) {
            highlightElement.parentNode.insertBefore(highlightElement.firstChild, highlightElement);
        }
        highlightElement.remove();
    }
}
