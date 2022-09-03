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
  <a href="#link" @click.prevent="openLink($event, href)"><slot/></a>
</template>

<script>
import {useCommon} from "@/composables";
import {inject} from "vue";
import {addEventFunction, EventPriorities} from "@/utils";

export default {
  name: "A",
  props: {href: {type: String, required: true}},
  setup() {
    const {openExternalLink} = inject("android");
    const {strings, ...common} = useCommon()
    function openLink(event, url) {
      addEventFunction(event, () => openExternalLink(url), {title: strings.externalLink, priority: EventPriorities.EXTERNAL_LINK});
    }
    return {openLink, ...common};
  },
}
</script>

<style scoped>

</style>
