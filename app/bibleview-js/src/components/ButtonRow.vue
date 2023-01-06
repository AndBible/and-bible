<!--
  - Copyright (c) 2021-2022 Martin Denham, Tuomas Airaksinen and the AndBible contributors.
  -
  - This file is part of AndBible: Bible Study (http://github.com/AndBible/and-bible).
  -
  - AndBible is free software: you can redistribute it and/or modify it under the
  - terms of the GNU General Public License as published by the Free Software Foundation,
  - either version 3 of the License, or (at your option) any later version.
  -
  - AndBible is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
  - without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
  - See the GNU General Public License for more details.
  -
  - You should have received a copy of the GNU General Public License along with AndBible.
  - If not, see http://www.gnu.org/licenses/.
  -->

<template>
  <div
    ref="element"
    @touchstart="clicked"
    @click="clicked"
    :class="{'edit-buttons': expanded, 'menu': !handleTouch}"
  >
    <div class="between" v-if="expanded">
      <slot/>
      <div v-if="showDragHandle" class="drag-handle journal-button" @touchstart="dragStart" @touchend="dragEnd">
        <FontAwesomeIcon icon="sort"/>
      </div>
      <div class="journal-button">
        <FontAwesomeIcon icon="ellipsis-h"/>
      </div>
    </div>
    <slot v-if="!expanded" name="menubutton">
      <div class="journal-button">
        <FontAwesomeIcon icon="ellipsis-h"/>
      </div>
    </slot>
  </div>
</template>

<script lang="ts" setup>
import {FontAwesomeIcon} from "@fortawesome/vue-fontawesome";
import {ref, inject, watch} from "vue";
import {useCommon} from "@/composables";
import {eventBus} from "@/eventbus";
import {androidKey} from "@/types/constants";

let cancel = () => {}

const props = withDefaults(
  defineProps<{showDragHandle: boolean, handleTouch: boolean}>(),
  {showDragHandle: false, handleTouch: false}
)

const android = inject(androidKey)!;
const {strings} = useCommon();
const expanded = ref(false);
const element = ref(null);
function close() {
  expanded.value = false
}
async function clicked(event: MouseEvent|TouchEvent) {
  if(event.type === "touchstart" && !props.handleTouch) {
    return;
  }
  if(event.type === "click" && props.handleTouch) {
    return
  }
  event.stopPropagation();
  expanded.value = !expanded.value;
}

watch(expanded, v => {
  if(v) {
    cancel()
    eventBus.on("back_clicked", close);
    cancel = close
  } else {
    eventBus.off("back_clicked", close);
    if(cancel === close) {
      cancel = () => {}
    }
  }
})
function showHelp() {
  android.toast(strings.dragHelp);
}

let startTime = 0;
function dragEnd() {
  const delta = Date.now() - startTime;
  if(delta > 200) {
    expanded.value = false;
  } else {
    showHelp();
  }
}

function dragStart() {
  startTime = Date.now();
}
</script>

<style scoped lang="scss">
@import "~@/common.scss";

.between {
  display: flex;
}
.edit-buttons {
  background: var(--background-color);
  border-style: solid;
  border-color: rgba(0, 0, 0, 0.3);
  border-width: 1pt;
  border-radius: 10pt;
  position: absolute;
  //right: 0;
  display: flex;
  justify-content: flex-end;
  z-index: 1;
  top: 0;
  opacity: 0.8;
  .night & {
    border-color: rgba(255, 255, 255, 0.6);
  }
}
</style>
