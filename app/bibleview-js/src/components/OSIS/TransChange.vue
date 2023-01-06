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
  <span v-if="show" :class="{nonCanonical: config.makeNonCanonicalItalic && isNonCanonical}"><slot/></span>
</template>

<script setup lang="ts">
import {checkUnsupportedProps, useCommon} from "@/composables";
import {computed} from "vue";

const props = defineProps({
    type: {type: String, default: null}
});
checkUnsupportedProps(props, "type", ["added"]);
const {config} = useCommon();
const isNonCanonical = computed(() => props.type.toLowerCase() === "added");
const show = computed(() => (!isNonCanonical.value) || (isNonCanonical.value && config.showNonCanonical));
</script>

<style scoped>
.nonCanonical {
    font-style: italic;
}
</style>
