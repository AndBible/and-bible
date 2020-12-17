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
  <div :style="`height:${config.toolbarOffset}px`"/>
  <div id="notes"/>
  <div v-if="config.developmentMode"
      :style="`position: fixed; top:0; width:100%;  background-color: rgba(100, 255, 100, 0.7);
               height:${config.toolbarOffset}px`"
  >
     Current verse: {{currentVerse}}
  </div>
  <div v-if="config.developmentMode" class="highlightButton"><span v-show="false" @click="highLight">Highlight!</span> <span @mouseenter="makeBookmarkFromSelection">Get selection!</span></div>
  <div id="top" ref="topElement" :style="styleConfig">
    <div v-for="({contents}, index) in osisFragments" :key="index">
      <template v-for="({xml, key, ordinalRange}, idx) in contents" :key="key">
        <div :class="`frag frag-${key}`">
          <OsisFragment :xml="xml" :fragment-key="`${key}`" :ordinal-range="ordinalRange"/>
        </div>
        <div v-if="contents.length > 0 && idx < contents.length" class="divider" />
      </template>
    </div>
  </div>
  <div id="bottom"/>
</template>
<script>
  import OsisFragment from "@/components/OsisFragment";
  import {provide, reactive, watch} from "@vue/runtime-core";
  import {useConfig, useStrings, useVerseNotifier} from "@/composables";
  import {testData} from "@/testdata";
  import {ref} from "@vue/reactivity";
  import {useInfiniteScroll} from "@/code/infinite-scroll";
  import {useGlobalBookmarks} from "@/composables/bookmarks";
  import {findElemWithOsisID} from "@/dom";
  import {setupWindowEventListener} from "@/utils";
  import {Events, setupEventBusListener} from "@/eventbus";
  import {useScroll} from "@/code/scroll";
  import {useAndroid} from "@/composables/android";

  export default {
    name: "BibleView",
    components: {OsisFragment},
    setup() {
      const {config} = useConfig();
      const strings = useStrings();
      const android = useAndroid();
      const osisFragments = reactive([]);
      const topElement = ref(null);
      useScroll(config);
      useInfiniteScroll(config, android, osisFragments);
      const {currentVerse} = useVerseNotifier(config, android, topElement);
      const globalBookmarks = useGlobalBookmarks(android);

      watch(() => osisFragments, () => {
        for(const frag of osisFragments) {
            globalBookmarks.updateBookmarks(...frag.bookmarks);
            globalBookmarks.updateBookmarkLabels(...frag.bookmarkLabels);
        }
      }, {deep: true});

      function replaceOsis(...s) {
        osisFragments.splice(0)
        osisFragments.push(...s)
      }

      setupEventBusListener(Events.REPLACE_OSIS, replaceOsis);

      if(process.env.NODE_ENV === "development") {
        console.log("populating test data");
        replaceOsis(...testData)
      }

      setupEventBusListener(Events.SET_TITLE,
          (title) => document.title = `${title} (${process.env.NODE_ENV})`
      );

      setupWindowEventListener("mouseover", (ev) => {
        const {x, y} = ev;
        const element = document.elementFromPoint(x, y)
        //console.log("elem", element, element.parentElement, element.parentElement.parentElement);
        //const osisElem = element.closest(".osis");
        const osisElem = findElemWithOsisID(element);
        if(osisElem) {
          //console.log(osisElem.dataset.osisID);
        }

        ev.preventDefault()
        ev.stopPropagation()
      });

      provide("globalBookmarks", globalBookmarks);
      provide("config", config);
      provide("strings", strings);
      provide("android", android);

      return {
        makeBookmarkFromSelection: globalBookmarks.makeBookmarkFromSelection,
        updateBookmarks: globalBookmarks.updateBookmarks,
        config, strings, osisFragments, topElement, currentVerse
      };
    },
    computed: {
      styleConfig({config}) {
        let style = `
          max-width: ${config.marginSize.maxWidth};
          color: ${config.textColor};
          hyphens: ${config.hyphenation ? "auto": "none"};
          noise-opacity: ${config.noiseOpacity/100};
          line-spacing: ${config.lineSpacing / 10}em;
          line-height: ${config.lineSpacing / 10}em;
          text-align: ${config.justifyText ? "justify" : "start"};
          `;
        if(config.marginSize.marginLeft || config.marginSize.marginRight) {
          style += `
            margin-left: ${config.marginSize.marginLeft}mm;
            margin-right: ${config.marginSize.marginRight}mm;
          `;
        }
        return style;
      }
    },
  }
</script>
<style>

.highlightButton {
  position: fixed;
  bottom: 0;
  left: 0;
  padding: 2em;
  background: yellow;
}
.inlineDiv {
  display: inline;
}

.divider {
  height: 1em;
}

#bottom {
  padding-bottom: 100vh;
}
</style>
