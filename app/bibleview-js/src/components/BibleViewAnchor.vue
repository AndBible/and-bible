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
  <span
      :id="`o-${ordinal}`"
      :data-ordinal="ordinal"
      class="ordinal"
      @click="ordinalClicked"
  >
      <span class="highlight-transition" :class="{isHighlighted: highlighted}">
        <slot/>
      </span>
  </span>
</template>

<script lang="ts" setup>
import {addEventOrdinalInfo} from "@/utils";
import {computed, inject} from "vue";
import {androidKey, osisDocumentInfoKey, ordinalHighlightKey} from "@/types/constants";

const props = defineProps<{ ordinal: string }>();

const ordinal = computed(() => parseInt(props.ordinal));

const {querySelection} = inject(androidKey)!
const {highlightOrdinal, isHighlighted} = inject(ordinalHighlightKey)!;

const osisDocumentInfo = inject(osisDocumentInfoKey);

const highlighted = computed(
    () => isHighlighted(ordinal.value, osisDocumentInfo?.bookInitials, osisDocumentInfo?.osisRef)
);

if (
    osisDocumentInfo?.highlightedOrdinalRange &&
    ordinal.value <= osisDocumentInfo.highlightedOrdinalRange[1] &&
    ordinal.value >= osisDocumentInfo.highlightedOrdinalRange[0]
) {
    highlightOrdinal(ordinal.value, osisDocumentInfo?.bookInitials, osisDocumentInfo?.osisRef)
}
function ordinalClicked(event: Event) {
    if(querySelection() != null || !osisDocumentInfo) return;

    addEventOrdinalInfo(event, {
        ordinal: ordinal.value,
        bookInitials: osisDocumentInfo.bookInitials,
        osisRef: osisDocumentInfo.osisRef
    })
}

</script>
<style lang="scss">
@import "~@/common.scss";
</style>
