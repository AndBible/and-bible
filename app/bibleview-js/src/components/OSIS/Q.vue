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

<template><span class="q" :class="{redLetters: config.showRedLetters && isJesus}">{{ displayMarker }}<slot/></span></template>

<script setup lang="ts">
import {checkUnsupportedProps, useCommon} from "@/composables";
import {computed} from "vue";

const props = defineProps<{
    marker?: string
    sID?: string
    eID?: string
    who?: string
    level?: string
}>();

checkUnsupportedProps(props, "who", ["jesus", "Jesus"]);
const isJesus = computed(() => props.who && props.who.toLowerCase() === "jesus");
const displayMarker = computed(() => {
    if (props.marker) {
        return props.marker;
    } else {
        return "";
    }
});
const {config} = useCommon();
</script>

<style lang="scss">
$redLetters: rgb(215, 13, 13);
.redLetters {
  color: $redLetters !important;
  .monochrome & {
    color: unset !important;
  }
}

.redLetters > a:link, a:visited {
  color: $redLetters;
  .monochrome & {
    color: unset !important;
  }
}
</style>
