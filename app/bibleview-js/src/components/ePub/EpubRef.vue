<!--
  - Copyright (c) 2022-2022 Martin Denham, Tuomas Airaksinen and the AndBible contributors.
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
  <a href="#link" @click.prevent="openLink($event, toKey, toId)"><slot/></a>
</template>

<script setup lang="ts">
import {useCommon} from "@/composables";
import {inject} from "vue";
import {addEventFunction, EventPriorities} from "@/utils";
import {androidKey, osisDocumentInfoKey} from "@/types/constants";

defineProps<{href: string, toKey: string, toId: string}>();
const {openEpubLink} = inject(androidKey)!;
const {bookInitials} = inject(osisDocumentInfoKey)!;
const {strings} = useCommon()

function openLink(event: MouseEvent, toKey: string, toId: string) {
    addEventFunction(
        event,
        () => openEpubLink(bookInitials, toKey, toId),
        {title: strings.externalLink, priority: EventPriorities.EXTERNAL_LINK}
    );
}
</script>

<style scoped>

</style>