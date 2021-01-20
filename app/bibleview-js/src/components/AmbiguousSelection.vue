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
  <Modal v-if="selections" @close="$emit('close')">
    <template v-for="(s, index) of selections" :key="index">
      <button class="button light" @click="s.callback(); $emit('close')">
        <span :style="`color: ${s.options.color}`"><FontAwesomeIcon v-if="s.options.icon" :icon="s.options.icon"/></span> {{s.options.title}}</button>
    </template>
    <template #title>
      {{ strings.ambiguousSelection }}
    </template>
    <template #footer>
      <button class="button" @click="$emit('close')">{{strings.cancel}}</button>
    </template>
  </Modal>
</template>

<script>
import Modal from "@/components/Modal";
import {useCommon} from "@/composables";
import {FontAwesomeIcon} from "@fortawesome/vue-fontawesome";

export default {
  name: "AmbiguousSelection.vue",
  emits: ["close"],
  components: {Modal, FontAwesomeIcon},
  props: {selections: {type: Array, required: false}},
  setup() {
    return useCommon();
  }
}
</script>

<style scoped>

</style>
