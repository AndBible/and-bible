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

import {useAndroid} from "@/composables/android";

export function useParsers(android: ReturnType<typeof useAndroid>) {
    const parsers: BcvParserType[] = [];

    let languages: string[] | null = null;

    function getLanguages() {
        if (!languages) {
            languages = android.getActiveLanguages()
        }
        return languages
    }

    async function loadParser(lang: string) {
        console.log(`Loading parser for ${lang}`)
        const url = `/features/RefParser/${lang}_bcv_parser.js`
        const content = await (await fetch(url)).text();
        const module: BcvModule = {}
        Function(content).call(module)
        const parser: BcvParserType = new module.bcv_parser!;
        parser.include_apocrypha(true);
        return parser;
    }

    async function initializeEnglish() {
        const {bcv_parser: BcvParser} = await import("@/lib/en_bcv_parser");
        const enParser: BcvParserType = new BcvParser;
        enParser.include_apocrypha(true);
        parsers.push(enParser)
    }

    initializeEnglish()

    async function initialize() {
        //Get the active languages and create a bible reference parser for each language
        const languages = getLanguages()
        console.log(`Enabling parsers for ${languages.join(",")}`)
        await Promise.all(languages.filter(l => l !== "en").map(async (lang) => {
            try {
                parsers.push(await loadParser(lang))
            } catch (error) {
                console.log(`Could not load parser for language: ${lang} due to ${error}`)
            }
        }))
    }

    function parse(text: string) {
        let parsed = ""
        //Try each of the parsers until one succeeds
        parsers.some(parser => {
            parsed = parser.parse(text).osis();
            if (parsed !== "") return true
        })
        return parsed;
    }

    return {initialize, parsers, parse}
}
