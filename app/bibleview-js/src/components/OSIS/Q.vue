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

<template><span :class="{redLetters: config.showRedLetters && isJesus}"><slot/></span></template>

<script>
import {checkUnsupportedProps, useCommon} from "@/composables";

export default {
  name: "Q",
  props: {
    marker: {type: String, default: null},
    sID: {type: String, default: null},
    eID: {type: String, default: null},
    who: {type: String, default: null},
    level: {type: String, default: null},
  },
  computed: {
    isJesus: ({who}) => who && who.toLowerCase() === "jesus"
  },
  setup(props) {
    checkUnsupportedProps(props, "who", ["jesus", "Jesus"]);
    return useCommon();
  },
}
</script>

<style scoped lang="scss">
$redLetters: rgb(215, 13, 13);
.redLetters {
  color: $redLetters !important;
}
.redLetters > a:link, a:visited {
  color: $redLetters;
}
</style>
