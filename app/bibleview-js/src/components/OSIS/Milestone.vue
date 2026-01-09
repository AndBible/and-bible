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

<!--
  - because verses and paragraphs are separate structures they would logically be <p><v></p></v>
  - which is html not xml but osis wanted to be xml.  therefore the paragraph structure is marked by
  - <milestone> which is equivalent to a pilcrow
  - the crosswire kjv uses type='x-p' and others use type='line'
-->
<template>
  <span class="milestone" :class="{paragraphBreak}">{{ marker }}<slot/></span>
</template>

<script setup lang="ts">
import {checkUnsupportedProps, useCommon} from "@/composables";
import {computed} from "vue";
import {Logger} from "../../utils/logger";
const logger = new Logger({module: "Milestone"});

const props = withDefaults(defineProps<{
    subType?: string
    type?: string
    marker: string
    resp?: string
}>(),{
    marker: "",
    resp: ""
});

logger.debug('setup, props are', {...props});
checkUnsupportedProps(props, "resp");
checkUnsupportedProps(props, "type", ["x-strongsMarkup", "x-PN", "line", "x-p"]);
checkUnsupportedProps(props, "subType", ["x-PO", "x-PM"]);
const paragraphBreak = computed(() => props.type === "line" || props.type === 'x-p');
useCommon();
</script>

<style lang="scss">
@use "@/common.scss" as *;

</style>
