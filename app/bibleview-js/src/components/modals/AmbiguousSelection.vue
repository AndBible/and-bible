<!--
  - Copyright (c) 2021-2022 Martin Denham, Tuomas Airaksinen and the AndBible contributors.
  -
  - This file is part of AndBible: Bible Study (http://github.com/AndBible/and-bible).
  -
  - AndBible is free software: you can redistribute it and/or modify it under the
  - terms of the GNU General Public License as published by the Free Software Foundation,
  - either version 3 of the License, or (at your option) any later version.
  -
  - AndBible is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
  - without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
  - See the GNU General Public License for more details.
  -
  - You should have received a copy of the GNU General Public License along with AndBible.
  - If not, see http://www.gnu.org/licenses/.
  -->

<template>
  <ModalDialog ref="modal" :blocking="blocking" v-if="showModal" :locate-top="locateTop" @close="cancelled" :limit="limitAmbiguousModalSize">
    <template #extra-buttons>
      <button class="modal-action-button right" @touchstart.stop @click="multiSelectionButtonClicked">
        <FontAwesomeIcon icon="plus-circle"/>
      </button>
      <button v-if="modal && (limitAmbiguousModalSize || modal.height > 196)" class="modal-action-button right" @touchstart.stop @click="limitAmbiguousModalSize = !limitAmbiguousModalSize">
        <FontAwesomeIcon :icon="limitAmbiguousModalSize?'expand-arrows-alt':'compress-arrows-alt'"/>
      </button>
      <button class="modal-action-button right" @touchstart.stop @click="help">
        <FontAwesomeIcon icon="question-circle"/>
      </button>
    </template>

    <div class="buttons">
      <AmbiguousActionButtons v-if="selectionInfo" :has-actions="!noActions" :selection-info="selectionInfo" @close="cancelled"/>
      <template v-for="(s, index) of selectedActions" :key="index">
        <template v-if="!s.options.bookmarkId">
          <button class="button light" @click.stop="selected(s)">
            <span :style="`color: ${s.options.color}`"><FontAwesomeIcon v-if="s.options.icon" :icon="s.options.icon"/></span>
            {{s.options.title}}
          </button>
        </template>
      </template>
      <AmbiguousSelectionBookmarkButton
        v-for="b of clickedBookmarks"
        :key="`b-${b.id}`"
        :bookmark-id="b.id"
        @selected="selected(b)"
      />
      <div v-if="clickedBookmarks.length > 0 && selectedBookmarks.length > 0" class="separator"/>
      <AmbiguousSelectionBookmarkButton
        v-for="b of selectedBookmarks"
        :key="`b-${b.id}`"
        :bookmark-id="b.id"
        @selected="selected(b)"
      />
    </div>
    <template #title>
      <template v-if="verseInfo">
        {{ bibleBookName }} {{ verseInfo.chapter}}:{{verseInfo.verse}}<template v-if="verseInfo.verseTo">-{{verseInfo.verseTo}}</template>
      </template>
      <template v-else>
        {{ strings.ambiguousSelection }}
      </template>
    </template>
  </ModalDialog>
</template>

<script lang="ts" setup>
import ModalDialog from "@/components/modals/ModalDialog.vue";
import {useCommon} from "@/composables";
import {FontAwesomeIcon} from "@fortawesome/vue-fontawesome";
import {provide, inject, ref, computed, Ref} from "vue";
import {
    Deferred,
    getHighestPriorityEventFunctions,
    getEventVerseInfo,
    getAllEventFunctions,
    isBottomHalfClicked,
    EventVerseInfo,
    Callback,
} from "@/utils";
import AmbiguousSelectionBookmarkButton from "@/components/modals/AmbiguousSelectionBookmarkButton.vue";
import {emit, Events} from "@/eventbus";
import AmbiguousActionButtons from "@/components/AmbiguousActionButtons.vue";
import {sortBy} from "lodash";
import {
    androidKey,
    appSettingsKey,
    globalBookmarksKey,
    locateTopKey,
    modalKey,
    verseHighlightKey
} from "@/types/constants";
import {Bookmark} from "@/types/client-objects";
import {Nullable, Optional, SelectionInfo} from "@/types/common";

const props = withDefaults(
    defineProps<{blocking: boolean, doNotCloseModals: boolean}>(),
    {blocking: false, doNotCloseModals: false}
);

const $emit = defineEmits(["back-clicked"])

const appSettings = inject(appSettingsKey)!;
const limitAmbiguousModalSize = computed({
    get() {
        return appSettings.limitAmbiguousModalSize;
    },
    set(value) {
        android.setLimitAmbiguousModalSize(value);
    }
});
const {bookmarkMap, bookmarkIdsByOrdinal} = inject(globalBookmarksKey)!;
const {strings} = useCommon();
const android = inject(androidKey)!;
const multiSelectionMode = ref(false);

const {resetHighlights, highlightVerse, hasHighlights} = inject(verseHighlightKey)!;
const {modalOpen, closeModals} = inject(modalKey)!;

const showModal = ref(false);
const locateTop = ref(false);
provide(locateTopKey, locateTop);

const verseInfo: Ref<Nullable<EventVerseInfo>> = ref(null);

const selectionInfo = computed<Nullable<SelectionInfo>>(() => {
    if(!verseInfo.value) return null;
    return {
        ...verseInfo.value,
        startOrdinal: startOrdinal.value!,
        endOrdinal: endOrdinal.value!,
    }
});

const originalSelections = ref<Callback[]|null>(null);
const bibleBookName = computed(() => verseInfo.value && verseInfo.value.bibleBookName);

const selectedActions = computed<Callback[]>(() => {
    if (originalSelections.value === null) return [];
    return originalSelections.value.filter(v => !v.options.bookmarkId)
});

const clickedBookmarks = computed<Bookmark[]>(() => {
    if (originalSelections.value === null) return [];

    return sortBy(
        originalSelections.value
            .filter(v => v.options.bookmarkId && !v.options.hidden && bookmarkMap.has(v.options.bookmarkId))
            .map(v => bookmarkMap.get(v.options.bookmarkId)!),
        v => v.text.length
    );
});

let deferred: Nullable<Deferred<Bookmark|Callback|undefined>> = null;

async function select(event: MouseEvent, sel: Callback[]): Promise<Callback|Bookmark|undefined> {
    originalSelections.value = sel;
    locateTop.value = isBottomHalfClicked(event);
    showModal.value = true;

    deferred = new Deferred();
    return await deferred.wait();
}

function selected(s: Callback|Bookmark) {
    deferred!.resolve(s);
}

function cancelled() {
    if (deferred) {
        deferred.resolve();
    }
}

function close() {
    multiSelectionMode.value = false;
    showModal.value = false;
    resetHighlights(true);
}

//const {isDoubleClick} = createDoubleClickDetector();

function updateHighlight() {
    resetHighlights();
    for(let o of ordinalRange()) {
        highlightVerse(o);
    }
    if(!verseInfo.value) return;
    if (endOrdinal.value == null || endOrdinal.value === startOrdinal.value){
        verseInfo.value.verseTo = "";
    } else {
        const {ordinalRange: [, chapterEnd]} = verseInfo.value.bibleDocumentInfo!;

        const endOrd = chapterEnd > endOrdinal.value ? endOrdinal.value: chapterEnd;
        verseInfo.value.verseTo = `${verseInfo.value.verse + endOrd - startOrdinal.value!}${endOrdinal.value > chapterEnd ? "+" : ""}`;
    }
}

function multiSelect(_verseInfo: Optional<EventVerseInfo>) {
    if(!_verseInfo) return false;
    if(_verseInfo.ordinal < startOrdinal.value!) {
        endOrdinal.value = null;
        return false
    } else {
        endOrdinal.value = _verseInfo.ordinal;
    }
    updateHighlight();
    return true;
}

const startOrdinal = ref<number|null>(null);
const endOrdinal = ref<number|null>(null);

function* ordinalRange(): Generator<number> {
    const _endOrdinal = endOrdinal.value || startOrdinal.value;
    for(let o = startOrdinal.value!; o<=_endOrdinal!; o++) {
        yield o;
    }
}

const selectedBookmarks = computed<Bookmark[]>(() => {
    const clickedIds = new Set(clickedBookmarks.value.map(b => b.id));
    const result: number[] = [];
    for(const o of ordinalRange()) {
        result.push(
            ...Array.from(bookmarkIdsByOrdinal.get(o) || [])
                .filter(bId => !clickedIds.has(bId) && !result.includes(bId)))
    }
    return result.map(bId => bookmarkMap.get(bId)).filter(b => b) as Bookmark[];
});

function setInitialVerse(_verseInfo: EventVerseInfo) {
    verseInfo.value = _verseInfo;
    startOrdinal.value = _verseInfo.ordinal;
    endOrdinal.value = null;
    updateHighlight();
}

function multiSelectionButtonClicked() {
    if(multiSelectionMode.value) {
        endOrdinal.value! += 1;
    } else {
        multiSelectionMode.value = true;
        endOrdinal.value = startOrdinal.value! + 1;
    }

    updateHighlight();
}

async function handle(event: MouseEvent) {
    console.log("AmbiguousSelection handling", event);
    const isActive = appSettings.activeWindow && (performance.now() - appSettings.activeSince > 250);
    const eventFunctions = getHighestPriorityEventFunctions(event);
    const allEventFunctions = getAllEventFunctions(event);
    const hasParticularClicks = eventFunctions.filter(f => !f.options.hidden).length > 0; // let's not show only "hidden" items
    if(appSettings.actionMode) return;
    const hadHighlights = hasHighlights.value;
    resetHighlights();
    if(hadHighlights && !showModal.value && !hasParticularClicks) {
        return;
    }
    if(!isActive && !hasParticularClicks) return;
    emit(Events.WINDOW_CLICKED);
    const _verseInfo: Nullable<EventVerseInfo> = getEventVerseInfo(event);
    if (multiSelectionMode.value && multiSelect(_verseInfo)) {
        return;
    }
    multiSelectionMode.value = false;

    if(eventFunctions.length > 0 || _verseInfo != null) {
        const firstFunc = eventFunctions[0];
        if(
            (eventFunctions.length === 1 && firstFunc.options.priority > 0 && !firstFunc.options.dottedStrongs)
            || (allEventFunctions.length === 1 && firstFunc.options.dottedStrongs)
        ) {
            if (eventFunctions[0].options.bookmarkId) {
                emit(Events.BOOKMARK_CLICKED, eventFunctions[0].options.bookmarkId, {locateTop: isBottomHalfClicked(event)});
            } else {
                const cb = eventFunctions[0].callback;
                if(cb) {
                    cb();
                }
            }
        }
        else {
            if (modalOpen.value && !hasParticularClicks) {
                if(!props.doNotCloseModals) {
                    closeModals();
                }
            } else if(_verseInfo) {
                setInitialVerse(_verseInfo);
                const s = await select(event, allEventFunctions);
                if (s && s.type === "callback" && s.callback) s.callback();
            } else {
                console.error("")
            }
        }
    } else {
        $emit("back-clicked");
        if(!props.doNotCloseModals) {
            closeModals();
        }
    }
    close();
}

const noActions = computed(() => selectedActions.value.length === 0);

function help() {
    android.helpBookmarks()
}

const modal = ref<InstanceType<typeof ModalDialog>|null>(null);
defineExpose({handle});
</script>

<style scoped lang="scss">
@import "~@/common.scss";
.buttons {
  @extend .visible-scrollbar;
  max-height: calc(var(--max-height) - 25pt);
  display: flex;
  flex-direction: column;
  overflow-y: auto;
}

.separator {
  margin-top: 2pt;
  margin-bottom: 2pt;
}

</style>
