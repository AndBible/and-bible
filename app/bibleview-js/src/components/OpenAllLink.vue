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
  <div class="open-all" v-if="openAllLink">
    <a :href="openAllLink">{{ strings.openAll }}</a>
  </div>
</template>

<script lang="ts" setup>
import {computed, inject} from "vue";
import {useCommon} from "@/composables";
import {referenceCollectorKey} from "@/types/constants";

const props = withDefaults(defineProps<{ v11n: string | null }>(), {v11n: null});

const referenceCollector = inject(referenceCollectorKey, null);
const openAllLink = computed(() => {
    if (referenceCollector === null) return null;
    const refs = referenceCollector.references;
    if (refs.length < 2) return null;
    return "multi://?" + refs.map(v => "osis=" + encodeURI(v.value)).join("&") + (props.v11n ? "&v11n=" + encodeURI(props.v11n) : "")
});

const {strings} = useCommon();
</script>

<style>
.open-all {
    padding-top: 1em;
    text-align: right;
}
</style>
