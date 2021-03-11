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
  <template v-if="bookmark.text">
    <div @click.stop="$emit('change-expanded', false)">
      <OsisFragment
          v-if="expanded"
          :highlight-ordinal-range="bookmark.ordinalRange"
          :highlight-offset-range="bookmark.offsetRange"
          :fragment="bookmark.osisFragment"
          hide-titles
      />
    </div>
    <span class="bookmark-text">
      <q v-if="!expanded" @click.stop="$emit('change-expanded', true)" class="bible-text">{{abbreviated(bookmark.text, 80)}}</q>
    </span>
  </template>
</template>

<script>
import {useCommon} from "@/composables";
import OsisFragment from "@/components/documents/OsisFragment";

export default {
  name: "BookmarkText",
  components: {OsisFragment},
  emits: ["change-expanded"],
  props: {
    bookmark: {type: Object, required: true},
    expanded: {type: Boolean, default: false},
  },
  setup() {
    return {...useCommon()};
  }
}
</script>

<style scoped>
.bookmark-text {
  font-style: italic;
}
</style>
