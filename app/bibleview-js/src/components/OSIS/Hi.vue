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
  <span :class="hiStyle"><slot/></span>
</template>

<script setup lang="ts">
import {useCommon} from "@/composables";
import {computed} from "vue";

const props = defineProps<{type?: string, rend?: string}>()

const hiStyle = computed(() => {
    const classes = [];
    if (props.type) {
        classes.push(`hi-${props.type}`);
    }
    if (props.rend) {
        classes.push(`rend-${props.rend}`);
    }
    return classes;
});
useCommon();
</script>

<style>
.hi-italic, .rend-italic {
    font-style: italic;
}

.hi-bold, .rend-italic {
    font-weight: bold;
}

.hi-super, .rend-super {
    vertical-align: super;
}

.rend-x-indirect {
    font-style: italic;
    color: #78909c;
}

.night .rend-x-indirect {
    color: #b0bec5;
}

.monochrome .rend-x-indirect {
    color: inherit;
}

.monochrome .rend-x-indirect::before { content: "⟨"; }
.monochrome .rend-x-indirect::after { content: "⟩"; }
</style>
