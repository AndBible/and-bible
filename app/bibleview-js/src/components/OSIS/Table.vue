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
  <table :border="border"><slot/></table>
</template>

<script>
import {checkUnsupportedProps, useCommon} from "@/composables";

export default {
  name: "Table",
  props: {
    border: {type: String, default: null},
  },
  setup(props) {
    checkUnsupportedProps(props, "border", ["1", "0"])
    return useCommon();
  },
}
</script>

<style scoped>

</style>
