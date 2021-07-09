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
  <Modal v-if="showHelp" @close="showHelp = false" blocking>
    {{strings.verseTip}}
    <template #title>
      {{ strings.addBookmark}}
    </template>
  </Modal>
  <Modal :blocking="blocking" v-if="showModal" @close="cancelled">
    <template #extra-buttons v-if="noActions">
      <button class="modal-action-button right" @touchstart.stop @click="showHelp = !showHelp">
        <FontAwesomeIcon icon="question-circle"/>
      </button>
    </template>

    <div class="buttons">
      <AmbiguousActionButtons v-if="noActions" :verse-info="verseInfo" @close="cancelled"/>
      <template v-for="(s, index) of selections" :key="index">
        <template v-if="!s.options.bookmarkId">
          <button class="button light" @click.stop="selected(s)">
            <span :style="`color: ${s.options.color}`"><FontAwesomeIcon v-if="s.options.icon" :icon="s.options.icon"/></span>
            {{s.options.title}}
          </button>
        </template>
        <AmbiguousSelectionBookmarkButton
          v-else
          :bookmark-id="s.options.bookmarkId"
          @selected="selected(s)"
        />
      </template>
      <AmbiguousActionButtons v-if="!noActions" :verse-info="verseInfo" @close="cancelled"/>
    </div>
    <template #title>
      <template v-if="verseInfo">
        {{ bibleBookName }} {{ verseInfo.chapter}}:{{verseInfo.verse}}
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
import {inject, ref} from "@vue/runtime-core";
import {Deferred, getEventFunctions, getEventVerseInfo} from "@/utils";
import AmbiguousSelectionBookmarkButton from "@/components/modals/AmbiguousSelectionBookmarkButton";
import {emit, Events} from "@/eventbus";
import {computed} from "@vue/reactivity";
import AmbiguousActionButtons from "@/components/AmbiguousActionButtons";

export default {
  name: "AmbiguousSelection",
  props: {
    blocking: {type: Boolean, default: false}
  },
  components: {Modal, FontAwesomeIcon, AmbiguousSelectionBookmarkButton, AmbiguousActionButtons},
  setup() {
    const appSettings = inject("appSettings");
    const {bookmarkMap} = inject("globalBookmarks");
    const {resetHighlights} = inject("verseMap");
    const {modalOpen, closeModals} = inject("modal");

    const showModal = ref(false);
    const verseInfo = ref(null);
    const originalSelections = ref(null);
    const bibleBookName = computed(() => verseInfo.value && verseInfo.value.bibleBookName);

    const selections = computed(() => {
      if(originalSelections.value === null) return null;
      return originalSelections.value.filter(v => {
        if(v.options.bookmarkId) {
          return bookmarkMap.has(v.options.bookmarkId);
        }
        return true;
      });
    });
    let deferred = null;

    async function select(sel) {
      originalSelections.value = sel;
      showModal.value = true;
      deferred = new Deferred();
      return await deferred.wait();
    }

    function selected(s) {
      deferred.resolve(s);
      showModal.value = false;
    }

    function cancelled() {
      if(deferred) {
        deferred.resolve(null);
      }
      showModal.value = false;
    }

    async function handle(event) {
      console.log("AmbiguousSelection handling", event);
      const isActive = appSettings.activeWindow && (performance.now() - appSettings.activeSince > 250);
      const eventFunctions = getEventFunctions(event);
      const hasParticularClicks = eventFunctions.filter(f => !f.options.backClick).length > 0; // not only "back" clicks
      if(!isActive && !hasParticularClicks) return;
      const _verseInfo = getEventVerseInfo(event);
      resetHighlights();

      if(eventFunctions.length > 0 || _verseInfo != null) {
        if(eventFunctions.length === 1 && eventFunctions[0].options.priority > 0) {
          if (eventFunctions[0].options.bookmarkId) {
            emit(Events.BOOKMARK_CLICKED, eventFunctions[0].options.bookmarkId);
          } else {
            eventFunctions[0].callback();
          }
        }
        else {
          if (modalOpen.value && !hasParticularClicks) {
            closeModals();
          } else {
            verseInfo.value = _verseInfo;
            const s = await select(eventFunctions);
            if (s && s.callback) s.callback();
          }
        }
      }
    }

    const noActions = computed(() => selections.value.length === 0);

    return {
      showHelp: ref(false),
      bibleBookName, verseInfo, selected, handle, cancelled, noActions,
      showModal, selections, bookmarkMap, ...useCommon()
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
</style>
