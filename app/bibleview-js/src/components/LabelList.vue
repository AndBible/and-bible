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
  <AmbiguousSelection blocking ref="ambiguousSelection"/>
  <div class="label-list"
       :class="{favourites}"
  >
    <FontAwesomeIcon v-if="favourites" icon="heart"/>
    <div
      @touchstart="labelClicked($event, label)"
      @click="labelClicked($event, label)"
      v-for="label in labels"
      :key="label.id"
      :style="labelStyle(label)"
      class="label"
    >
      <span v-if="!favourites && isPrimary(label)" class="icon"><FontAwesomeIcon icon="bookmark"/></span>
      {{label.name}}
    </div>
  </div>
</template>

<script>
import {useCommon} from "@/composables";
import {inject} from "@vue/runtime-core";
import {computed, ref} from "@vue/reactivity";
import {addEventFunction} from "@/utils";
import {FontAwesomeIcon} from "@fortawesome/vue-fontawesome";

export default {
  props: {
    bookmarkId: {type: Number, required: true},
    handleTouch: {type: Boolean, default: false},
    disableLinks: {type: Boolean, default: false},
    favourites: {type: Boolean, default: false},
  },
  components: {FontAwesomeIcon},
  name: "LabelList",
  setup(props) {
    const {adjustedColor, strings, ...common} = useCommon();
    const ambiguousSelection = ref(null);
    const appSettings = inject("appSettings");

    function labelStyle(label) {
      const color = adjustedColor(label.color).string();
      if(props.favourites && !bookmark.value.labels.includes(label.id)) {
        return `border-color: ${color};`;
      } else {
        return `background-color: ${color};`;
      }
    }

    const {bookmarkMap, bookmarkLabels} = inject("globalBookmarks");
    const bookmark = computed(() => bookmarkMap.get(props.bookmarkId));

    const labels = computed(() => {
      if(props.favourites) {
        return appSettings.favouriteLabels.map(labelId => bookmarkLabels.get(labelId));
      } else {
        return bookmark.value.labels.map(labelId => bookmarkLabels.get(labelId));
      }
    });

    const android = inject("android");

    function assignLabels() {
      if(bookmark.value) {
        android.assignLabels(bookmark.value.id);
      }
    }

    function labelClicked(event, label) {
      if(props.disableLinks) return;
      if(event.type === "touchstart" && !props.handleTouch) {
        return;
      }
      if(event.type === "click" && props.handleTouch) {
        return
      }
      event.stopPropagation();
      if(props.favourites) {
        android.toggleBookmarkLabel(bookmark.value.id, label.id);
      } else {
        if (bookmark.value.primaryLabelId !== label.id) {
          android.setAsPrimaryLabel(bookmark.value.id, label.id);
        } else {
          addEventFunction(event, assignLabels, {title: strings.assignLabelsMenuEntry})
          if (label.isRealLabel) {
            addEventFunction(event, () => {
              window.location.assign(`journal://?id=${label.id}`);
            }, {title: strings.jumpToStudyPad});
            if (bookmark.value.primaryLabelId !== label.id) {
              addEventFunction(event, () => {
                android.setAsPrimaryLabel(bookmark.value.id, label.id);
              }, {title: strings.setAsPrimaryLabel});
            }
          }
          ambiguousSelection.value.handle(event);
        }
      }
    }
    function isPrimary(label) {
      return label.id === bookmark.value.primaryLabelId;
    }
    return {labelStyle, assignLabels, ambiguousSelection, labelClicked, labels, isPrimary, ...common}
  }
}
</script>

<style scoped lang="scss">
@import "~@/common.scss";
.icon {
  font-size: 10px;
}
.label {
  $padding: 2px;
  height: calc(12px + #{2*$padding});
  padding-top: $padding;
  font-weight: normal;
  color: #e8e8e8;
  font-size: 11px;
  border-radius: 6pt;
  padding-left: 4pt;
  padding-right: 4pt;
  margin-right: 2pt;
  .favourites & {
    border-style: solid;
    border-width: 2px;
  }
}
.label-list {
  line-height: 1em;
  display: inline-flex;
  flex-wrap: wrap;
//  @extend .superscript;
//  font-size: 100%;
//  line-height: 1.1em;
//  display: inline-flex;
//  flex-direction: row;
//  bottom: 30pt;
//  padding: 2pt 2pt 0 0;
}
</style>
