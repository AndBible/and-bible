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

import {nextTick, provide, ref} from "@vue/runtime-core";
import {Events, setupEventBusListener} from "@/eventbus";

export function useSharing({topElement, android}) {
    const exportMode = ref(false);
    provide("exportMode", exportMode);

    let exportCss;
    async function shareDocument() {
        if (!exportCss) {
            exportCss = (await import("!raw-loader!sass-loader!@/export.scss")).default;
        }
        exportMode.value = true;
        await nextTick();
        const html = `
         <!DOCTYPE html>
         <html>
           <head>
             <meta charset="utf-8">
             <style>${exportCss}</style>
           </head>
           <body>${topElement.value.innerHTML}</body>
         </html>`;
        exportMode.value = false;
        android.shareHtml(html);
    }
    setupEventBusListener(Events.EXPORT_HTML, shareDocument)
}
