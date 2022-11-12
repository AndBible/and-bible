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
import {bcv_parser as BcvParser} from "bible-passage-reference-parser/js/en_bcv_parser.min";

export function useParsers(android) {
    const enParser = new BcvParser;
    enParser.include_apocrypha(true);
    const parsers = [enParser];

    let languages = null;

    function getLanguages() {
        if (!languages) {
            languages = android.getActiveLanguages()
        }
        return languages
    }

    async function loadParser(lang) {
        console.log(`Loading parser for ${lang}`)
        const url = `/features/RefParser/${lang}_bcv_parser.js`
        const content = await (await fetch(url)).text();
        const module = {}
        Function(content).call(module)
        const parser = new module["bcv_parser"];
        parser.include_apocrypha(true);
        return parser;
    }

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

    function parse(text) {
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
