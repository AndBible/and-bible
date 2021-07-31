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
  <div class="ambiguous-button" :style="buttonStyle" @click.stop="openBookmark">
    <div class="one-liner">
      {{ bookmark.verseRangeAbbreviated }} <q v-if="bookmark.text"><i>{{ bookmark.text}}</i></q>
    </div>
    <div v-if="bookmark.hasNote" class="one-liner">
      {{ htmlToString(bookmark.notes)}}
    </div>

    <div style="overflow-x: auto">
      <LabelList in-bookmark single-line :bookmark-id="bookmark.id"/>
    </div>

    <div style="height: 7px"/>
    <BookmarkButtons
      :bookmark="bookmark"
      show-study-pad-buttons
      @edit-clicked="editNotes"
    />
  </div>
</template>

<script>
import LabelList from "@/components/LabelList";
import {inject} from "@vue/runtime-core";
import {computed} from "@vue/reactivity";
import {useCommon} from "@/composables";
import {Events, emit} from "@/eventbus";
import {adjustedColor} from "@/utils";
import Color from "color";
import BookmarkButtons from "@/components/BookmarkButtons";

export default {
  emits: ["selected"],
  name: "AmbiguousSelectionBookmarkButton",
  props: {
    bookmarkId: {required: true, type: Number},
    locateTop: {default: false, type: Boolean},
  },
  components: {LabelList, BookmarkButtons},
  setup(props, {emit: $emit}) {
    const {bookmarkMap, bookmarkLabels} = inject("globalBookmarks");
    const common = useCommon();
    const bookmark = computed(() => {
      return bookmarkMap.get(props.bookmarkId);
    });

    const primaryLabel = computed(() => {
      const primaryLabelId = bookmark.value.primaryLabelId || bookmark.value.labels[0];
      return bookmarkLabels.get(primaryLabelId);
    });

    const buttonStyle = computed(() => {
      let color = Color(primaryLabel.value.color);
      color = color.alpha(0.5)
      return `background-color: ${color.hsl()};`
    });

    const bookmarkColor = computed(() => {
      return `color: ${adjustedColor(primaryLabel.value.color)}`
    });

    function editNotes() {
      $emit("selected");
      emit(Events.BOOKMARK_CLICKED, bookmark.value.id, {openNotes: true, locateTop: props.locateTop});
    }

    function openBookmark() {
      $emit("selected");
      emit(Events.BOOKMARK_CLICKED, bookmark.value.id, {locateTop: props.locateTop});
    }

    function htmlToString(html) {
      const ele = document.createElement("div")
      ele.innerHTML = html
      return ele.innerText
    }

    return {
      bookmark, buttonStyle, adjustedColor, bookmarkColor, editNotes, openBookmark, ...common, htmlToString,
    };
  },
}
</script>

<style scoped lang="scss">
@import "~@/common.scss";
.ambiguous-button {
  color: black;
  .night & {
    color: #d7d7d7;
  }
  @extend .button;
  text-align: start;
}
</style>
