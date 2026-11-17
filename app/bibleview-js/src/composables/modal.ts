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

import {computed, onUnmounted, reactive, Ref, watch} from "vue";
import {setupEventBusListener} from "@/eventbus";
import {useAndroid} from "@/composables/android";

export type ModalOpts = {
    blocking: boolean
    close: () => void
}

export function useModal(android: ReturnType<typeof useAndroid>) {
    const modalOptArray: ModalOpts[] = reactive([]);
    const modalOpen = computed(() => modalOptArray.length > 0);

    function register(opts: ModalOpts) {
        if (!opts.blocking) {
            closeModals();
        }

        modalOptArray.push(opts);

        onUnmounted(() => {
            const idx = modalOptArray.indexOf(opts);
            modalOptArray.splice(idx, 1);
        });
    }

    function closeModals() {
        for (const {close} of modalOptArray.filter(o => !o.blocking))
            close();
    }

    // Popups inside a document (e.g. Memorize's overflow menu and settings gear) that Back closes (#4118). They are kept
    // apart from the modals: a tap in the document closes the modals (AmbiguousSelection), but a tap inside such a
    // popup must not close it; each popup closes itself on a tap outside.
    const popupCloseArray: (() => void)[] = reactive([]);

    function removePopup(close: () => void) {
        const idx = popupCloseArray.indexOf(close);
        if (idx >= 0) popupCloseArray.splice(idx, 1);
    }

    /**
     * Registers a popup whose visibility is [open]. While it is open the page reports a modal to native, so Back
     * (and desktop Escape) closes the popup through close_modals instead of navigating away.
     */
    function registerPopup(open: Ref<boolean>) {
        const close = () => { open.value = false };
        watch(open, isOpen => isOpen ? popupCloseArray.push(close) : removePopup(close), {flush: "sync"});
        onUnmounted(() => removePopup(close));
    }

    function closePopups() {
        for (const close of [...popupCloseArray]) close();
    }

    setupEventBusListener("close_modals", () => {
        closeModals();
        closePopups();
    })

    watch(() => modalOpen.value || popupCloseArray.length > 0, v => android.reportModalState(v), {flush: "sync"})

    return {register, registerPopup, closeModals, modalOpen}
}
