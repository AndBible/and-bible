<!--
  - Copyright (c) 2020-2022 Martin Denham, Tuomas Airaksinen and the AndBible contributors.
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

<template><span :dir="fragment.direction" v-if="show"
                class="skip-offset verseNumber">{{ sprintf(strings.verseNum, verseNum) }}<template v-if="exportMode">.&nbsp;</template></span>
</template>

<script setup lang="ts">
import {useCommon} from "@/composables";
import {computed, inject, ref} from "vue";
import {exportModeKey, osisFragmentKey} from "@/types/constants";

defineProps<{ verseNum: number }>()

const fragment = inject(osisFragmentKey)!;
const show = computed(() => fragment.bookCategory === "BIBLE")
const exportMode = inject(exportModeKey, ref(false));
const {sprintf, strings} = useCommon();
</script>

<style lang="scss">
@import "~@/common.scss";

.verseNumber {
  @extend .superscript;
  font-size: 60%;
  margin-right: 1pt;
  color: var(--verse-number-color);
}
</style>
