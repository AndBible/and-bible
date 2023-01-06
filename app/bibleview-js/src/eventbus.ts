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

type EventTypeNames = "update_labels"|"clear_document"|"add_documents"|"set_config"|"set_action_mode"|"set_active"
    |"set_title"|"setup_content"|"scroll_to_verse"|"add_or_update_bookmarks"|"delete_bookmarks"|"remove_ranges"
    |"set_offsets"|"bookmark_clicked"|"close_modals"|"back_clicked"|"add_or_update_journal"
    |"add_or_update_bookmark_to_label"|"delete_journal"|"config_changed"|"reload_addons"
    |"bookmark_note_modified"|"scroll_up"|"scroll_down"|"adjust_loading_count"|"export_html"

export const eventBus: Emitter<Record<EventTypeNames, any[]>> = mitt()

export function emit(eventId: EventTypeNames, ...args: any[]){
    console.log(`Emitting ${eventId}`);
    eventBus.emit(eventId, args)
}

export function setupEventBusListener(eventId: EventTypeNames, callback: (...args: any[]) => void) {
    function eventCallback(args: any[]) {
        console.log("Calling eventbus listener for", eventId, ...args);
        callback(...args);
    }
    onMounted(() => eventBus.on(eventId, eventCallback))
    onUnmounted(() => eventBus.off(eventId, eventCallback))
}