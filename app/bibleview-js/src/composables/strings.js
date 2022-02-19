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
import {testMode} from "@/composables/config";

const untranslated = {
    chapterNum: "— %s —",
    verseNum: "%s",
    multiDocumentLink: "%s (%s)"
}

let strings;

if(testMode) {
    strings = {}
} else {
    strings = reactive({...require(`@/lang/default.yaml`), ...untranslated});
    const enFixes = require(`@/lang/en.yaml`);
    for(const i in enFixes) {
        if(enFixes[i]) {
            strings[i] = enFixes[i]
        }
    }
}

let stringsLoaded = false;

async function loadStrings() {
    const langTag = new URLSearchParams(window.location.search).get("lang") || "";
    if(langTag === "en-US") return;
    const langShort = langTag.split('-')[0]
    console.log(`Loading lang ${langTag}`)
    let translations;
    try {
        translations = await import(`@/lang/${langTag}.yaml`);
    } catch (e) {
        try {
            translations = await import(`@/lang/${langShort}.yaml`);
        } catch(e) {
            console.error(`Language ${langTag} not found, falling back to English!`)
            return;
        }
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
