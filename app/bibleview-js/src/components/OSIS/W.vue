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
      <slot/><span class="base skip-offset">&nbsp;<a :class="{isHighlighted}" class="strongs highlight-transition" :href="formatLink(lemma)" @click.prevent="goToLink($event, formatLink(lemma))">{{formatName(lemma)}}</a>/<a :class="{isHighlighted}" class="morph highlight-transition" :href="formatLink(morph)" @click.prevent="goToLink($event, formatLink(morph))">{{formatName(morph)}}</a></span>
    </template>
    <template v-else-if="(showStrongs && lemma) && (!config.showMorphology || !morph)">
      <slot/><span class="base skip-offset">&nbsp;<a class="strongs highlight-transition" :class="{isHighlighted}" :href="formatLink(lemma)" @click.prevent="goToLink($event, formatLink(lemma))">{{formatName(lemma)}}</a></span>
    </template>
    <template v-else-if="(!showStrongs || !lemma) && (config.showMorphology && morph)">
      <slot/><span class="base skip-offset">&nbsp;<a class="morph highlight-transition" :href="formatLink(morph)" :class="{isHighlighted}" @click.prevent="goToLink($event, formatLink(morph))">{{formatName(morph)}}</a></span>
    </template>
    <template v-else><slot/></template>
  </template>
  <template v-else>
    <span v-if="(showStrongs && lemma) || (showStrongs && config.showMorphology && morph)" :class="{isHighlighted}"  class="highlight-transition link-style" @click="goToLink($event, formatLink(lemma, morph))"><slot/></span>
    <span v-else><slot/></span>
  </template>
</template>

<script>
import {checkUnsupportedProps, strongsModes, useCommon} from "@/composables";
import {addEventFunction, EventPriorities} from "@/utils";
import {computed, ref} from "@vue/reactivity";
import {inject} from "@vue/runtime-core";

export default {
  name: "W",
  props: {
    lemma: {type: String, default: null}, // example: strong:H8064 lemma.TR:XXXXX
    morph: {type: String, default: null}, // example: strongMorph:TH8792
    src: {type: String, default: null},
    n: {type: String, default: null},
    type: {type: String, default: null},
    subType: {type: String, default: null},
  },
  setup(props) {
    checkUnsupportedProps(props, "n")
    checkUnsupportedProps(props, "src")
    checkUnsupportedProps(props, "type", ["x-split"])
    checkUnsupportedProps(props, "subType")
    const {strings, config, ...common} = useCommon();
    const isHighlighted = ref(false);
    const {addCustom, resetHighlights} = inject("verseHighlight");
    function prep(string) {
      let remainingString = string;
      const res = []
      do {
        const match = remainingString.match(/([^ :]+:)([^:]+)$/)
        if(!match) return res;
        const s = match[0]
        res.push(s);
        remainingString = remainingString.slice(0, remainingString.length - s.length)
      } while(remainingString.trim().length > 0)
      return res;
    }
    function formatName(string) {
      return prep(string).filter(s => !s.startsWith("lemma.TR:")).map(s => s.match(/([^ :]+:)[HG0 ]*([^:]+) *$/)[2].trim()).join(",")
    }
    function formatLink(first, second) {
      const linkBodies = [];

      function toArgs(string) {
        return prep(string).map(s => s.trim().replace(/ /g, "_").replace(/:/g, "=")).join("&");
      }

      if(first) {
        linkBodies.push(toArgs(first))
      }
      if(second) {
        linkBodies.push(toArgs(second))
      }
      // Link format:
      // ab-w://?robinson=x&strong=y&strong=z, x and y have ' ' replaced to '_'.
      return "ab-w://?" + linkBodies.join("&")
    }
    function goToLink(event, url) {
      const priority = showStrongsSeparately.value ? EventPriorities.STRONGS_LINK: EventPriorities.STRONGS_DOTTED;
      addEventFunction(event, () => {
        window.location.assign(url)
        resetHighlights();
        isHighlighted.value = true;
        addCustom(() => isHighlighted.value = false);
      }, {priority, icon: "custom-morph", title: strings.strongsAndMorph, dottedStrongs: !showStrongsSeparately.value});
    }
    const exportMode = inject("exportMode", ref(false));
    const showStrongs = computed(() => !exportMode.value && config.strongsMode !== strongsModes.off);
    const showStrongsSeparately = computed(() => !exportMode.value && config.strongsMode === strongsModes.links);

    return {formatLink, formatName, isHighlighted, goToLink, config, strings, showStrongs, showStrongsSeparately, ...common};
  },
}
</script>

<style scoped lang="scss">
@import "~@/common.scss";
  .link-style {
    text-decoration: underline dotted;
    [lang=he],[lang=hbo] & {
      text-decoration-style: solid;
      text-decoration-color: hsla(var(--text-color-h), var(--text-color-s), var(--text-color-l), 0.5);
    }
  }
.base {
  font-size: 0.6em;
  text-decoration: none;
  color: gray;
}
.strongs, .morph {
  color: coral;
  text-decoration: none;
}
</style>
