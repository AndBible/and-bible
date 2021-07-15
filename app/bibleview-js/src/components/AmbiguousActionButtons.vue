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
  <div v-if="verseInfo">
    <div :class="{hasActions, horizontal: !vertical, vertical}">
      <div class="large-action" @click="addBookmark">
        <FontAwesomeLayers>
          <FontAwesomeIcon icon="bookmark"/>
          <FontAwesomeIcon icon="plus" transform="shrink-5 down-6 right-12"/>
        </FontAwesomeLayers>
        <div class="title">{{ strings.addBookmark }}</div>
      </div>
      <div class="large-action" @click="addNote">
        <FontAwesomeLayers>
          <FontAwesomeIcon icon="edit"/>
          <FontAwesomeIcon icon="plus" transform="shrink-5 down-6 right-12"/>
        </FontAwesomeLayers>
        <div class="title">{{ vertical ? strings.verseNoteLong: strings.verseNote }}</div>
      </div>
      <div class="large-action" @click="openMyNotes">
        <FontAwesomeIcon icon="file-alt"/>
        <div class="title">{{ strings.verseMyNotes }}</div>
      </div>
      <div class="large-action" @click="speak">
        <FontAwesomeIcon icon="headphones"/>
        <div class="title">{{ vertical? strings.verseSpeakLong: strings.verseSpeak }}</div>
      </div>
      <div class="large-action" @click="share">
        <FontAwesomeIcon icon="share-alt"/>
        <div class="title">{{ vertical? strings.verseShareLong: strings.verseShare }}</div>
      </div>
      <div class="large-action" @click="compare">
        <FontAwesomeIcon icon="compress-arrows-alt"/>
        <div class="title">{{ vertical? strings.verseCompareLong: strings.verseCompare }}</div>
      </div>
    </div>
  </div>
</template>

<script>
import {computed} from "@vue/reactivity";
import {inject} from "@vue/runtime-core";
import {FontAwesomeIcon, FontAwesomeLayers} from "@fortawesome/vue-fontawesome";
import {useCommon} from "@/composables";

export default {
  name: "AmbiguousActionButtons",
  props: {
    verseInfo: {
      type: Object,
      default: null,
    },
    vertical: {type: Boolean, default: false},
    hasActions: {type: Boolean, default: false},
  },
  components: {
    FontAwesomeIcon, FontAwesomeLayers,
  },
  emits: ["close"],
  setup(props, {emit}) {
    const {strings, ...common} = useCommon()
    const verseInfo = computed(() => props.verseInfo);
    const android = inject("android");

    const bookInitials = computed(() => verseInfo.value && verseInfo.value.bookInitials);
    const ordinal = computed(() => verseInfo.value && verseInfo.value.ordinal);

    function share() {
      android.shareVerse(bookInitials.value, ordinal.value);
    }

    function addBookmark() {
      android.addBookmark(bookInitials.value, ordinal.value, false);
      android.toast(strings.verseTip);
      emit("close");
    }

    function compare() {
      android.compare(bookInitials.value, ordinal.value);
    }

    function addNote() {
      android.addBookmark(bookInitials.value, ordinal.value, true);
      android.toast(strings.verseTip);
      emit("close");
    }

    function openMyNotes() {
      android.openMyNotes(bookInitials.value, ordinal.value);
    }

    function speak() {
      android.speak(bookInitials.value, ordinal.value);
      emit("close");
    }

    return {share, addBookmark, addNote, compare, openMyNotes, speak, strings, ...common}
  }
}
</script>

<style scoped lang="scss">
@import "~@/common.scss";
.large-action {
  display: flex;
  flex-direction: row;
  .horizontal & {
    flex-direction: column;
    font-size: 60%;
    margin: 0 auto 0 auto;
  }
  .vertical & {
    @extend .light;
    @extend .button;
  }

  .fa-layers, .svg-inline--fa {
    padding-inline-end: 14px;
    .horizontal & {
      color: $button-grey;
      margin: 0 auto 0 auto;
      padding-bottom: 5px;
    $size: 20px;
      width: $size;
      height: $size;
    }
  }
  .title {

  }
  padding-bottom: 0.5em;
  .horizontal & {
    .hasActions & {
      padding-bottom: 5px;
    }
  }
}
.horizontal {
  display: flex;
  flex-direction: row;
  justify-content: space-evenly;
  flex-wrap: wrap;
}
</style>
