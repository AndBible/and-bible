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
  <div @click="ambiguousSelection.handle" :class="{night: appSettings.nightMode}" :style="topStyle" :dir="direction">
    <div class="background" :style="backgroundStyle"/>
    <div :style="`height:${calculatedConfig.topOffset}px`"/>
    <div :style="modalStyle" id="modals"/>
    <template v-if="mounted">
      <BookmarkModal/>
      <AmbiguousSelection ref="ambiguousSelection"/>
    </template>
    <ErrorBox v-if="appSettings.errorBox"/>
    <DevelopmentMode :current-verse="currentVerse" v-if="config.developmentMode"/>
    <div v-if="calculatedConfig.topMargin > 0" class="top-margin" :style="`height: ${calculatedConfig.topOffset}px;`"/>
    <div v-if="appSettings.hasActiveIndicator">
      <div class="top-left-corner"/>
      <div class="top-right-corner"/>
      <div class="bottom-left-corner"/>
      <div class="bottom-right-corner"/>
    </div>
    <div id="top"/>
    <div class="loading" v-if="isLoading"><div class="lds-ring"><div></div><div></div><div></div><div></div></div></div>
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
  useFontAwesome, useModal, useSharing,
  useVerseHighlight,
  useVerseNotifier
} from "@/composables";
import {testBookmarkLabels, testData} from "@/testdata";
import {computed, ref} from "@vue/reactivity";
import {useInfiniteScroll} from "@/composables/infinite-scroll";
import {useGlobalBookmarks} from "@/composables/bookmarks";
import {emit, Events, setupEventBusListener} from "@/eventbus";
import {useScroll} from "@/composables/scroll";
import {clearLog, useAndroid} from "@/composables/android";
import {setupWindowEventListener, waitNextAnimationFrame} from "@/utils";
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
    const verseHighlight = useVerseHighlight();
    provide("verseHighlight", verseHighlight);
    const {resetHighlights} = verseHighlight;
    const scroll = useScroll(config, appSettings, calculatedConfig, verseHighlight, documentPromise);
    const {doScrolling, scrollToId} = scroll;
    provide("scroll", scroll);
    const globalBookmarks = useGlobalBookmarks(config);
    const android = useAndroid(globalBookmarks, config);

    const modal = useModal(android);
    provide("modal", modal);

    let footNoteCount = 0;

    function getFootNoteCount() {
      return footNoteCount ++;
    }

    provide("footNoteCount", {getFootNoteCount});

    const {closeModals} = modal;

    const mounted = ref(false);
    onMounted(() => mounted.value = true)
    onUnmounted(() => mounted.value = false)

    const {currentVerse} = useVerseNotifier(config, calculatedConfig, mounted, android, topElement, scroll);
    const customCss = useCustomCss();
    provide("customCss", customCss);
    const customFeatures = useCustomFeatures(android);
    provide("customFeatures", customFeatures);

    useInfiniteScroll(android, documents);
    const loadingCount = ref(0);

    function addDocuments(...docs) {
      async function doAddDocuments() {
        loadingCount.value ++;
        await document.fonts.ready;
        await nextTick();
        // 2 animation frames seem to make sure that loading indicator is visible.
        await waitNextAnimationFrame();
        await waitNextAnimationFrame();
        documents.push(...docs);
        await nextTick();
        loadingCount.value --;
      }
      documentPromise.value = doAddDocuments()
    }

    setupEventBusListener(Events.CONFIG_CHANGED, async (deferred) => {
      const verseBeforeConfigChange = currentVerse.value;
      await deferred.wait();
      scrollToId(`o-${verseBeforeConfigChange}`, {now: true})
    })

    setupEventBusListener(Events.CLEAR_DOCUMENT, function clearDocument() {
      footNoteCount = 0;
      resetHighlights();
      closeModals();
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
      const backgroundColor = Color(appSettings.nightMode ? config.colors.nightBackground: config.colors.dayBackground);
      const noiseOpacity = appSettings.nightMode ? config.colors.nightNoise : config.colors.dayNoise;
      const textColor = Color(appSettings.nightMode ? config.colors.nightTextColor : config.colors.dayTextColor);
      const verseNumberColor = appSettings.nightMode ?
        textColor.fade(0.2).hsl().string():
        textColor.fade(0.5).hsl().string();
      return `
          --bottom-offset: ${appSettings.bottomOffset}px;
          --top-offset: ${appSettings.topOffset}px;
          --noise-opacity: ${noiseOpacity/100};
          --text-max-width: ${config.marginSize.maxWidth}mm;
          --text-color: ${textColor.hsl().string()};
          --text-color-h: ${textColor.hsl().color[0]};
          --text-color-s: ${textColor.hsl().color[1]}%;
          --text-color-l: ${textColor.hsl().color[2]}%;
          --verse-number-color: ${verseNumberColor};
          --background-color: ${backgroundColor.hsl().string()};
          `;
    });

    setupEventBusListener(Events.ADJUST_LOADING_COUNT, a => {
      loadingCount.value += a;
    });

    const isLoading = computed(() => documents.length === 0 || loadingCount.value > 0);

    function scrollUpDown(up = false) {
      const amount = window.innerHeight / 2;
      doScrolling(window.pageYOffset + (up ? -amount: amount), 500)
    }

    setupEventBusListener(Events.SCROLL_DOWN, () => scrollUpDown());
    setupEventBusListener(Events.SCROLL_UP, () => scrollUpDown(true));

    useSharing({topElement, android});

    return {
      direction: computed(() => appSettings.rightToLeft ? "rtl": "ltr"),
      makeBookmarkFromSelection: globalBookmarks.makeBookmarkFromSelection,
      updateBookmarks: globalBookmarks.updateBookmarks, ambiguousSelection,
      config, strings, documents, topElement, currentVerse, mounted, emit, Events, isLoading,
      contentStyle, backgroundStyle, modalStyle, topStyle, calculatedConfig, appSettings,
    };
  },
}
</script>
<style lang="scss" scoped>
@import "~@/common.scss";

$ring-size: 35px;
$ring-thickness: $ring-size/12;

.loading {
  position: fixed;
  left: calc(50% - #{$ring-size}/2);
  top: calc(50% - #{$ring-size}/2);
}

$ring-color: $button-grey;

.lds-ring {
  display: inline-block;
  position: relative;
  width: $ring-size;
  height: $ring-size;
  & div {
    box-sizing: border-box;
    display: block;
    position: absolute;
    width: $ring-size;
    height: $ring-size;
    margin: 8px;
    border: $ring-thickness solid $ring-color;
    border-radius: 50%;
    animation: lds-ring 1.2s cubic-bezier(0.5, 0, 0.5, 1) infinite;
    border-color: $ring-color transparent transparent transparent;
    &:nth-child(1) {
      animation-delay: -0.45s;
    }
    &:nth-child(2) {
      animation-delay: -0.3s;
    }
    &:nth-child(3) {
      animation-delay: -0.15s;
    }
  }
}

@keyframes lds-ring {
  0% {
    transform: rotate(0deg);
  }
  100% {
    transform: rotate(360deg);
  }
}

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
