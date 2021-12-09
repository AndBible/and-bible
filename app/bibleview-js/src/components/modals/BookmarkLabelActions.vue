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
  <Modal blocking v-if="showModal" @close="showModal = false" :locate-top="locateTop">
    <template #title>
      {{ strings.bookmarkLabels }}
    </template>
    <template #extra-buttons>
      <div class="modal-action-button" @touchstart.stop @click.stop="assignLabels">
        <FontAwesomeIcon icon="tags"/>
      </div>
    </template>

    <div class="items">
      <div class="item">
        <LabelList :bookmark-id="bookmarkId" only-assign in-bookmark />
        <hr/>
      </div>
      <div class="item title top" v-if="hasFavourites">
        <FontAwesomeIcon icon="heart"/>
        {{strings.favouriteLabels}}
      </div>
      <div class="item" v-show="hasFavourites">
        <LabelList :bookmark-id="bookmarkId" favourites only-assign @has-entries="hasFavourites = $event"/>
      </div>
      <div class="item title" v-if="hasRecent">
        <FontAwesomeIcon icon="history"/>
        {{strings.recentLabels}}
      </div>
      <div class="item" v-show="hasRecent">
        <LabelList :bookmark-id="bookmarkId" recent only-assign @has-entries="hasRecent = $event" />
      </div>
      <!--div class="item title">
        <FontAwesomeIcon icon="fire-alt"/>
        {{strings.frequentlyUsedLabels}}
      </div>
      <div class="item">
        <LabelList :bookmark-id="bookmarkId" frequent only-assign />
      </div-->
      <button class="item button light" @click="assignLabels">
        <FontAwesomeIcon icon="tags"/>
        {{ strings.assignLabelsMenuEntry1 }}
      </button>
    </div>
  </Modal>
</template>

<script>
import Modal from "@/components/modals/Modal";
import {computed, ref} from "@vue/reactivity";
import {useCommon} from "@/composables";
import {FontAwesomeIcon} from "@fortawesome/vue-fontawesome";
import {inject} from "@vue/runtime-core";
export default {
  name: "BookmarkLabelActions",
  components: {Modal, FontAwesomeIcon},
  props: {
    bookmarkId: {type: Number, required: true},
  },
  setup(props) {
    const showModal = ref(false);
    const {bookmarkMap} = inject("globalBookmarks");
    const android = inject("android");
    const locateTop = ref(true);

    const bookmark = computed(() => bookmarkMap.get(props.bookmarkId));

    function assignLabels() {
      if(bookmark.value) {
        android.assignLabels(bookmark.value.id);
      }
    }
    const hasFavourites = ref(false);
    const hasRecent = ref(false);
    function showActions({locateTop: locateTop_ = true} = {}) {
      locateTop.value = locateTop_;
      showModal.value = true;
    }
    return {showModal, showActions, assignLabels, hasFavourites, hasRecent, ...useCommon()}
  }
}
</script>

<style scoped lang="scss">
@import "~@/common.scss";
.items {
  @extend .visible-scrollbar;
  display: flex;
  flex-direction: column;
  max-height: calc(var(--max-height) - 25pt);
  overflow-y: auto;
}
.item {
  padding-top: 5px;
  padding-bottom: 5px;
  padding-left: 2px;
  &.title {
    padding-top: 10px;
    &.top {
      padding-top: 0;
    }
  }

}
hr {
  border-top: 1px solid rgba(0, 0, 0, 0.2);
}
</style>
