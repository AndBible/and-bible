/*
 * Copyright (c) 2021-2022 Martin Denham, Tuomas Airaksinen and the AndBible contributors.
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

import {setupDocumentEventListener} from "@/utils";
import {UseAndroid} from "@/composables/android";
import {useScroll} from "@/composables/scroll";
import {ComputedRef} from "vue";
import {useModal} from "@/composables/modal";

const altKeys: Set<string> = new Set(["ArrowUp", "ArrowDown", "ArrowLeft", "ArrowRight", "KeyW", "KeyM", "KeyO", "KeyG"]);
const keys: Set<string> = new Set(["ArrowUp", "ArrowDown"]);
const handleJsSide: Set<string> = new Set(["ArrowUp", "ArrowDown"]);

export function useKeyboard(
    {onKeyDown}: UseAndroid,
    {doScrolling}: ReturnType<typeof useScroll>,
    {modalOpen}: ReturnType<typeof useModal>,
    lineHeight: ComputedRef<number>
) {
    setupDocumentEventListener("keydown", (e: KeyboardEvent) => {
        if (keys.has(e.code) || (e.altKey && altKeys.has(e.code))) {
            let key = e.code;
            if (e.altKey) {
                key = "Alt" + key;
            }
            if(handleJsSide.has(key)) {
                if(modalOpen.value) {
                    return
                }
                else if(key === "ArrowDown") {
                    doScrolling(window.scrollY + lineHeight.value, 50);
                } else if(key === "ArrowUp") {
                    doScrolling(window.scrollY - lineHeight.value, 50);
                }
            } else {
                onKeyDown(key);
            }
            e.preventDefault();
            e.stopPropagation();
        }
    });
}
