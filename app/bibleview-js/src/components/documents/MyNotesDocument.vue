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
    {{strings.noNotes}}
  </div>
  <div v-else>
    <h2>{{ document.verseRange }}</h2>
  </div>
  <div class="note-container verse" v-for="b in notes" :key="b.id" :id="`v-${b.ordinalRange[0]}`">
    <MyNoteRow :bookmark="b"/>
  </div>
</template>

<script>
import {computed} from "@vue/reactivity";
import {inject} from "@vue/runtime-core";
import {useCommon} from "@/composables";
import MyNoteRow from "@/components/MyNoteRow";

export default {
  name: "MyNotesDocument",
  components: {MyNoteRow},
  props: {
    document: {type: Object, required: true},
  },
  setup(props) {
    // eslint-disable-next-line vue/no-setup-props-destructure
    const {bookmarks} = props.document;

    const globalBookmarks = inject("globalBookmarks");
    const android = inject("android");

    globalBookmarks.updateBookmarks(...bookmarks);

    const notes = computed(() => {
      return globalBookmarks.bookmarks.value.filter(b => b.notes !== null)
    });

    function editNotes(b, newText) {
      b.notes = newText;
    }

    function save(b) {
      android.saveBookmarkNote(b.id, b.notes);
    }

    return {notes, save, editNotes, ...useCommon()}
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
