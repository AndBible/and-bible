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

import mitt, {Emitter} from "mitt";
import {onMounted, onUnmounted} from "vue";

// TODO: can use literals directly with typescript
export const Events: Record<string, keyof EventTypes> = {
    UPDATE_LABELS: "update_labels",
    CLEAR_DOCUMENT: "clear_document",
    ADD_DOCUMENTS: "add_documents",
    SET_CONFIG: "set_config",
    SET_ACTION_MODE: "set_action_mode",
    SET_ACTIVE: "set_active",
    SET_TITLE: "set_title",
    SETUP_CONTENT: "setup_content",
    SCROLL_TO_VERSE: "scroll_to_verse",
    ADD_OR_UPDATE_BOOKMARKS: "add_or_update_bookmarks",
    DELETE_BOOKMARKS: "delete_bookmarks",
    REMOVE_RANGES: "remove_ranges",
    SET_OFFSETS: "set_offsets",
    BOOKMARK_CLICKED: "bookmark_clicked",
    CLOSE_MODALS: "close_modals",
    WINDOW_CLICKED: "back_clicked",
    ADD_OR_UPDATE_JOURNAL: "add_or_update_journal",
    ADD_OR_UPDATE_BOOKMARK_TO_LABEL: "add_or_update_bookmark_to_label",
    DELETE_JOURNAL: "delete_journal",
    CONFIG_CHANGED: "config_changed",
    RELOAD_ADDONS: "reload_addons",
    BOOKMARK_NOTE_MODIFIED: "bookmark_note_modified",
    SCROLL_UP: "scroll_up",
    SCROLL_DOWN: "scroll_down",
    ADJUST_LOADING_COUNT: "adjust_loading_count",
    EXPORT_HTML: "export_html",
}

type EventCallbackParams = any[]

type EventTypes = {
    update_labels: EventCallbackParams
    clear_document: EventCallbackParams
    add_documents: EventCallbackParams
    set_config: EventCallbackParams
    set_action_mode: EventCallbackParams
    set_active: EventCallbackParams
    set_title: EventCallbackParams
    setup_content: EventCallbackParams
    scroll_to_verse: EventCallbackParams
    add_or_update_bookmarks: EventCallbackParams
    delete_bookmarks: EventCallbackParams
    remove_ranges: EventCallbackParams
    set_offsets: EventCallbackParams
    bookmark_clicked: EventCallbackParams
    close_modals: EventCallbackParams
    back_clicked: EventCallbackParams
    add_or_update_journal: EventCallbackParams
    add_or_update_bookmark_to_label: EventCallbackParams
    delete_journal: EventCallbackParams
    config_changed: EventCallbackParams
    reload_addons: EventCallbackParams
    bookmark_note_modified: EventCallbackParams
    scroll_up: EventCallbackParams
    scroll_down: EventCallbackParams
    adjust_loading_count: EventCallbackParams
    export_html: EventCallbackParams
}

export const eventBus: Emitter<EventTypes> = mitt()

export function emit(eventId: keyof EventTypes, ...args: any[]){
    console.log(`Emitting ${eventId}`);
    eventBus.emit(eventId, args)
}

export function setupEventBusListener(eventId: keyof EventTypes, callback: (...args: any[]) => void) {
    function eventCallback(args: any[]) {
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
