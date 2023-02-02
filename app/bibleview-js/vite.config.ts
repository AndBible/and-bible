/*
 * Copyright (c) 2022 Martin Denham, Tuomas Airaksinen and the AndBible contributors.
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

import { fileURLToPath, URL } from 'node:url'
import {defineConfig, UserConfig} from 'vite'
import vue from '@vitejs/plugin-vue'
import { load } from "js-yaml";
import toSource from "tosource";
import {resolve} from "path";

// https://vitejs.dev/config/

const fileRegex = /\.ya?ml$/;
export function yaml() { // copied from https://github.com/mzaini30/vite-plugin-yaml2
    return {
        name: "yaml-to-js",
        transform(src: string, id: string) {
            if (fileRegex.test(id)) {
                const transformedCode = `const data = ${toSource(load(src))}\n`;
                const result = transformedCode + "export default data";

                return {
                    code: result,
                    map: null, // provide source map if available
                };
            }
        },
    }
}

const sourcemap = process.env.NODE_ENV !== "production" ? "inline": false;
console.log("NODE_ENV", {NODE_ENV: process.env.NODE_ENV, sourcemap});

export const config: UserConfig = {
    base: '',
    build: {
        sourcemap,
        rollupOptions: {
            input: {
                main: resolve(__dirname, "index.html")
            }
        },
        commonjsOptions: {
            //
            //exclude: ["node_modules/bible-passage-reference-parser/js/en_bcv_parser.min.js"],
            //exclude: ["bible-passage-reference-parser"],
            //include: [
            //    "node_modules/color/index.js"
            //],
        }
    },
    plugins: [vue(), yaml()],
    resolve: {
        alias: {
            '@': fileURLToPath(new URL('./src', import.meta.url)),
            '~@': fileURLToPath(new URL('./src', import.meta.url)),
            "vue": "vue/dist/vue.esm-bundler.js",
        }
    },
}

export default defineConfig(config)
