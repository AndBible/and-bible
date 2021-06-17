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
  <div @click="ambiguousSelection.handle" :class="{night: appSettings.nightMode}" :style="topStyle">
    <div class="background" :style="backgroundStyle"/>
    <div :style="`height:${calculatedConfig.topOffset}px`"/>
    <div :style="modalStyle" id="modals"/>
    <template v-if="mounted">
      <BookmarkModal/>
      <AmbiguousSelection ref="ambiguousSelection" @back-clicked="backClicked"/>
    </template>
    <ErrorBox v-if="appSettings.errorBox"/>
    <DevelopmentMode :current-verse="currentVerse" v-if="config.developmentMode"/>
    <div v-if="calculatedConfig.topMargin > 0" class="top-margin" :style="`height: ${calculatedConfig.topOffset}px;`"/>
    <div v-if="appSettings.activeWindow">
      <div class="top-left-corner"/>
      <div class="top-right-corner"/>
      <div class="bottom-left-corner"/>
      <div class="bottom-right-corner"/>
    </div>
    <div id="top"/>
    <div id="content" ref="topElement" :style="contentStyle">
      <div style="position: absolute; top: -5000px;" v-if="documents.length === 0">Invisible element to make fonts load properly</div>
      <Document v-for="document in documents" :key="document.id" :document="document"/>
    </div>
    <div id="bottom"/>
  </div>
</template>
<script>
import Document from "@/components/documents/Document";
import {nextTick, onMounted, onUnmounted, provide, reactive, watch} from "@vue/runtime-core";
import {
  useAddonFonts,
  useConfig,
  useCustomCss,
  useCustomFeatures,
  useFontAwesome, useModal,
  useVerseMap,
  useVerseNotifier
} from "@/composables";
import {testBookmarkLabels, testData} from "@/testdata";
import {computed, ref} from "@vue/reactivity";
import {useInfiniteScroll} from "@/composables/infinite-scroll";
import {useGlobalBookmarks} from "@/composables/bookmarks";
import {emit, Events, setupEventBusListener} from "@/eventbus";
import {useScroll} from "@/composables/scroll";
import {clearLog, useAndroid} from "@/composables/android";
import {setupWindowEventListener} from "@/utils";
import ErrorBox from "@/components/ErrorBox";
import BookmarkModal from "@/components/modals/BookmarkModal";
import DevelopmentMode from "@/components/DevelopmentMode";
import Color from "color";
import {useStrings} from "@/composables/strings";
import {DocumentTypes} from "@/constants";

export default {
  name: "BibleView",
  components: {Document, ErrorBox, BookmarkModal, DevelopmentMode},
  setup() {
    useAddonFonts();
    useFontAwesome();
    const documents = reactive([]);
    const documentType = computed(() => {
      if(documents.length < 1) {
        return DocumentTypes.NONE;
      }
      return documents[0].type;
    });
    const {config, appSettings, calculatedConfig} = useConfig(documentType);
    const strings = useStrings();
    window.bibleViewDebug.documents = documents;
    const topElement = ref(null);
    const documentPromise = ref(null);
    const verseMap = useVerseMap();
    provide("verseMap", verseMap);
    const scroll = useScroll(config, appSettings, calculatedConfig, verseMap, documentPromise);
    const {scrollToId} = scroll;
    provide("scroll", scroll);
    const globalBookmarks = useGlobalBookmarks(config, documentType);
    const android = useAndroid(globalBookmarks, config);

    const modal = useModal(android);
    provide("modal", modal);

    const mounted = ref(false);
    onMounted(() => mounted.value = true)
    onUnmounted(() => mounted.value = false)

    const {currentVerse} = useVerseNotifier(config, calculatedConfig, mounted, android, topElement, scroll);
    const customCss = useCustomCss();
    provide("customCss", customCss);
    const customFeatures = useCustomFeatures();
    provide("customFeatures", customFeatures);

    useInfiniteScroll(android, documents);

    function addDocuments(...docs) {
      documentPromise.value = document.fonts.ready
        .then(() => nextTick())
        .then(() => documents.push(...docs));
    }

    setupEventBusListener(Events.CONFIG_CHANGED, async (deferred) => {
      const verseBeforeConfigChange = currentVerse.value;
      await deferred.wait();
      scrollToId(`o-${verseBeforeConfigChange}`, {now: true})
    })

    setupEventBusListener(Events.CLEAR_DOCUMENT, function clearDocument() {
      emit(Events.CLOSE_MODALS);
      clearLog();
      globalBookmarks.clearBookmarks();
      documents.splice(0)
    });

    setupEventBusListener(Events.ADD_DOCUMENTS, addDocuments);
    setupWindowEventListener("error", (e) => {
      console.error("Error caught", e.message, `on ${e.filename}:${e.colno}`);
    });

    if(config.developmentMode) {
      console.log("populating test data");
      globalBookmarks.updateBookmarkLabels(...testBookmarkLabels)
      addDocuments(...testData)
    }

    let titlePrefix = ""
    setupEventBusListener(Events.SET_TITLE, function setTitle(title) {
      titlePrefix = title;
    });

    watch(documents, () => {
      if(documents.length > 0) {
        const id = documents[0].id;
        const type = documents[0].type;
        document.title = `${titlePrefix}/${type}/${id} (${process.env.NODE_ENV})`
      }
    })

    provide("globalBookmarks", globalBookmarks);
    provide("config", config);
    provide("appSettings", appSettings);
    provide("calculatedConfig", calculatedConfig);

    provide("strings", strings);
    provide("android", android);

    const ambiguousSelection = ref(null);

    const backgroundStyle = computed(() => {
      const colorInt = appSettings.nightMode ? config.colors.nightBackground: config.colors.dayBackground;
      if(colorInt === null) return "";
      const backgroundColor = Color(colorInt).hsl().string();
      return `
            background-color: ${backgroundColor};
        `;
    });

    const contentStyle = computed(() => {
      const textColor = Color(appSettings.nightMode ? config.colors.nightTextColor: config.colors.dayTextColor);
      const backgroundColor = Color(appSettings.nightMode ? config.colors.nightBackground: config.colors.dayBackground);

      let style = `
          max-width: ${config.marginSize.maxWidth}mm;
          margin-left: auto;
          margin-right: auto;
          color: ${textColor.hsl().string()};
          hyphens: ${config.hyphenation ? "auto": "none"};
          line-spacing: ${config.lineSpacing / 10}em;
          line-height: ${config.lineSpacing / 10}em;
          text-align: ${config.justifyText ? "justify" : "start"};
          font-family: ${config.fontFamily};
          font-size: ${config.fontSize}px;
          --font-size: ${config.fontSize}px;
          --background-color: ${backgroundColor.hsl().string()};
          `;
      if(config.marginSize.marginLeft || config.marginSize.marginRight) {
        style += `
            margin-left: ${config.marginSize.marginLeft}mm;
            margin-right: ${config.marginSize.marginRight}mm;
          `;
      }
      return style;
    });

    const modalStyle = computed(() => {
      return `
          --bottom-offset: ${appSettings.bottomOffset}px;
          --top-offset: ${appSettings.topOffset}px;
          --font-size:${config.fontSize}px;
          --font-family:${config.fontFamily};`
    });

    const topStyle = computed(() => {
      const noiseOpacity = appSettings.nightMode ? config.colors.nightNoise : config.colors.dayNoise;
      return `
          --bottom-offset: ${appSettings.bottomOffset}px;
          --top-offset: ${appSettings.topOffset}px;
          --noise-opacity: ${noiseOpacity/100};
          `;
    });

    setupEventBusListener(Events.BOOKMARK_CLICKED, () => {
      verseMap.resetHighlights();
    })

    function backClicked() {
      emit(Events.CLOSE_MODALS)
      verseMap.resetHighlights();
    }

    return {
      makeBookmarkFromSelection: globalBookmarks.makeBookmarkFromSelection,
      updateBookmarks: globalBookmarks.updateBookmarks, ambiguousSelection,
      config, strings, documents, topElement, currentVerse, mounted, emit, Events,
      contentStyle, backgroundStyle, modalStyle, topStyle, calculatedConfig, appSettings, backClicked,
    };
  },
}
</script>
<style lang="scss" scoped>
.background {
  z-index: -3;
  position: fixed;
  left: 0;
  top: 0;
  right: 0;
  bottom: 0;
  opacity: var(--noise-opacity);
  background-image: url("~@/assets/noise.svg");
}

$dayAlpha: 0.07;
$nightAlpha: 0.3;
$borderDistance: 0;

.active-window-corner {
  position: fixed;
  z-index: -1;
  height: 20px;
  width: 20px;
  border-width: 2.5px;
  .night & {
    border-color: rgba(196, 196, 255, 0.8);
  }
  border-color: rgba(0, 0, 255, 0.6);
}

.top-left-corner {
  @extend .active-window-corner;
  top: $borderDistance;
  left: $borderDistance;
  border-top-style: solid;
  border-left-style: solid;
}
.top-right-corner {
  @extend .active-window-corner;
  top: $borderDistance;
  right: $borderDistance;
  border-top-style: solid;
  border-right-style: solid;
}
.bottom-right-corner {
  @extend .active-window-corner;
  bottom: $borderDistance;
  right: $borderDistance;
  border-bottom-style: solid;
  border-right-style: solid;
}
.bottom-left-corner {
  @extend .active-window-corner;
  bottom: $borderDistance;
  left: $borderDistance;
  border-bottom-style: solid;
  border-left-style: solid;
}

.active-window-indicator {
  position: fixed;
  z-index: -1;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  border-style: solid;
  border-width: 15px;
  .night & {
    border-color: rgba(255, 255, 255, $nightAlpha);
  }
  border-color: rgba(0, 0, 0, $dayAlpha);
}

.top-margin {
  position: fixed;
  z-index: -2;
  top: 0;
  left: 0;
  right: 0;
  .night & {
    background-color: rgba(255, 255, 255, 0.15);
  }
  background-color: rgba(0, 0, 0, 0.15);
}

</style>
<style lang="scss">
@import "~@/common.scss";
a {
  color: blue;
  .night & {
    color: #7b7bff;
  }
}

.bookmark-marker {
  @extend .superscript;
  font-size: 50%;
  top: -0.8em;
}

.divider {
  height: 1em;
}

#bottom {
  padding-bottom: 100vh;
}

.modal-action-button {
  font-size: 120%;
  line-height: 0.5em; // make sure this does not increase modal title height
  &.toggled {
    color: #d5d5d5;
  }

  &.right {
    align-self: flex-end;
  }
  background-color: inherit;
  border: none;
  color: white;
  border-radius: 5pt;
  padding: 5pt 5pt;
  margin: 2pt 2pt;
  text-align: center;
  text-decoration: none;
  display: inline-block;
}
</style>
