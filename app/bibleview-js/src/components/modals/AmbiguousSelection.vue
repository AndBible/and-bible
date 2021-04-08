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
    <template v-for="(s, index) of selections" :key="index">
      <button class="button light" @click.stop="selected(s)">
        <span :style="`color: ${s.options.color}`"><FontAwesomeIcon v-if="s.options.icon" :icon="s.options.icon"/></span>
        {{s.options.title}} <LabelList v-if="s.options.bookmark" :bookmark="s.options.bookmark" :labels="getLabels(s.options.bookmark)"/>
      </button>
    </template>
    <template #title>
      {{ strings.ambiguousSelection }}
    </template>
    <template #footer>
      <button class="button" @click="cancelled">{{strings.cancel}}</button>
    </template>
  </Modal>
</template>

<script>
import Modal from "@/components/modals/Modal";
import {useCommon} from "@/composables";
import {FontAwesomeIcon} from "@fortawesome/vue-fontawesome";
import {inject, ref} from "@vue/runtime-core";
import {Deferred, getEventFunctions} from "@/utils";
import LabelList from "@/components/LabelList";

export default {
  name: "AmbiguousSelection",
  emits: ["back-clicked"],
  props: {
    blocking: {type: Boolean, default: false}
  },
  components: {LabelList, Modal, FontAwesomeIcon},
  setup(props, {emit}) {
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

    async function handle(event) {
      const eventFunctions = getEventFunctions(event);
      if(eventFunctions.length > 0) {
        if(eventFunctions.length === 1) eventFunctions[0].callback();
        else {
          const s = await select(eventFunctions);
          if(s) s.callback();
        }
      } else {
        emit("back-clicked")
      }
    }

    const {bookmarkLabels} = inject("globalBookmarks")

    function getLabels(bookmark) {
        return bookmark.labels.map(labelId => bookmarkLabels.get(labelId))
    }

    return {selected, handle, cancelled, showModal, selections, getLabels, ...useCommon()};
  }
}
</script>

<style scoped>

</style>
