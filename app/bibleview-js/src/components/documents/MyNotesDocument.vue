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
  <div v-if="notes.length === 0">
    <h2>{{ strings.noMyNotesTitle }}</h2>
    <p>{{ strings.noMyNotesDescription }}</p>
  </div>
  <div v-else>
    <h2>{{ document.verseRange }}</h2>
  </div>
  <div class="note-container verse" v-for="b in notes" :key="b.id" :id="`o-${b.ordinalRange[0]}`">
    <MyNoteRow :bookmark="b"/>
  </div>
</template>

<script setup lang="ts">
import {computed, inject} from "vue";
import {useCommon} from "@/composables";
import MyNoteRow from "@/components/MyNoteRow.vue";
import {sortBy} from "lodash";
import {intersection, rangesOverlap} from "@/utils";
import {globalBookmarksKey} from "@/types/constants";
import {BibleBookmark} from "@/types/client-objects";
import {MyNotesDocument} from "@/types/documents";
import {isBibleBookmark} from "@/composables/bookmarks";

const props = defineProps<{ document: MyNotesDocument }>()
// eslint-disable-next-line vue/no-setup-props-destructure
const {bookmarks, ordinalRange} = props.document;

const {config, strings} = useCommon()

const globalBookmarks = inject(globalBookmarksKey)!;

globalBookmarks.updateBookmarks(bookmarks);

const notes = computed<BibleBookmark[]>(() => {
    let bs1 = globalBookmarks.bookmarks.value;

    const hideLabels = new Set(config.bookmarksHideLabels);
    let bs = bs1.filter(v =>
        isBibleBookmark(v) &&
        rangesOverlap(v.ordinalRange, ordinalRange, {addRange: true, inclusive: true}) &&
        intersection(new Set(v.labels), hideLabels).size === 0
    ) as BibleBookmark[]

    if (!config.showBookmarks) {
        bs = bs.filter(v => v.hasNote)
    }
    return sortBy(bs, [o => o.ordinalRange[0], o => o.offsetRange && o.offsetRange[0]])
});
</script>

<style scoped lang="scss">
@import "~@/common.scss";

.bible-text {
  margin-top: 2pt;
  text-indent: 5pt;
  margin-bottom: 2pt;
  font-style: italic;
}

.notes {
  padding-top: 2pt;
  text-indent: 2pt;
}

</style>
