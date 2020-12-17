/*
 * Copyright (c) 2020 Martin Denham, Tuomas Airaksinen and the And Bible contributors.
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

import mitt from "mitt";
import {onMounted, onUnmounted} from "@vue/runtime-core";

export const eventBus = mitt()

export function emit(eventId, ...args){
    eventBus.emit(eventId, args)
}

export const Events = {
    REPLACE_OSIS: "replace_osis",
    SET_CONFIG: "set_config",
    SET_TITLE: "set_title",
    SETUP_CONTENT: "setup_content",
    SCROLL_TO_VERSE: "scroll_to_verse",
    MAKE_BOOKMARK: "make_bookmark",
    SET_TOOLBAR_OFFSET: "set_toolbar_offset",
}

export function setupEventBusListener(eventId, callback) {
    function eventCallback(args) {
        console.log("Calling eventbus listener for", eventId, ...args);
        callback(...args);
    }
    onMounted(() => eventBus.on(eventId, eventCallback))
    onUnmounted(() => eventBus.off(eventId, eventCallback))
}

eventBus.on("*", (type, args) => {
    if(!Object.values(Events).includes(type)) {
        console.error("Eventbus event type not supported", type, args)
    }
})
