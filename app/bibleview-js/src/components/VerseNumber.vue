<!--
  - Copyright (c) 2020 Martin Denham, Tuomas Airaksinen and the And Bible contributors.
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

<template><span v-if="show" class="skip-offset verseNumber">{{sprintf(strings.verseNum, verseNum)}}</span></template>

<script>
import {useCommon} from "@/composables";
import {inject} from "@vue/runtime-core";
import {BookCategories} from "@/constants";
import {computed} from "@vue/reactivity";

export default {
  name: "VerseNumber",
  props: {
    verseNum: {type: Number, required: true}
  },
  setup() {
    const fragment = inject("osisFragment");
    const show = computed(() => fragment.bookCategory === BookCategories.BIBLE)
    return {show, ...useCommon()};
  }
}
</script>

<style scoped lang="scss">
@import "~@/common.scss";
.verseNumber {
  @extend .superscript;
  font-size: 60%;
  margin-right: 1pt;
  color: var(--verse-number-color);
}
</style>
