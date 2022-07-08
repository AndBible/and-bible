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
  <div v-if="notes.length === 0">
    <h2>{{strings.noMyNotesTitle}}</h2>
    <p>{{strings.noMyNotesDescription}}</p>
  </div>
  <div v-else>
    <h2>{{ document.verseRange }}</h2>
  </div>
  <div class="note-container verse" v-for="b in notes" :key="b.id" :id="`o-${b.ordinalRange[0]}`">
    <MyNoteRow :bookmark="b"/>
  </div>
</template>

<script>
import {inject} from "vue";
import {useCommon} from "@/composables";
import MyNoteRow from "@/components/MyNoteRow";
import {computed} from "vue";
import {sortBy} from "lodash";
import {intersection} from "@/utils";

export default {
  name: "MyNotesDocument",
  components: {MyNoteRow},
  props: {
    document: {type: Object, required: true},
  },
  setup(props) {
    // eslint-disable-next-line vue/no-setup-props-destructure
    const {bookmarks} = props.document;

    const config = inject("config");

    const globalBookmarks = inject("globalBookmarks");

    globalBookmarks.updateBookmarks(...bookmarks);

    const notes = computed(() => {
      let bs = globalBookmarks.bookmarks.value;

      const hideLabels = new Set(config.bookmarksHideLabels);
      bs = bs.filter(v => intersection(new Set(v.labels), hideLabels).size === 0)

      if(!config.showBookmarks) {
        bs = bs.filter(v => v.hasNote)
      }
      return sortBy(bs, [o => o.ordinalRange[0], o => o.offsetRange && o.offsetRange[0]])
    });

    return {notes, ...useCommon()}
  }
}
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
