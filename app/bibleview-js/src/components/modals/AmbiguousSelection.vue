<!--
  - Copyright (c) 2021 Martin Denham, Tuomas Airaksinen and the And Bible contributors.
  -
  - This file is part of And Bible (http://github.com/AndBible/and-bible).
  -
  - And Bible is free software: you can redistribute it and/or modify it under the
  - terms of the GNU General Public License as published by the Free Software Foundation,
  - either version 3 of the License, or (at your option) any later version.
  -
  - And Bible is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
  - without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
  - See the GNU General Public License for more details.
  -
  - You should have received a copy of the GNU General Public License along with And Bible.
  - If not, see http://www.gnu.org/licenses/.
  -->

<template>
  <Modal ref="modal" :blocking="blocking" v-if="showModal" :locate-top="locateTop" @close="cancelled" :limit="limitAmbiguousModalSize">
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
  </Modal>
</template>

<script>
import Modal from "@/components/modals/Modal";
import {useCommon} from "@/composables";
import {FontAwesomeIcon} from "@fortawesome/vue-fontawesome";
import {provide, inject, ref} from "@vue/runtime-core";
import {
  Deferred,
  getHighestPriorityEventFunctions,
  getEventVerseInfo,
  getAllEventFunctions, isBottomHalfClicked,
  //createDoubleClickDetector
} from "@/utils";
import AmbiguousSelectionBookmarkButton from "@/components/modals/AmbiguousSelectionBookmarkButton";
import {emit, Events} from "@/eventbus";
import {computed} from "@vue/reactivity";
import AmbiguousActionButtons from "@/components/AmbiguousActionButtons";

export default {
  name: "AmbiguousSelection",
  props: {
    blocking: {type: Boolean, default: false}
  },
  emits: ["back-clicked"],
  components: {Modal, FontAwesomeIcon, AmbiguousSelectionBookmarkButton, AmbiguousActionButtons},
  setup(props, {emit: $emit}) {
    const appSettings = inject("appSettings");
    const limitAmbiguousModalSize = computed({
      get() {
        return appSettings.limitAmbiguousModalSize;
      },
      set(value) {
        android.setLimitAmbiguousModalSize(value);
      }
    });
    const {bookmarkMap, bookmarkIdsByOrdinal} = inject("globalBookmarks");
    const {strings, ...common} = useCommon();
    const android = inject("android");
    const multiSelectionMode = ref(false);

    const {resetHighlights, highlightVerse, hasHighlights} = inject("verseHighlight");
    const {modalOpen, closeModals} = inject("modal");

    const showModal = ref(false);
    const locateTop = ref(false);
    provide("locateTop", locateTop);
    const verseInfo = ref(null);

    const selectionInfo = computed(() => {
      if(!verseInfo.value) return null;
      return {
        ...verseInfo.value,
        startOrdinal: startOrdinal.value,
        endOrdinal: endOrdinal.value,
      }
    });

    const originalSelections = ref(null);
    const bibleBookName = computed(() => verseInfo.value && verseInfo.value.bibleBookName);

    const selectedActions = computed(() => {
      if (originalSelections.value === null) return [];
      return originalSelections.value.filter(v => !v.options.bookmarkId)
    });

    const clickedBookmarks = computed(() => {
      if (originalSelections.value === null) return [];

      return originalSelections.value
        .filter(v => v.options.bookmarkId && !v.options.hidden && bookmarkMap.has(v.options.bookmarkId))
        .map(v => bookmarkMap.get(v.options.bookmarkId));
    });

    let deferred = null;

    async function select(event, sel) {
      originalSelections.value = sel;
      locateTop.value = isBottomHalfClicked(event);
      showModal.value = true;

      deferred = new Deferred();
      return await deferred.wait();
    }

    function selected(s) {
      deferred.resolve(s);
    }

    function cancelled() {
      if (deferred) {
        deferred.resolve(null);
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
      if (endOrdinal.value == null || endOrdinal.value == startOrdinal.value){
        verseInfo.value.verseTo = "";
      } else {
        //TODO: Check if selection goes into next chapter and display accordingly.
        verseInfo.value.verseTo = verseInfo.value.verse + endOrdinal.value - startOrdinal.value;
      }
    }

    function multiSelect(_verseInfo) {
      if(!_verseInfo) return false;
      if(_verseInfo.ordinal < startOrdinal.value) {
        endOrdinal.value = null;
        return false
      } else {
        endOrdinal.value = _verseInfo.ordinal;
      }
      updateHighlight();
      return true;
    }

    const startOrdinal = ref(null);
    const endOrdinal = ref(null);

    function* ordinalRange() {
      const _endOrdinal = endOrdinal.value || startOrdinal.value;
      for(let o = startOrdinal.value; o<=_endOrdinal; o++) {
        yield o;
      }
    }

    const selectedBookmarks = computed(() => {
      const clickedIds = new Set(clickedBookmarks.value.map(b => b.id));
      const result = [];
      for(const o of ordinalRange()) {
        result.push(
          ...Array.from(bookmarkIdsByOrdinal.get(o) || [])
            .filter(bId => !clickedIds.has(bId) && !result.includes(bId)))
      }
      return result.map(bId => bookmarkMap.get(bId)).filter(b => b);
    });

    function setInitialVerse(_verseInfo) {
      verseInfo.value = _verseInfo;
      startOrdinal.value = _verseInfo.ordinal;
      endOrdinal.value = null;
      updateHighlight();
    }

    function multiSelectionButtonClicked() {
      if(multiSelectionMode.value) {
        endOrdinal.value += 1;
      } else {
        multiSelectionMode.value = true;
        endOrdinal.value = startOrdinal.value + 1;
      }

      updateHighlight();
    }

    async function handle(event) {
      //if(await isDoubleClick()) return;

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
      const _verseInfo = getEventVerseInfo(event);
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
            eventFunctions[0].callback();
          }
        }
        else {
          if (modalOpen.value && !hasParticularClicks) {
            closeModals();
          } else {
            setInitialVerse(_verseInfo);
            const s = await select(event, allEventFunctions);
            if (s && s.callback) s.callback();
          }
        }
      } else {
        $emit("back-clicked");
        closeModals();
      }
      close();
    }

    const noActions = computed(() => selectedActions.value.length === 0);

    function help() {
      android.helpBookmarks()
    }

    return {
      help, selectionInfo, locateTop, limitAmbiguousModalSize,
      bibleBookName, verseInfo, selected, handle, cancelled, noActions,
      showModal, selectedActions, selectedBookmarks, clickedBookmarks,
      bookmarkMap, common, strings, multiSelectionMode, multiSelectionButtonClicked,
      modal: ref(null),
    };
  }
}
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
