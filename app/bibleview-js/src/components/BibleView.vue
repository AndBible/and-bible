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
  <div v-if="config.developmentMode" class="highlightButton">
    <span @mouseenter="testMakeBookmark">Get selection!</span>
  </div>
  <div id="top" ref="topElement" :style="styleConfig">
    <div v-for="({contents, showTransition}, index) in osisFragments" :key="index">
      <template v-for="(data, idx) in contents" :key="data.key">
        <div :id="`f-${data.key}`" class="fragment">
          <OsisFragment :show-transition="showTransition" :data="data"/>
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
  import {useInfiniteScroll} from "@/composables/infinite-scroll";
  import {useGlobalBookmarks} from "@/composables/bookmarks";
  import {emit, Events, setupEventBusListener} from "@/eventbus";
  import {useScroll} from "@/composables/scroll";
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
      const {scrollToVerse} = useScroll(config);
      useInfiniteScroll(config, android, osisFragments);
      const {currentVerse} = useVerseNotifier(config, android, topElement);
      const globalBookmarks = useGlobalBookmarks(config);

      watch(() => osisFragments, () => {
        for(const frag of osisFragments) {
            globalBookmarks.updateBookmarkLabels(...frag.bookmarkLabels);
            globalBookmarks.updateBookmarks(...frag.bookmarks);
        }
      }, {deep: true});

      function replaceOsis(...s) {
        osisFragments.splice(0)
        osisFragments.push(...s)
      }

      setupEventBusListener(Events.CONFIG_CHANGED, async (deferred) => {
        const verseBeforeConfigChange = currentVerse.value;
        await deferred.wait();
        scrollToVerse(`v-${verseBeforeConfigChange}`, true)
      })

      setupEventBusListener(Events.REPLACE_OSIS, replaceOsis);

      if(process.env.NODE_ENV === "development") {
        console.log("populating test data");
        replaceOsis(...testData)
      }

      setupEventBusListener(Events.SET_TITLE,
          (title) => document.title = `${title} (${process.env.NODE_ENV})`
      );

      provide("globalBookmarks", globalBookmarks);
      provide("config", config);
      provide("strings", strings);
      provide("android", android);

      let lblCount = 0;

      function testMakeBookmark() {
        const selection = android.querySelection()
        if(!selection) return
        const bookmark = {
          id: -lblCount -1,
          ordinalRange: [selection.startOrdinal, selection.endOrdinal],
          offsetRange: [selection.startOffset, selection.endOffset],
          book: selection.bookInitials,
          labels: [-(lblCount++ % 5) - 1]
        }
        emit(Events.ADD_OR_UPDATE_BOOKMARKS, {bookmarks: [bookmark], labels: []})
        emit(Events.REMOVE_RANGES)
      }

      return {
        makeBookmarkFromSelection: globalBookmarks.makeBookmarkFromSelection,
        updateBookmarks: globalBookmarks.updateBookmarks,
        config, strings, osisFragments, topElement, currentVerse, testMakeBookmark
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
