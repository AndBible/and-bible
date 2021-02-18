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
    UPDATE_LABELS: "update_labels",
    REPLACE_DOCUMENT: "replace_document",
    SET_CONFIG: "set_config",
    SET_TITLE: "set_title",
    SETUP_CONTENT: "setup_content",
    SCROLL_TO_VERSE: "scroll_to_verse",
    ADD_OR_UPDATE_BOOKMARKS: "add_or_update_bookmarks",
    DELETE_BOOKMARKS: "delete_bookmarks",
    REMOVE_RANGES: "remove_ranges",
    SET_OFFSETS: "set_offsets",
    BOOKMARK_FLAG_CLICKED: "bookmark_flag_clicked",
    BOOKMARK_HIGHLIGHT_CLICKED: "bookmark_highlight_clicked",
    CLOSE_MODALS: "close_modals",
    ADD_OR_UPDATE_JOURNAL: "add_or_update_journal",
    ADD_OR_UPDATE_BOOKMARK_TO_LABEL: "add_or_update_bookmark_to_label",
    DELETE_JOURNAL: "delete_journal",
    CONFIG_CHANGED: "config_changed",
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
