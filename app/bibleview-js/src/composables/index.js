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

import {
    getCurrentInstance,
    inject,
    reactive,
} from "vue";
import {abbreviated, sprintf, adjustedColor} from "@/utils";
import {emit, Events} from "@/eventbus";

export function useCommon() {
    const currentInstance = getCurrentInstance();

    const config = inject("config");
    const appSettings = inject("appSettings");
    const calculatedConfig = inject("calculatedConfig");
    const android = inject("android");

    const strings = inject("strings");

    const unusedAttrs = Object.keys(currentInstance.attrs).filter(v => !v.startsWith("__") && v !== "onClose");
    if(unusedAttrs.length > 0) {
        console.error("Unhandled attributes", currentInstance.type.name, currentInstance.attrs);
    }

    function split(string, separator, n) {
        return string.split(separator)[n]
    }

    function formatTimestamp(timestamp) {
        let options = { year:'numeric', month:'numeric', day: 'numeric', hour:'numeric', minute:'2-digit'};
        return new Date(timestamp).toLocaleString([],options)
    }

    return {config, appSettings, calculatedConfig, strings, sprintf, split,
        adjustedColor, formatTimestamp, abbreviated, emit, Events, android,
    }
}

export function checkUnsupportedProps(props, attributeName, values = []) {
    const value = props[attributeName];
    const appSettings = inject("appSettings", {});
    if(!appSettings.errorBox) return;
    if(value && !values.includes(value)) {
        const tagName = getCurrentInstance().type.name
        const origin = inject("verseInfo", {}).osisID;
        console.warn(`${tagName}: Unsupported (ignored) attribute "${attributeName}" value "${value}", origin: ${origin}`)
    }
}

export function useReferenceCollector() {
    const references = reactive([]);
    function collect(linkRef) {
        references.push(linkRef);
    }
    function clear() {
        references.splice(0);
    }
    return {references, collect, clear}
}
