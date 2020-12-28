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
  <template v-if="showStrongsSeparately">
    <template v-if="(showStrongs && lemma) && (config.showMorphology && morph)">
      <slot/><span class="skip-offset">&nbsp;<a class="strongs" :href="formatLink(lemma)">{{formatName(lemma)}}</a>-<a class="morph" :href="formatLink(morph)">{{formatName(morph)}}</a></span>
    </template>
    <template v-else-if="(showStrongs && lemma) && (!config.showMorphology || !morph)">
      <slot/><span class="skip-offset">&nbsp;<a class="strongs" :href="formatLink(lemma)">{{formatName(lemma)}}</a></span>
    </template>
    <template v-else-if="(!showStrongs || !lemma) && (config.showMorphology && morph)">
      <slot/><span class="skip-offset">&nbsp;<a class="morph" :href="formatLink(morph)">{{formatName(morph)}}</a></span>
    </template>
    <template v-else><slot/></template>
  </template>
  <template v-else>
    <span v-if="(showStrongs && lemma) || (showStrongs && config.showMorphology && morph)"><span class="linkstyle" @click="goToLink(formatLink(lemma, morph))"><slot/></span></span>
    <span v-else><slot/></span>
  </template>
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
          return s.match(/([^ :]+:)[HG0 ]*([^:]+) *$/)[2].trim()
        }).join(",")
    }
    function formatLink(first, second) {
      const linkBodies = [];
      if(first) {
        linkBodies.push(prep(first).map(s => s.trim().replace(/ /g, "_").replace(/:/g, "=")).join("&"))
      }
      if(second) {
        linkBodies.push(prep(second).map(s => s.trim().replace(/ /g, "_").replace(/:/g, "=")).join("&"))
      }
      // Link format:
      // ab-w://?robinson=x&strong=y&strong=z, x and y have ' ' replaced to '_'.
      return "ab-w://?" + linkBodies.join("&")
    }
    const common = useCommon();
    function goToLink(url) {
        window.location.assign(url);
    }
    return {formatLink, formatName, goToLink, ...common};
  },
  computed: {
    showStrongs() {
      return this.config.strongsMode > 0
    },
    showStrongsSeparately() {
      return this.config.strongsMode === 2
    },
  }
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
    /*
    font-size: 50%;
    vertical-align: super;
    */
  }
</style>
