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
  <span v-if="show" class="lg"/><slot/>
</template>

<script>
import {useCommon} from "@/composables";
import {computed} from "@vue/reactivity";

/** The lg or "line group" element is used to contain any group of poetic lines.
 * Poetic lines are handled at the line level by And Bible, not line group
 * so this does nothing.
 */

export default {
  name: "Lg",
  props: {
    sID: {type: String, default: null},
    eID: {type: String, default: null},
    level: {type: String, default: null},
  },
  setup(props) {
    const show = computed(() => props.sID);
    return {show, ...useCommon()};
  },
}
</script>

<style scoped>
.lg {
  height: 0.3em;
  display: block;
}
</style>
