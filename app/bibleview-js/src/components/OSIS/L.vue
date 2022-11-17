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
  <span class="skip-offset">
    <br v-if="isBreakLine"/>
    <template v-else-if="isIndent">
      <template v-for="i in levelInt" :key="i">
        &nbsp;
      </template>
    </template>
    <template v-else-if="sID">
      <template v-for="i in levelInt-1" :key="i">
        &nbsp;
      </template>
    </template>
    <template v-else>
      <template v-for="i in levelInt-1" :key="i">
        &nbsp;
      </template>
    </template>
  </span>
  <slot/>
</template>

<script>
import {checkUnsupportedProps, useCommon} from "@/composables";
import {computed, toRefs} from "vue";

export default {
  name: "L",
  props: {
    sID: {type: String, default: null},
    eID: {type: String, default: null},
    level: {type: String, default: "1"},
    type: {type: String, default: null},
  },
  setup(props) {
    checkUnsupportedProps(props, "type", ["x-br", "x-indent"]);
    const {sID, eID, level, type} = toRefs(props);
    const levelInt = computed(() => parseInt(level.value));
    const isBreakLine = computed(() => {
      const allNull = sID.value === null && eID.value === null && level.value === "1" && type.value === null;
      return type.value === 'x-br' || eID.value || allNull;
    })
    const isIndent = computed(() => type.value === "x-indent");
    return {levelInt, isBreakLine, isIndent, ...useCommon()};
  },
}
</script>

<style scoped>
</style>
