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
  <Modal :blocking="blocking" v-if="showModal" @close="cancelled">
    <div class="buttons">
      <template v-for="(s, index) of selections" :key="index">
        <template v-if="!s.options.bookmarkId">
          <button class="button light" @click.stop="selected(s)">
            <span :style="`color: ${s.options.color}`"><FontAwesomeIcon v-if="s.options.icon" :icon="s.options.icon"/></span>
            {{s.options.title}}
          </button>
        </template>
        <AmbiguousSelectionBookmarkButton
          v-else-if="bookmarkMap.has(s.options.bookmarkId)"
          :bookmark-id="s.options.bookmarkId"
          :document-id="s.options.documentId"
          @selected="selected(s)"
        />
      </template>
    </div>
    <template #title>
      {{ strings.ambiguousSelection }}
    </template>
  </Modal>
</template>

<script>
import Modal from "@/components/modals/Modal";
import {useCommon} from "@/composables";
import {FontAwesomeIcon} from "@fortawesome/vue-fontawesome";
import {inject, ref} from "@vue/runtime-core";
import {Deferred, getEventFunctions} from "@/utils";
import AmbiguousSelectionBookmarkButton from "@/components/modals/AmbiguousSelectionBookmarkButton";
import {emit, Events} from "@/eventbus";

export default {
  name: "AmbiguousSelection",
  emits: ["back-clicked"],
  props: {
    blocking: {type: Boolean, default: false}
  },
  components: {Modal, FontAwesomeIcon, AmbiguousSelectionBookmarkButton},
  setup(props, {emit: $emit}) {
    const showModal = ref(false);
    const selections = ref(null);
    let deferred = null;

    async function select(sel) {
      selections.value = sel;
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

    const {bookmarkMap} = inject("globalBookmarks");

    async function handle(event) {
      const eventFunctions = getEventFunctions(event);
      if(eventFunctions.length > 0) {
        if(eventFunctions.length === 1) {
          if(eventFunctions[0].options.bookmarkId) {
            emit(Events.BOOKMARK_CLICKED, eventFunctions[0].options.bookmarkId, eventFunctions[0].options.documentId);
          } else {
            eventFunctions[0].callback();
          }
        }
        else {
          const s = await select(eventFunctions);
          if(s && s.callback) s.callback();
        }
      } else {
        $emit("back-clicked")
      }
    }

    return {selected, handle, cancelled, showModal, selections, bookmarkMap, ...useCommon()};
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
