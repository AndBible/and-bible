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

import {Deferred} from "@/utils";
import {onUnmounted, reactive} from "vue";
import {setupEventBusListener} from "@/eventbus";

export function useCustomCss() {
    const cssNodes = new Map();
    const count = new Map();
    const customCssPromises: {bookInitials: string, promise: Promise<undefined>}[] = [];

    window.bibleViewDebug.customCssPromises = customCssPromises;

    function addCss(bookInitials: string) {
        console.log(`Adding style for ${bookInitials}`);
        const c = count.get(bookInitials) || 0;
        if (!c) {
            const link = document.createElement("link");
            const onLoadDefer = new Deferred();
            const promise = onLoadDefer.wait();
            customCssPromises.push({bookInitials, promise});
            link.href = `/module-style/${bookInitials}/style.css`;
            link.type = "text/css";
            link.rel = "stylesheet";
            const cssReady = () => {
                const idx = customCssPromises.findIndex(v => v.promise === promise);
                if (idx != -1) {
                    customCssPromises.splice(idx, 1);
                }
                onLoadDefer.resolve();
            }
            link.onload = cssReady;
            link.onerror = cssReady;
            cssNodes.set(bookInitials, link);
            document.getElementsByTagName("head")[0].appendChild(link);
        }
        count.set(bookInitials, c + 1);
    }

    function removeCss(bookInitials: string) {
        console.log(`Removing style for ${bookInitials}`)
        const c = count.get(bookInitials) || 0;
        if (c > 1) {
            count.set(bookInitials, c - 1);
        } else if (c === 1) {
            const idx = customCssPromises.findIndex(v => v.bookInitials === bookInitials);
            if(idx != -1) {
                customCssPromises.splice(idx, 1);
            }
            count.delete(bookInitials);
            cssNodes.get(bookInitials).remove();
            cssNodes.delete(bookInitials);
        }
    }

    function registerBook(bookInitials: string) {
        addCss(bookInitials);
        onUnmounted(() => {
            removeCss(bookInitials);
        });
    }

    const customStyles: string[] = reactive([]);

    function reloadStyles(styleModuleNames: string[]) {
        customStyles.forEach(moduleName => {
            removeCss(moduleName);
        });
        customStyles.splice(0);

        styleModuleNames.forEach(moduleName => {
            customStyles.push(moduleName);
            addCss(moduleName);
        });
    }

    const styleModuleNames = new URLSearchParams(window.location.search).get("styleModuleNames");
    if (styleModuleNames) {
        reloadStyles(styleModuleNames.split(","))
    }

    setupEventBusListener("reload_addons", ({styleModuleNames}: { styleModuleNames: string[] }) => {
        reloadStyles(styleModuleNames)
    })

    return {registerBook, customCssPromises}
}
