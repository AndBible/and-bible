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

import {setupDocumentEventListener} from "@/utils";
import {ref} from "@vue/reactivity";
import {watch} from "@vue/runtime-core";

const keys = new Set(["KeyW", "KeyM", "KeyO", "KeyG"]);
const focusKeys = new Set(["ArrowUp", "ArrowDown", "ArrowLeft", "ArrowRight", "Tab"]);

export function useKeyboard(
    {
        android: {onKeyDown},
        appSettings,
        verseHandler: {adjFocusedVerse},
        modal: {modalOpen},
    }) {
    const keyboardMode = ref(false);
    const editMode = ref(false);

    setupDocumentEventListener("keydown", e => {
        if(editMode.value) {
            return;
        }

        console.log("Keycode:", e.code);

        if (keys.has(e.code)) {
            let key = e.code;
            onKeyDown(key);
            e.preventDefault();
            e.stopPropagation();
            return;
        }

        if(!keyboardMode.value) {
            if(e.code === "Enter") {
                keyboardMode.value = true
            }
            else if(focusKeys.has(e.code)) {
                onKeyDown(e.code);
            }
            e.preventDefault();
            e.stopPropagation();
        }

        if(keyboardMode.value) {
            let handled = false;

            if(modalOpen.value) return;

            switch(e.code) {
                case "Escape":
                    keyboardMode.value = false
                    handled = true;
                    break;
                case "ArrowUp":
                    adjFocusedVerse(-1);
                    handled = true;
                    break;
                case "ArrowDown":
                    adjFocusedVerse(1);
                    handled = true;
                    break;
            }
            if (handled) {
                e.preventDefault();
                e.stopPropagation();
            }
        }
    });

    watch(() => appSettings.activeWindow, v => {
        if(!v) {
            keyboardMode.value = false;
        }
    });

    return {
        setEditMode: v => editMode.value = v,
        keyboardMode,
    }
}
