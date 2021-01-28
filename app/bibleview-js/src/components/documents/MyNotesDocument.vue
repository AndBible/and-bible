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
    <div class="edit-button" @click.stop="editNote(b)">
      <FontAwesomeIcon icon="edit"/>
    </div>

    <b>{{ sprintf(strings.verses, b.verseRangeAbbreviated) }}</b>
    <div v-html="b.notes"/>
  </div>
</template>

<script>
import {computed} from "@vue/reactivity";
import {inject} from "@vue/runtime-core";
import {useCommon} from "@/composables";
import {emit, Events, setupEventBusListener} from "@/eventbus";
import {FontAwesomeIcon} from "@fortawesome/vue-fontawesome";

export default {
  name: "MyNotesDocument",
  components: {FontAwesomeIcon},
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

    function editNote(b) {
      const bookmarkLabels_ = b.labels.map(l => globalBookmarks.bookmarkLabels.get(l)).filter(l => !l.noHighlight);
      emit(Events.BOOKMARK_FLAG_CLICKED, b, bookmarkLabels_, {open: true})
    }

    return {notes, save, editNotes, editNote, ...useCommon()}
  }
}
</script>

<style scoped>
.note-container {
  margin: 5pt;
  background: rgba(255, 255, 255, 0.3);
  .night & {
    background: rgba(255, 255, 255, 0.05);
  }
}
.edit-button {
  position: absolute;
  height: 20pt;
  width: 20pt;
  right: 5px;
  color: #939393;
}
</style>
