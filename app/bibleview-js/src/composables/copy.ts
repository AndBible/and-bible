/*
 * Copyright (c) 2026 Martin Denham, Tuomas Airaksinen and the AndBible contributors.
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

import {ComputedRef} from "vue";
import {useAndroid} from "@/composables/android";
import {useKeyboard} from "@/composables/keyboard";
import {BibleViewDocumentType} from "@/types/documents";

function isSelectionInModal(): boolean {
    const selection = window.getSelection();
    if (!selection || selection.rangeCount < 1 || selection.isCollapsed) return false;
    const range = selection.getRangeAt(0);
    const node = range.startContainer instanceof Element
        ? range.startContainer
        : range.startContainer.parentElement;
    return node?.closest("#modals") != null;
}

/**
 * Sets up a keyboard listener for Ctrl+c to copy text.
 */
export function useCopy(
    {android, keyboard, documentType}:
    {android: ReturnType<typeof useAndroid>,
     keyboard: ReturnType<typeof useKeyboard>,
     documentType: ComputedRef<BibleViewDocumentType>}
) {
    function handleCopyText(): boolean {
        if (isSelectionInModal()) {
            const selectedText = window.getSelection()?.toString();
            if (selectedText && selectedText.trim()) {
                android.copyText(selectedText);
                return true;
            }
            return false;
        }

        if (documentType.value === "bible") {
            const sel = android.querySelection();
            if (sel == null) return false;
            if (typeof sel !== "string") {
                android.copyVerse(sel.bookInitials, sel.startOrdinal, sel.endOrdinal);
            } else {
                android.copyText(sel);
            }
            return true;
        }

        const selectedText = window.getSelection()?.toString();
        if (selectedText && selectedText.trim()) {
            android.copyText(selectedText);
            return true;
        }
        return false;
    }

    keyboard.setupKeyboardListener((e: KeyboardEvent) => {
        if (e.ctrlKey && e.code === "KeyC") {
            return handleCopyText();
        }
        return false;
    }, 2);
}
