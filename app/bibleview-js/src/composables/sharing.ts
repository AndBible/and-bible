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

import {nextTick, provide, Ref, ref} from "vue";
import {setupEventBusListener} from "@/eventbus";
import {exportModeKey} from "@/types/constants";
import {useAndroid} from "@/composables/android";

export function useSharing(
    {topElement, android}: { topElement: Ref<Element | null>, android: ReturnType<typeof useAndroid> }) {
    const exportMode = ref(false);
    provide(exportModeKey, exportMode);

    let exportCss: string;

    async function shareDocument() {
        if (!exportCss) {
            exportCss = (await import("@/export.scss")); // TODO: check if this works!
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
           <body>${topElement.value!.innerHTML}</body>
         </html>`;
        exportMode.value = false;
        android.shareHtml(html);
    }

    setupEventBusListener("export_html", shareDocument)
}
