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

const altKeys: Set<string> = new Set(["ArrowUp", "ArrowDown", "ArrowLeft", "ArrowRight", "KeyW", "KeyM", "KeyO", "KeyG"]);
const keys: Set<string> = new Set([]);

export function useKeyboard({onKeyDown}: UseAndroid) {
    setupDocumentEventListener("keydown", (e: KeyboardEvent) => {
        if (keys.has(e.code) || (e.altKey && altKeys.has(e.code))) {
            let key = e.code;
            if (e.altKey) {
                key = "Alt" + key;
            }
            onKeyDown(key);
            e.preventDefault();
            e.stopPropagation();
        }
    });
}
