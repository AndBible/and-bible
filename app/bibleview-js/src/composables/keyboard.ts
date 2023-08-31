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
import {computed, ComputedRef, ref, watch} from "vue";

const altKeys: Set<string> = new Set(["ArrowUp", "ArrowDown", "ArrowLeft", "ArrowRight", "KeyW", "KeyM", "KeyO", "KeyG"]);
const ctrlKeys: Set<string> = new Set(["KeyC"]);
const keys: Set<string> = new Set(["ArrowUp", "ArrowDown"]);
const handleJsSide: Set<string> = new Set(["ArrowUp", "ArrowDown"]);

export function useKeyboard(
    {onKeyDown, setEditing}: UseAndroid,
    {doScrolling}: ReturnType<typeof useScroll>,
    lineHeight: ComputedRef<number>
) {
    const editorMode = ref(0);
    const isEditing = computed(() => editorMode.value > 0);

    watch(isEditing, value => {
        setEditing(value);
    })

    setupDocumentEventListener("keydown", (e: KeyboardEvent) => {
        if (keys.has(e.code) || (e.altKey && altKeys.has(e.code)) || (e.ctrlKey && ctrlKeys.has(e.code))) {
            let key = e.code;
            if (e.altKey) {
                key = "Alt" + key;
            }
            if (e.ctrlKey) {
                key = "Ctrl" + key;
            }
            if(handleJsSide.has(key)) {
                if(isEditing.value) return
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

    return {editorMode};
}
