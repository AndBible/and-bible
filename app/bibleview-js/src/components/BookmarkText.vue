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
  <template v-if="bookmark.text">
    <AmbiguousSelection ref="ambiguousSelection" @back-clicked="$emit('change-expanded', false)"/>
    <div v-if="expanded" @click.stop="ambiguousSelection?.handle">
      <OsisFragment
          v-if="isBibleBookmark(bookmark) && bookmark.osisFragment"
          :highlight-ordinal-range="bookmark.originalOrdinalRange"
          :highlight-offset-range="highlightOffset"
          :fragment="bookmark.osisFragment"
          hide-titles
      />
    </div>
    <div class="bookmark-text one-liner" v-if="!expanded">
      <q @click.stop="$emit('change-expanded', true)" class="bible-text">{{ bookmark.text }}</q>
    </div>
  </template>
</template>

<script lang="ts" setup>
import {useCommon} from "@/composables";
import OsisFragment from "@/components/documents/OsisFragment.vue";
import {computed, ref} from "vue";
import {BaseBookmark} from "@/types/client-objects";
import AmbiguousSelection from "@/components/modals/AmbiguousSelection.vue";
import {isBibleBookmark} from "@/composables/bookmarks";

const props = withDefaults(defineProps<{
    bookmark: BaseBookmark,
    expanded: boolean
}>(), {
    expanded: false
});

const ambiguousSelection = ref<InstanceType<typeof AmbiguousSelection> | null>(null);

const highlightOffset = computed(() => {
    //const highlightedLength = props.bookmark.text.length;
    //const fullLength = props.bookmark.fullText.length;
    //if(highlightedLength > 0.95*fullLength || highlightedLength > fullLength - 5) return null
    return props.bookmark.offsetRange
});
useCommon();
</script>

<style scoped lang="scss">
@import "~@/common.scss";

.bookmark-text {
  font-style: italic;
}
</style>
