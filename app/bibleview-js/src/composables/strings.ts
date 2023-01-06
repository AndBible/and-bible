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

import {reactive} from "vue";
import defaultStrings from "@/lang/default.yaml";
import enStrings from "@/lang/en.yaml";

const untranslated = {
    chapterNum: "— %s —",
    verseNum: "%s",
    multiDocumentLink: "%s (%s)"
}

const strings: Strings = reactive({...defaultStrings, ...untranslated});

let k: keyof TranslatedStrings;
for (k in enStrings) {
    if (enStrings[k]) {
        strings[k] = enStrings[k]
    }
}

let stringsLoaded = false;

async function loadStrings() {
    const langTag = new URLSearchParams(window.location.search).get("lang") || "";
    if (langTag === "en-US") return;
    const langShort = langTag.split('-')[0]
    console.log(`Loading lang ${langTag}`)
    let translations: Partial<TranslatedStrings>;
    try {
        translations = (await import(`@/lang/${langTag}.yaml`)).default;
    } catch (e) {
        try {
            translations = (await import(`@/lang/${langShort}.yaml`)).default;
        } catch (e) {
            console.error(`Language ${langTag} not found, falling back to English!`)
            return;
        }
    }
    let k: keyof TranslatedStrings
    for (k in translations) {
        const t = translations[k]
        if (t) {
            strings[k] = t
        }
    }
}

export function useStrings() {
    if (!stringsLoaded) {
        loadStrings();
        stringsLoaded = true;
    }
    return strings;
}
