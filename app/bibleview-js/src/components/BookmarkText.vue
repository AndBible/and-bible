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
    <AmbiguousSelection ref="ambiguousSelection" @back-clicked="$emit('change-expanded', false)"/>
    <div v-if="expanded" @click.stop="ambiguousSelection.handle">
      <OsisFragment
          :highlight-ordinal-range="bookmark.originalOrdinalRange"
          :highlight-offset-range="highlightOffset"
          :fragment="bookmark.osisFragment"
          hide-titles
      />
    </div>
    <span class="bookmark-text">
      <q v-if="!expanded" @click.stop="$emit('change-expanded', true)" class="bible-text">{{abbreviated(bookmark.text, 50)}}</q>
    </span>
  </template>
</template>

<script>
import {useCommon} from "@/composables";
import OsisFragment from "@/components/documents/OsisFragment";
import {computed, ref} from "@vue/reactivity";

export default {
  name: "BookmarkText",
  components: {OsisFragment},
  emits: ["change-expanded"],
  props: {
    bookmark: {type: Object, required: true},
    expanded: {type: Boolean, default: false},
  },
  setup(props) {
    const ambiguousSelection = ref(null);

    const highlightOffset = computed(() => {
      const highlightedLength = props.bookmark.text.length;
      const fullLength = props.bookmark.fullText.length;
      if(highlightedLength > 0.5*fullLength || highlightedLength > fullLength - 5) return null
      return props.bookmark.offsetRange
    });
    return {ambiguousSelection, highlightOffset, ...useCommon()};
  }
}
</script>

<style scoped>
.bookmark-text {
  font-style: italic;
}
</style>
