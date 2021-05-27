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
  <Modal blocking v-if="showModal" @close="showModal = false">
    <template #title>
      {{ strings.setupLabels }}
    </template>

    <div class="items">
      <div class="item title">
        <FontAwesomeIcon icon="tags"/>
        {{ strings.currentLabels }}
      </div>
      <div class="item">
        <LabelList :bookmark-id="bookmarkId" only-assign in-bookmark />
      </div>
      <div class="item title">
        <FontAwesomeIcon icon="heart"/>
        {{strings.favouriteLabels}}
      </div>
      <div class="item">
        <LabelList :bookmark-id="bookmarkId" favourites only-assign />
      </div>
      <div class="item title">
        <FontAwesomeIcon icon="history"/>
        {{strings.recentLabels}}
      </div>
      <div class="item">
        <LabelList :bookmark-id="bookmarkId" recent only-assign />
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

    const bookmark = computed(() => bookmarkMap.get(props.bookmarkId));

    function assignLabels() {
      if(bookmark.value) {
        android.assignLabels(bookmark.value.id);
      }
    }

    async function showActions() {
      showModal.value = true;
    }
    return {showModal, showActions, assignLabels, ...useCommon()}
  }
}
</script>

<style scoped>
@import "~@/common.scss";
.items {
  display: flex;
  flex-direction: column;
}
.item {
  padding-top: 5px;
  padding-bottom: 5px;
  padding-left: 2px;
  &.title {
    padding-top: 20px;
  }
}
</style>
