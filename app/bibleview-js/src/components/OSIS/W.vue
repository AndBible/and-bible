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
  <span v-if="(config.showStrongs && lemma) && (config.showMorph && morph)"><a class="strongs" :href="`link://${lemma}`" ref="contentTag"><slot/></a><a class="morph" :href="`link://${morph}`"> ({{morph|split(':', 1)}})</a></span>
  <a v-else-if="(config.showStrongs && lemma) && (!config.showMorph || !morph)" class="strongs" :href="`link://${lemma}`" ref="contentTag"><slot/></a>
  <a v-else-if="(!config.showStrongs || !lemma) && (config.showMorph && morph)" class="morph" :href="`link://${morph}`" ref="contentTag"><slot/></a>
  <span v-else ref="contentTag"><slot/></span>
</template>

<script>
import TagMixin from "@/components/TagMixin";

export default {
  name: "W",
  props: {
    lemma: {type: String, default: null}, // strong:H8064
    morph: {type: String, default: null}, // strongMorph:TH8792
  },
  mixins: [TagMixin],
}
</script>

<style scoped>
  .strongs {
    color: #4b9700;
  }
  .morph {
    color: #8d0097;
  }
</style>
