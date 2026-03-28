<!--
  - Copyright (c) 2021-2026 Martin Denham, Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
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
        <span v-if="config.showOrdinals" class="ordinal-badge skip-offset">§{{ ordinal }}</span>
        <slot/>
      </span>
  </span>
</template>

<script lang="ts" setup>
import {addEventOrdinalInfo} from "@/utils";
import {computed, inject} from "vue";
import {androidKey, configKey, osisDocumentInfoKey, ordinalHighlightKey} from "@/types/constants";

const props = defineProps<{ ordinal: string }>();

const ordinal = computed(() => parseInt(props.ordinal));

const config = inject(configKey)!;
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
@use "@/common.scss" as *;

.ordinal-badge {
    font-size: 0.65em;
    vertical-align: super;
    opacity: 0.5;
    user-select: none;
}
</style>
