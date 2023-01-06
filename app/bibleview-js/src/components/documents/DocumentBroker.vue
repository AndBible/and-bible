<!--
  - Copyright (c) 2021-2022 Martin Denham, Tuomas Airaksinen and the AndBible contributors.
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
  <OsisDocument v-if="document.type === 'osis'" :document="osis"/>
  <BibleDocument v-else-if="document.type === 'bible'" :document="bible"/>
  <ErrorDocument v-else-if="document.type === 'error'" :document="error"/>
  <MyNotesDocument v-else-if="document.type === 'notes'" :document="myNotes"/>
  <StudyPadDocument v-else-if="document.type === 'journal'" :document="studyPad"/>
  <MultiDocument v-else-if="document.type === 'multi'" :document="multi"/>
</template>

<script setup lang="ts">
import ErrorDocument from "@/components/documents/ErrorDocument.vue";
import OsisDocument from "@/components/documents/OsisDocument.vue";
import BibleDocument from "@/components/documents/BibleDocument.vue";
import MyNotesDocument from "@/components/documents/MyNotesDocument.vue";
import StudyPadDocument from "@/components/documents/StudyPadDocument.vue";
import MultiDocument from "@/components/documents/MultiDocument.vue";
import {BaseDocument, DocumentOfType} from "@/types/documents";

const props = defineProps<{ document: BaseDocument }>()

const bible = props.document as DocumentOfType<"bible">
const osis = props.document as DocumentOfType<"osis">
const error = props.document as DocumentOfType<"error">
const myNotes = props.document as DocumentOfType<"notes">
const studyPad = props.document as DocumentOfType<"journal">
const multi = props.document as DocumentOfType<"multi">

</script>

