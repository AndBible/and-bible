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
  <span>{{marker}}<slot/></span>
</template>

<script>
import {checkUnsupportedProps, useCommon} from "@/composables";

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
    return useCommon();
  },
}
</script>

<style scoped>

</style>
