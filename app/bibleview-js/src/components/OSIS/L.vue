<!--
  - Copyright (c) 2020 Martin Denham, Tuomas Airaksinen and the And Bible contributors.
  -
  - This file is part of And Bible (http://github.com/AndBible/and-bible).
  -
  - And Bible is free software: you can redistribute it and/or modify it under the
  - terms of the GNU General Public License as published by the Free Software Foundation,
  - either version 3 of the License, or (at your option) any later version.
  -
  - And Bible is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
  - without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
  - See the GNU General Public License for more details.
  -
  - You should have received a copy of the GNU General Public License along with And Bible.
  - If not, see http://www.gnu.org/licenses/.
  -->

<template>
  <br v-if="type==='x-br' || eID"/>
  <template v-else-if="type==='x-indent'">
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
  <slot/>
</template>

<script>
import {checkUnsupportedProps, useCommon} from "@/composables";
import {computed} from "@vue/reactivity";

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
    const levelInt = computed(() => parseInt(props.level));
    return {levelInt, ...useCommon()};
  },
}
</script>

<style scoped>
</style>
