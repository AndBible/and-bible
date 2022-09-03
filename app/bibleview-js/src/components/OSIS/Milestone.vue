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
  <span :class="{paragraphBreak}">{{marker}}<slot/></span>
</template>

<script>
import {checkUnsupportedProps, useCommon} from "@/composables";
import {computed} from "vue";

export default {
  name: "Milestone",
  props: {
    subType: {type: String, default: null},
    type: {type: String, default: null},
    marker: {type: String, default: ""},
    resp: {type: String, default: ""},
  },
  setup(props) {
    checkUnsupportedProps(props, "resp");
    checkUnsupportedProps(props, "type", ["x-strongsMarkup", "x-PN", "line"]);
    checkUnsupportedProps(props, "subType", ["x-PO", "x-PM"]);
    const paragraphBreak = computed(() => props.type === "line");
    return {paragraphBreak, ...useCommon()};
  },
}
</script>

<style scoped lang="scss">
@import "~@/common.scss";

</style>
