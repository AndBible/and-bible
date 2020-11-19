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
  <span ref="contentTag">
    <template v-if="config.showStrongsSeparately">
      <template v-if="(config.showStrongs && lemma) && (config.showMorphology && morph)">
        <slot/> <a class="strongs" :href="formatLink(lemma)">{{ formatName(lemma) }}</a>-<a class="morph" :href="formatLink(morph)">{{formatName(morph)}}</a>
      </template>
      <template v-else-if="(config.showStrongs && lemma) && (!config.showMorphology || !morph)">
        <slot/> <a class="strongs" :href="formatLink(lemma)">{{formatName(lemma)}}</a>
      </template>
      <template v-else-if="(!config.showStrongs || !lemma) && (config.showMorphology && morph)">
        <slot/> <a class="morph" :href="formatLink(morph)">{{formatName(morph)}}</a>
      </template>
      <template v-else><slot/></template>
    </template>
    <template v-else>
      <span v-if="(config.showStrongs && lemma) || (config.showMorphology && morph)"><a class="linkstyle" :href="formatLink(lemma, morph)"><slot/></a></span>
      <span v-else ref="contentTag"><slot/></span>
    </template>
  </span>
</template>

<script>
import {useCommon} from "@/composables";

export default {
  name: "W",
  props: {
    lemma: {type: String, default: null}, // strong:H8064
    morph: {type: String, default: null}, // strongMorph:TH8792
  },
  setup() {
    function prep(string) {
      let remainingString = string;
      const res = []
      do {
        const s = remainingString.match(/([^ :]+:)([^:]+)$/)[0]
        res.push(s);
        remainingString = remainingString.slice(0, remainingString.length - s.length)
      } while(remainingString.trim().length > 0)
      return res;
    }
    function formatName(string) {
        return prep(string).map(s => {
          return s.match(/([^ :]+:)([^:]+)$/)[2]
        }).join(", ")
    }
    function formatLink(first, second) {
      const linkBodies = [];
      if(first) {
        linkBodies.push(prep(first).map(s => s.trim().replace(/ /g, "_").replace(/:/g, "=")).join("&"))
      }
      if(second) {
        linkBodies.push(prep(second).map(s => s.trim().replace(/ /g, "_").replaceAll(/:/g, "=")).join("&"))
      }
      // Link format:
      // andbible://?robinson=x&strong=y&strong=z, x and y have ' ' replaced to '_'.
      return "andbible://?" + linkBodies.join("&")
    }
    const common = useCommon();
    return {formatLink, formatName, ...common};
  },
}
</script>

<style scoped>
  .linkstyle {
    color: black;
    text-decoration: underline dotted;
  }
  .strongs {
    color: #4b9700;
  }
  .morph {
    color: #8d0097;
  }
</style>
