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
  <div class="large-action" @click="emit('click')">
    <FontAwesomeLayers v-if="button === 'BOOKMARK'">
      <FontAwesomeIcon icon="bookmark"/>
      <FontAwesomeIcon icon="plus" transform="shrink-5 down-6 right-12"/>
    </FontAwesomeLayers>
    <FontAwesomeLayers v-else-if="button === 'BOOKMARK_NOTES'">
      <FontAwesomeIcon icon="edit"/>
      <FontAwesomeIcon icon="plus" transform="shrink-5 down-6 right-12"/>
    </FontAwesomeLayers>
    <FontAwesomeIcon v-else-if="button === 'SHARE'" icon="share-alt"/>
    <FontAwesomeIcon v-else-if="button === 'MY_NOTES'" icon="file-alt"/>
    <FontAwesomeIcon v-else-if="button === 'COMPARE'" icon="custom-compare"/>
    <FontAwesomeIcon v-else-if="button === 'MEMORIZE'" :icon="faBrain"/>
    <FontAwesomeIcon v-else-if="button === 'SPEAK'" icon="headphones"/>
    <FontAwesomeIcon v-else-if="button === 'ADD_PARAGRAPH_BREAK'" :icon="faParagraph"/>
    <div class="title">
      <template v-if="button === 'BOOKMARK'">{{ strings.addBookmark }}</template>
      <template v-else-if="button === 'BOOKMARK_NOTES'">{{ vertical ? strings.verseNoteLong : strings.verseNote }}</template>
      <template v-else-if="button === 'SHARE'">{{ vertical ? strings.verseShareLong : strings.verseShare }}</template>
      <template v-else-if="button === 'MY_NOTES'">{{ strings.verseMyNotes }}</template>
      <template v-else-if="button === 'COMPARE'">{{ vertical ? strings.verseCompareLong : strings.verseCompare }}</template>
      <template v-else-if="button === 'MEMORIZE'">{{ vertical ? strings.verseMemorizeLong : strings.verseMemorize }}</template>
      <template v-else-if="button === 'SPEAK'">{{ strings.verseSpeak }}</template>
      <template v-else-if="button === 'ADD_PARAGRAPH_BREAK'">{{ vertical ? strings.verseParagraphBreakLong : strings.verseParagraphBreak }}</template>
    </div>
  </div>
</template>

<script lang="ts" setup>
import { PropType } from "vue";
import { FontAwesomeIcon, FontAwesomeLayers } from "@fortawesome/vue-fontawesome";
import { useCommon } from "@/composables";
import { ModalButtonId } from "@/composables/config";
import { faBrain, faParagraph } from "@fortawesome/free-solid-svg-icons";

defineProps({
  button: {
    type: String as PropType<ModalButtonId>,
    required: true
  },
  vertical: {
    type: Boolean,
    default: false
  }
});

const emit = defineEmits(['click']);
const { strings } = useCommon();

</script>

<style scoped lang="scss">
@use "@/common.scss" as *;

</style>