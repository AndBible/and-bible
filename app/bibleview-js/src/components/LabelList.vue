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
  <div class="label-list">
    <span @touchstart="labelClicked($event, label)" @click="labelClicked($event, label)" v-for="label in labels" :key="label.id" :style="labelStyle(label)" class="label">{{label.name}}</span>
  </div>
</template>

<script>
import {useCommon} from "@/composables";
import {inject} from "@vue/runtime-core";
import {computed, ref} from "@vue/reactivity";
import {addEventFunction} from "@/utils";

export default {
  props: {
    bookmarkId: {type: Number, required: true},
    handleTouch: {type: Boolean, default: false},
  },
  name: "LabelList",
  setup(props) {
    const {adjustedColor, strings, ...common} = useCommon();
    const ambiguousSelection = ref(null);
    function labelStyle(label) {
      return "background-color: " + adjustedColor(label.color).string() + ";";
    }

    const {bookmarkMap, bookmarkLabels} = inject("globalBookmarks");
    const bookmark = computed(() => bookmarkMap.get(props.bookmarkId));

    const labels = computed(() => {
      return bookmark.value.labels.map(labelId => bookmarkLabels.get(labelId));
    });

    const android = inject("android");

    function assignLabels() {
      if(bookmark.value) {
        android.assignLabels(bookmark.value.id);
      }
    }

    function labelClicked(event, label) {
      if(event.type === "touchstart" && !props.handleTouch) {
        return;
      }
      if(event.type === "click" && props.handleTouch) {
        return
      }
      event.stopPropagation();
      addEventFunction(event, assignLabels, {title: strings.assignLabelsMenuEntry})
      if(label.isRealLabel) {
        addEventFunction(event, () => {
          window.location.assign(`journal://?id=${label.id}`);
        }, {title: strings.jumpToStudyPad});
      }
      ambiguousSelection.value.handle(event);
    }

    return {labelStyle, assignLabels, ambiguousSelection, labelClicked, labels, ...common}
  }
}
</script>

<style scoped>
.label {
  font-weight: bold;
  color: #e8e8e8;
  font-size: 70%;
  border-radius: 6pt;
  padding-left: 4pt;
  padding-right: 4pt;
  margin-right: 2pt;
}
.label-list {
  vertical-align: super;
  line-height: 1.1em;
  display: inline-flex;
  flex-direction: row;
  bottom: 30pt;
  padding: 2pt 2pt 0 0;
}
</style>
