<!--
  - Copyright (c) 2021 Martin Denham, Tuomas Airaksinen and the And Bible contributors.
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
  <div class="label-list">
    <span v-for="label in labels" :key="label.id" :style="labelStyle(label)" class="label">{{label.name}}</span>
  </div>
</template>

<script>
import {useCommon} from "@/composables";

export default {
  props: {labels: {type: Array, required: true}},
  name: "LabelList",
  setup() {
    const {adjustedColor, ...common} = useCommon();
    function labelStyle(label) {
      return "background-color: " + adjustedColor(label.color).string() + ";";
    }
    return {labelStyle, ...common}
  }
}
</script>

<style scoped>
.label {
  color: white;
  .night & {
    color: #d7d7d7;
  }
  font-size: small;
  border-radius: 6pt;
  padding-left: 4pt;
  padding-right: 4pt;
  margin-right: 2pt;
}
.label-list {
  vertical-align: super;
  line-height: 1.1em;
  display: inline-flex;
  flex-direction: row;
  bottom: 30pt;
  padding: 2pt 2pt 0 0;
}
</style>
