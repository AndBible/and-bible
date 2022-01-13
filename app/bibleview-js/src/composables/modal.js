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

import {onUnmounted, reactive, watch} from "@vue/runtime-core";
import {computed} from "@vue/reactivity";
import {Events, setupEventBusListener} from "@/eventbus";

export function useModal(android) {
    const modalOptArray = reactive([]);
    const modalOpen = computed(() => modalOptArray.length > 0);

    function register(opts) {
        if(!opts.blocking) {
            closeModals();
        }

        modalOptArray.push(opts);

        onUnmounted(() => {
            const idx = modalOptArray.indexOf(opts);
            modalOptArray.splice(idx, 1);
        });
    }

    function closeModals() {
        for(const {close} of modalOptArray.filter(o => !o.blocking))
            close();
    }

    setupEventBusListener(Events.CLOSE_MODALS, closeModals)

    watch(modalOpen, v => android.reportModalState(v), {flush: "sync"})

    return {register, closeModals, modalOpen}
}
