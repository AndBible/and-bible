<!--
  - Copyright (c) 2021 Martin Denham, Tuomas Airaksinen and the And Bible contributors.
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
  <BookmarkLabelActions :bookmark-id="bookmarkId" ref="actions"/>

  <div class="label-list" :class="{singleLine}">
    <div
      @touchstart="labelClicked($event, label)"
      @click="labelClicked($event, label)"
      v-for="label in labels"
      :key="label.id"
      :style="labelStyle(label)"
      class="label"
      :class="{notAssigned: !isAssigned(label.id)}"
    >
      <span v-if="isPrimary(label)" class="icon"><FontAwesomeIcon icon="bookmark"/></span>
      {{label.name}}
    </div>
    <div style="width: 30px;"/>
  </div>
</template>

<script>
import {useCommon} from "@/composables";
import {inject, watch} from "vue";
import {computed, ref} from "vue";
import {addAll, clickWaiter, removeAll} from "@/utils";
import {FontAwesomeIcon} from "@fortawesome/vue-fontawesome";
import {sortBy} from "lodash";

export default {
  props: {
    bookmarkId: {type: Number, required: true},
    handleTouch: {type: Boolean, default: false},
    disableLinks: {type: Boolean, default: false},
    favourites: {type: Boolean, default: false},
    frequent: {type: Boolean, default: false},
    recent: {type: Boolean, default: false},
    inBookmark: {type: Boolean, default: false},
    onlyAssign: {type: Boolean, default: false},
    singleLine: {type: Boolean, default: false},
  },
  emits: ["has-entries"],
  components: {FontAwesomeIcon},
  name: "LabelList",
  setup(props, {emit}) {
    const {adjustedColor, ...common} = useCommon();
    const appSettings = inject("appSettings");
    const android = inject("android");
    const actions = ref(null);

    function labelStyle(label) {
      const color = adjustedColor(label.color);
      if (isAssigned(label.id)) {
        const textColor = color.isLight() ? "var(--label-text-black)": "var(--label-text-white)";
        return `background-color: ${color.string()}; color: ${textColor};`;
      } else {
        return `border-color: ${color.string()};`;
      }
    }

    const {bookmarkMap, bookmarkLabels} = inject("globalBookmarks");
    const bookmark = computed(() => bookmarkMap.get(props.bookmarkId));

    function isAssigned(labelId) {
      return bookmark.value.labels.includes(labelId);
    }

    function isPrimary(label) {
      return label.id === bookmark.value.primaryLabelId;
    }

    const labels = computed(() => {
      if(!bookmark.value) return [];
      const shown = new Set();
      const earlier = new Set();
      if(props.inBookmark) {
        addAll(shown, ...bookmark.value.labels);
      }
      addAll(earlier, ...bookmark.value.labels);
      if(props.favourites) {
        addAll(shown, ...appSettings.favouriteLabels);
        removeAll(shown, ...earlier);
      }
      addAll(earlier, ...appSettings.favouriteLabels);
      if(props.recent) {
        addAll(shown, ...appSettings.recentLabels);
        removeAll(shown, ...earlier);
      }
      addAll(earlier, ...appSettings.recentLabels);
      if(props.frequent) {
        addAll(shown, ...appSettings.frequentLabels);
        removeAll(shown, ...earlier);
      }
      //addAll(earlier, ...appSettings.frequentLabels);
      // TODO: add frequent
      return sortBy(Array.from(shown).map(labelId => bookmarkLabels.get(labelId)).filter(v => v), ["name"]);
    });

    watch(labels, v => {
      emit("has-entries", v.length > 0);
    }, {immediate: true});

    function assignLabels() {
      if(bookmark.value) {
        android.assignLabels(bookmark.value.id);
      }
    }

    const {waitForClick} = clickWaiter(props.handleTouch);

    const locateTop = inject("locateTop", ref(true));

    async function labelClicked(event, label) {
      if(props.disableLinks) return;
      if(!await waitForClick(event)) return;

      if(!isAssigned(label.id)) {
        android.toggleBookmarkLabel(bookmark.value.id, label.id);
      } else if(!props.onlyAssign) {
        actions.value.showActions({locateTop: locateTop.value})
      } else {
        if(isAssigned(label.id) && !isPrimary(label)) {
          android.setAsPrimaryLabel(bookmark.value.id, label.id);
        } else {
          android.toggleBookmarkLabel(bookmark.value.id, label.id);
        }
      }
    }

    function openActions() {
      actions.value.showActions()
    }

    return {
      labelStyle, assignLabels, actions, labelClicked, labels, isPrimary,
      isAssigned, openActions, ...common
    }
  }
}
</script>

<style scoped lang="scss">
@import "~@/common.scss";
.icon {
  font-size: 10px;
}
.label {
  position: relative;
  top: -2px;
  margin-top: 3px;
  margin-right: 3px;
  font-weight: normal;
  color: #e8e8e8;
  font-size: 11px;
  border-radius: 6pt;
  padding-left: 4pt;
  padding-right: 4pt;
  border-width: 2px;
  border-style: solid;
  text-overflow: ellipsis;
  white-space: nowrap;
  overflow: hidden;
  max-width: 150px;
  .night & {
    background-color: black;
    color: #bbbbbb;
  }
  &.notAssigned {
    border-style: solid;

    background-color: white;
    color: black;
    .night & {
      background-color: black;
      color: #bbbbbb;
    }

  }
  border-color: rgba(0, 0, 0, 0);
}
.label-list {
  line-height: 0.9em;
  display: inline-flex;
  flex-wrap: wrap;
  &.singleLine {
    flex-wrap: nowrap;
  }
}
</style>
