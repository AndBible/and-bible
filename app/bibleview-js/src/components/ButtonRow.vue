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
  <div
      ref="element"
      @touchstart="clicked"
      @click="clicked"
      :style="`left: ${leftPosition}px;`"
      :class="{'edit-buttons': expanded}"
  >
    <div class="between" v-show="expanded">
      <slot/>
      <div v-if="showDragHandle" class="drag-handle journal-button" @click.stop="showHelp" @touchend="expanded=false">
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

<script>
import {FontAwesomeIcon} from "@fortawesome/vue-fontawesome";
import {ref} from "@vue/reactivity";
import {inject, nextTick, watch} from "@vue/runtime-core";
import {useCommon} from "@/composables";
import {eventBus, Events} from "@/eventbus";

let cancel = () => {}

export default {
  name: "ButtonRow",
  props: {
    showDragHandle: {type: Boolean, default: false},
    handleTouch: {type: Boolean, default: false},
  },
  components: {FontAwesomeIcon},
  setup(props) {
    const android = inject("android");
    const {strings, ...common} = useCommon();
    const expanded = ref(false);
    const element = ref(null);
    const leftPosition = ref(0);
    function close() {
      expanded.value = false
    }
    async function clicked(event) {
      if(event.type === "touchstart" && !props.handleTouch) {
        return;
      }
      if(event.type === "click" && props.handleTouch) {
        return
      }
      event.stopPropagation();
      const pos = element.value.offsetLeft;
      expanded.value = !expanded.value;
      const oldWidth = props.handleTouch ? element.value.clientWidth: 0;
      await nextTick();
      const width = element.value.clientWidth;
      leftPosition.value = pos - width + oldWidth;
    }
    watch(expanded, v => {
      if(v) {
        cancel()
        eventBus.on(Events.CLOSE_MODALS, close);
        cancel = close
      } else {
        eventBus.off(Events.CLOSE_MODALS, close);
        if(cancel === close) {
          cancel = () => {}
        }
      }
    })
    function showHelp() {
      android.toast(strings.dragHelp);
    }
    return {expanded, showHelp, strings, clicked, leftPosition, element, ...common};
  }
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
