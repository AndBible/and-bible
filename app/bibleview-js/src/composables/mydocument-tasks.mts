/*
 * Copyright (c) 2026 Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
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

import {Marked, type Tokens} from "marked";

const parser = new Marked({gfm: true});
const taskMarker = /^(?:[-+*]|\d+[.)])[ \t]+\[([ xX])\]/;
const displayedMarker = /^\[([ xX])\](?=\s|$)/;

type ListItemPosition = {offset: number | null, checked: boolean} | null;

function sourceItems(markdown: string): ListItemPosition[] {
    const result: ListItemPosition[] = [];
    // Marked normalizes CRLF/CR to LF; keep a map back to the original text so
    // checkbox replacements preserve the document's original line endings.
    const originalOffsets: number[] = [];
    let normalized = "";
    for (let i = 0; i < markdown.length; i++) {
        originalOffsets.push(i);
        if (markdown[i] === "\r") {
            normalized += "\n";
            if (markdown[i + 1] === "\n") i++;
        } else {
            normalized += markdown[i];
        }
    }

    function appendUnmatched(list: Tokens.List) {
        for (const item of list.items) {
            result.push(null);
            for (const token of item.tokens) {
                if (token.type === "list") appendUnmatched(token as Tokens.List);
            }
        }
    }

    function visitList(list: Tokens.List, start: number) {
        let cursor = start;
        for (const item of list.items) {
            const position = normalized.indexOf(item.raw, cursor);
            const insideList = position >= cursor && position + item.raw.length <= start + list.raw.length;
            const marker = taskMarker.exec(item.raw);
            result.push(insideList && item.task && marker
                ? {offset: originalOffsets[position + marker[0].length - 3], checked: marker[1].toLowerCase() === "x"}
                : null);

            if (insideList) {
                for (const token of item.tokens) {
                    if (token.type === "list") {
                        const nestedStart = normalized.indexOf(token.raw, position + (marker?.[0].length ?? 0));
                        const itemEnd = position + item.raw.length;
                        const anotherMatch = normalized.indexOf(token.raw, nestedStart + token.raw.length);
                        if (nestedStart >= 0 && nestedStart + token.raw.length <= itemEnd &&
                            (anotherMatch < 0 || anotherMatch >= itemEnd)) {
                            visitList(token as Tokens.List, nestedStart);
                        } else {
                            appendUnmatched(token as Tokens.List);
                        }
                    }
                }
                cursor = position + item.raw.length;
            }
        }
    }

    let offset = 0;
    for (const token of parser.lexer(normalized)) {
        if (token.type === "list") visitList(token as Tokens.List, offset);
        offset += token.raw.length;
    }
    return result;
}

function firstItemText(li: HTMLLIElement): Text | null {
    const walker = document.createTreeWalker(li, NodeFilter.SHOW_TEXT);
    let node: Text | null;
    while ((node = walker.nextNode() as Text | null)) {
        if (node.parentElement?.closest("li") !== li) continue;
        if (node.parentElement?.closest(".skip-offset, .ordinal-badge")) continue;
        if (node.textContent?.trim()) return node;
    }
    return null;
}

/** Enhance only task markers whose source and rendered list positions both agree. */
export function enhanceMyDocumentTasks(root: HTMLElement, source: string, onChange: (markdown: string) => void) {
    const items = sourceItems(source);
    const renderedItems = Array.from(root.querySelectorAll("li"));
    if (items.length !== renderedItems.length) return;

    let current = source;
    items.forEach((item, index) => {
        if (!item || item.offset === null) return;
        const li = renderedItems[index];
        const text = firstItemText(li);
        const match = text && displayedMarker.exec(text.data.trimStart());
        if (!match || (match[1].toLowerCase() === "x") !== item.checked) return;
        if (current.slice(item.offset, item.offset + 3).toLowerCase() !== `[${item.checked ? "x" : " "}]`) return;

        const markerStart = text!.data.length - text!.data.trimStart().length;
        const checkbox = document.createElement("input");
        checkbox.type = "checkbox";
        checkbox.className = "mydoc-task-checkbox";
        checkbox.checked = item.checked;
        checkbox.setAttribute("aria-label", li.textContent?.replace(/^\s*\[[ xX]\]\s*/, "").trim() || "");
        text!.deleteData(markerStart, 3);
        text!.parentNode!.insertBefore(checkbox, text);
        li.classList.add("mydoc-task-item");
        checkbox.addEventListener("click", event => event.stopPropagation());
        checkbox.addEventListener("change", event => {
            event.stopPropagation();
            current = current.slice(0, item.offset!) + `[${checkbox.checked ? "x" : " "}]` + current.slice(item.offset! + 3);
            onChange(current);
        });
    });
}
