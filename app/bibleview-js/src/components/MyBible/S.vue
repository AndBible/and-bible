<!--
  - Copyright (c) 2020-2026 Martin Denham, Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
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
  <template v-if="showStrongs">
    &nbsp;<a class="skip-offset strongs" :href="link" @click.prevent="openLink"><span ref="slot"><slot/></span></a>
  </template>
</template>

<script setup lang="ts">

import {computed, inject, ref} from "vue";
import {useCommon} from "@/composables";
import {addEventFunction, EventPriorities} from "@/utils";
import {exportModeKey, osisFragmentKey} from "@/types/constants";
import {OsisFragment} from "@/types/client-objects";

const slot = ref<HTMLElement | null>(null);

const osisFragment = inject<OsisFragment>(osisFragmentKey)
const letter = osisFragment?.isNewTestament ? "G" : "H";

const link = computed(() => {
    if (slot.value === null) return;
    const strongsNum = slot.value.innerText
    return `ab-w://?strong=${letter}${strongsNum}`
});
const {strings} = useCommon();

const exportMode = inject(exportModeKey, ref(false));
const showStrongs = computed(() => !exportMode.value);

function openLink(event: MouseEvent) {
    addEventFunction(event, () => {
        if (link.value) {
            window.location.assign(link.value);
        }
    }, {
        priority: EventPriorities.STRONGS_LINK,
        icon: "custom-morph",
        title: strings.strongsAndMorph,
        dottedStrongs: false
    });
}
</script>

<style>
.strongs {
    font-size: 0.9em;
    text-decoration: none;
    color: coral;
}

.monochrome .strongs {
    color: inherit;
    font-style: italic;
}
</style>
