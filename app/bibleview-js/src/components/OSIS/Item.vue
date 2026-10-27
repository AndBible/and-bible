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

<template>
  <li><span class="highlight-transition" :class="{isHighlighted: highlighted}"><slot/></span></li>
</template>

<script setup lang="ts">
import {useCommon} from "@/composables";
import {computed, inject} from "vue";
import {ordinalHighlightKey, verseInfoKey} from "@/types/constants";

const verseInfo = inject(verseInfoKey, null);
const ordinalHighlight = inject(ordinalHighlightKey, null);
// The verse's inline background does not paint inside block-level list items.
const highlighted = computed(() => !!verseInfo && !!ordinalHighlight?.isHighlighted(verseInfo.ordinal));

useCommon()
</script>

<style>

</style>
