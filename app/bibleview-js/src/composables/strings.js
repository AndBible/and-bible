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

import {reactive} from "@vue/runtime-core";

const untranslated = {
    chapterNum: "— %d —",
    verseNum: "%d",
    multiDocumentLink: "%s (%s)"
}

let strings = reactive({...require(`@/lang/default.yaml`), ...untranslated});
let stringsLoaded = false;

async function loadStrings() {
    const lang = new URLSearchParams(window.location.search).get("lang");
    if(lang === "en-US") return;
    console.log(`Loading lang ${lang}`)
    let translations;
    try {
        translations = await import(`@/lang/${lang}.yaml`);
    } catch (e) {
        console.error(`Language ${lang} not found, falling back to English!`)
        return;
    }
    for(const i in translations) {
        if(translations[i]) {
            strings[i] = translations[i]
        }
    }
}

export function useStrings() {
    if(!stringsLoaded) {
        loadStrings();
        stringsLoaded = true;
    }
    return strings;
}
