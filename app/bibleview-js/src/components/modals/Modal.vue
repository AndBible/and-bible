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
  <teleport to="#modals">
    <div v-if="blocking" @click.stop="backdropClick" class="modal-backdrop"/>
    <div :class="{blocking}">
      <div ref="modal" @click.stop class="modal-content" :class="{blocking, wide}"
      >
        <div ref="header" class="modal-header">
          <slot name="title-div">
            <div class="title">
              <slot name="title"/>
            </div>
          </slot>
          <slot name="buttons">
            <button class="modal-action-button right" @touchstart.stop @click.stop="$emit('close')">
              <FontAwesomeIcon icon="times"/>
            </button>
          </slot>
        </div>
        <div v-if="ready" class="modal-body">
          <slot/>
        </div>
        <div class="modal-footer">
          <slot name="footer"/>
        </div>
      </div>
    </div>
  </teleport>
</template>
<script>

import {inject, onMounted} from "@vue/runtime-core";
import {useCommon} from "@/composables";
import {Events, emit, setupEventBusListener} from "@/eventbus";
import {ref} from "@vue/reactivity";
import {
  draggableElement,
  isInViewport, setupDocumentEventListener,
  setupWindowEventListener
} from "@/utils";
import {FontAwesomeIcon} from "@fortawesome/vue-fontawesome";
import {throttle} from "lodash";


export default {
  name: "Modal",
  emits: ["close"],
  props: {
    blocking: {type: Boolean, default: false},
    wide: {type: Boolean, default: false}
  },
  components: {FontAwesomeIcon},
  setup: function (props, {emit: $emit}) {
    const config = inject("config");
    const modal = ref(null);
    const header = ref(null);
    const ready = ref(false);

    function resetPosition() {
      modal.value.style.top = `calc(${window.scrollY}px + var(--top-offset) + var(--modal-top))`;
      modal.value.style.left = `var(--modal-left)`;
    }

    const {register} = inject("modal");
    register();

    setupWindowEventListener("resize", resetPosition)
    setupWindowEventListener("scroll", throttle(() => {
      if(!isInViewport(modal.value)) {
        resetPosition()
      }
    }, 50));
    setupDocumentEventListener("keyup", event => {
      if(event.key === "Escape") {
        $emit("close");
      }
    })

    onMounted(async () => {
      resetPosition()
      draggableElement(modal.value, header.value);
      ready.value = true;
    });
    if (!props.blocking) {
      emit(Events.CLOSE_MODALS);
      setupEventBusListener(Events.CLOSE_MODALS, () => $emit('close'))
    }

    function backdropClick(event) {
      console.log("backdrop clicked", event);
      $emit("close");
    }

    return {config, modal, header, ready, backdropClick, ...useCommon()}
  }
}
</script>

<style scoped lang="scss">
@import "~@/common.scss";
.modal-backdrop {
  display: block;
  position: fixed;
  z-index: 10;
  padding-top: 10px;
  left: 0;
  top: 0;
  width: 100%;
  height: 100%;
  background-color: rgba(0,0,0,0.5);
}

$border-radius: 8pt;
$border-radius2: $border-radius - 1.5pt;

.modal-content {
  font-family: sans-serif;
  font-size: 12pt;
  opacity: 0.95;
  z-index: 5;
  .blocking & {
    z-index: 15;
  }
  position: absolute;
  background-color: $modal-content-background-color;
  padding: 0;
  border: 1px solid #888;
  box-shadow: 0 4px 8px 0 rgba(0,0,0,0.2),0 6px 20px 0 rgba(0,0,0,0.19);
  animation-name: animatetop;
  animation-duration: 0.2s;
  .night & {
    background-color: $modal-content-background-color-night;
    color: #bdbdbd;
  }
  border-radius: $border-radius;

  width: 80%;
  --modal-left: calc(20% / 2);
  --modal-top: 30px;

  &.wide {
    width: calc(100% - 60px);
    --modal-left: calc(60px / 2);
    --modal-top: 25px;
  }
}

@keyframes animatetop {
  from {opacity:0}
  to {opacity:1}
}

.title {
  padding-top: 5pt;
  margin-top: 2pt;
}

.modal-header {
  display:flex;
  justify-content: space-between;
  padding: 0.1em;
  padding-left: 0.5em;
  background-color: $modal-header-background-color;
  --header-backround: #{$modal-header-background-color};
  color: white;
  font-weight: bold;
  border-radius: $border-radius2 $border-radius2 0 0;

  .night & {
    background-color: $night-modal-header-background-color;
    --header-backround: #{$night-modal-header-background-color};
    color: #e2e2e2;
  }
}

.modal-body {
  --max-height: calc(100vh - var(--top-offset) - var(--bottom-offset) - 100px);
  //min-height: 60pt;
  padding: 5px 5px;
  margin: 5pt 5pt;
}

.modal-footer {
  border-radius: 0 0 $border-radius2 $border-radius2;
  display: flex;
  flex-direction: row;
  justify-content: space-around;
  padding: 2px 16px;
  background-color: #acacac;
  color: white;
  .night & {
    background-color: #454545;
    color: #bdbdbd;
  }
}

</style>
