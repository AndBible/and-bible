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
  <div :id="`doc-${document.id}`"
       class="bible-document"
       :data-book-initials="bookInitials"
  >
    <Chapter v-if="document.addChapter" :n="document.chapterNumber.toString()"/>
    <OsisFragment :fragment="document.osisFragment"/>
  </div>
</template>

<script>
import {inject, provide} from "@vue/runtime-core";
import {useBookmarks} from "@/composables/bookmarks";
import {ref} from "@vue/reactivity";
import OsisFragment from "@/components/documents/OsisFragment";
import {useCommon} from "@/composables";
import Chapter from "@/components/OSIS/Chapter";

export default {
  name: "BibleDocument",
  components: {OsisFragment, Chapter},
  props: {
    document: {type: Object, required: true},
  },
  setup(props) {
    // eslint-disable-next-line no-unused-vars,vue/no-setup-props-destructure
    const {id, bibleBookName, bookInitials, bookmarks, ordinalRange, originalOrdinalRange, v11n} = props.document;

    provide("bibleDocumentInfo", {bibleBookName, bookInitials, ordinalRange, originalOrdinalRange, v11n})

    const globalBookmarks = inject("globalBookmarks");
    globalBookmarks.updateBookmarks(...bookmarks);

    const {config, appSettings, ...common} = useCommon();

    useBookmarks(id, ordinalRange, globalBookmarks, bookInitials, ref(true), common, config, appSettings);

    let footNoteCount = ordinalRange[0] || 0;

    function getFootNoteCount() {
      return footNoteCount ++;
    }

    provide("footNoteCount", {getFootNoteCount});

    return {bookInitials, ...common}
  }
}
</script>

<style scoped>

</style>
