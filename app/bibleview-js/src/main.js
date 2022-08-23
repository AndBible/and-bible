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

console.log("main.js begin");

// We will inject here callbacks / stuff that is manipulated by Android Javascript interface
import {patchAndroidConsole} from "@/composables/android";

window.bibleView = {};
window.bibleViewDebug = {};

patchAndroidConsole();

console.log("main.js after patching console");

import { createApp } from 'vue'

import BibleView from "@/components/BibleView";
import AmbiguousSelection from "@/components/modals/AmbiguousSelection";
import LabelList from "@/components/LabelList";
import BookmarkLabelActions from "@/components/modals/BookmarkLabelActions";

console.log("main.js After imports");

const app = createApp(BibleView);
app.component("AmbiguousSelection", AmbiguousSelection);
app.component("LabelList", LabelList);
app.component("BookmarkLabelActions", BookmarkLabelActions);
console.log("main.js After vue bootstrap. Mounting.");
app.mount('#app')
console.log("main.js After vue mount.");

