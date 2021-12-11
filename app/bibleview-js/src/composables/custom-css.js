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

import {Deferred} from "@/utils";
import {onUnmounted, reactive} from "@vue/runtime-core";
import {Events, setupEventBusListener} from "@/eventbus";

export function useCustomCss() {
    const cssNodes = new Map();
    const count = new Map();
    const customCssPromises = [];
    function addCss(bookInitials) {
        console.log(`Adding style for ${bookInitials}`);
        const c = count.get(bookInitials) || 0;
        if (!c) {
            const link = document.createElement("link");
            const onLoadDefer = new Deferred();
            const promise = onLoadDefer.wait();
            customCssPromises.push(promise);
            link.href = `/module-style/${bookInitials}/style.css`;
            link.type = "text/css";
            link.rel = "stylesheet";
            const cssReady = () => {
                onLoadDefer.resolve();
                customCssPromises.splice(customCssPromises.findIndex(v => v === promise), 1);
            }
            link.onload = cssReady;
            link.onerror = cssReady;
            cssNodes.set(bookInitials, link);
            document.getElementsByTagName("head")[0].appendChild(link);
        }
        count.set(bookInitials, c + 1);
    }

    function removeCss(bookInitials) {
        console.log(`Removing style for ${bookInitials}`)
        const c = count.get(bookInitials) || 0;
        if(c > 1) {
            count.set(bookInitials, c-1);
        } else if(c === 1){
            count.delete(bookInitials);
            cssNodes.get(bookInitials).remove();
            cssNodes.delete(bookInitials);
        }
    }

    function registerBook(bookInitials) {
        addCss(bookInitials);
        onUnmounted(() => {
            removeCss(bookInitials);
        });
    }

    const customStyles = reactive([]);

    function reloadStyles(styleModuleNames) {
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

    setupEventBusListener(Events.RELOAD_ADDONS, ({styleModuleNames}) => {
        reloadStyles(styleModuleNames)
    })

    return {registerBook, customCssPromises}
}
