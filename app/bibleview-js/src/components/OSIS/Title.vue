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
  <h3 class="titleStyle" :class="{'skip-offset': !isCanonical, isSubTitle}" v-if="show"><slot/></h3>
</template>

<script setup lang="ts">
import {checkUnsupportedProps, useCommon} from "@/composables";
import {computed, inject} from "vue";

const props = withDefaults(
    defineProps<{
        type?: string
        subType?: string
        canonical: string
        short: string
    }>(), {
        canonical: "false",
    }
);

checkUnsupportedProps(props, "type", ["sub", "x-gen", "x-psalm-book", "main", "chapter", "section"]);
checkUnsupportedProps(props, "subType", ["x-Chapter", "x-preverse"]);
checkUnsupportedProps(props, "canonical", ["true", "false"]);
const {config} = useCommon();
const hideTitles = inject("hideTitles", false);

const isCanonical = computed(() => props.canonical === "true");

const show = computed(() =>
    !hideTitles && config.showSectionTitles
    && ((config.showNonCanonical && !isCanonical.value) || isCanonical)
    && !(props.type === "sub" && props.subType === "x-Chapter")
    && props.type !== "chapter"
    && props.type !== "x-gen",
);

const isSubTitle = computed(() => props.type === "sub");
</script>

<style scoped lang="scss">
.listStyle .titleStyle {
  margin-inline-start: -1em;
}

h3.isSubTitle {
  font-size: 110%;
  margin-inline-start: 1em;
}
</style>
