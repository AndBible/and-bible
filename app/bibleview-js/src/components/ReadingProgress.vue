<!--
  - Copyright (c) 2026 Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
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
  <div v-if="text" class="reading-progress" :style="{bottom}">
    <div class="reading-progress-text">{{ text }}</div>
  </div>
</template>

<script setup lang="ts">
// `bottom` is computed by the parent (BibleView) to respect the bottom offset /
// window button bar and to stack above the page-number overlay when it is shown.
withDefaults(defineProps<{ text: string | null; bottom?: string }>(), {bottom: '0px'});
</script>

<style lang="scss">
.reading-progress {
  z-index: 5;
  position: fixed;
  right: 2mm;
  // `bottom` is set inline from the parent; `margin-bottom` matches the page-number
  // overlay's 2mm gap from the bottom edge.
  margin-bottom: 2mm;
  padding: 0 2mm;
  height: 0.5cm;
  display: flex;
  align-items: center;
  font-size: 70%;
  font-weight: bold;
  color: var(--text-color);
  background: rgba(207, 207, 207, 0.71);
  border-radius: 0.5cm;
  .noAnimation & {
    background-color: var(--background-color);
    border: 1px solid var(--text-color);
  }
}
</style>
