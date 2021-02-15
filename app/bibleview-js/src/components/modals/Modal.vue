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
    <div v-if="blocking" @click.stop="$emit('close')" class="modal-backdrop"/>
    <div :class="{blocking}">
      <div ref="modal" @click.stop :class="{'modal-content': true, blocking}"
      >
        <div ref="header" class="modal-header">
          <span class="title">
            <slot name="title"/>
          </span>
        </div>
        <div v-if="ready" class="modal-body">
          <slot/>
        </div>
        <div class="modal-footer">
          <slot name="footer">
            <button class="button" @click="$emit('close')">{{strings.closeModal}}</button>
          </slot>
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
import {draggableElement} from "@/utils";

export default {
  name: "Modal",
  emits: ["close"],
  props: {blocking: {type: Boolean, default: false}},
  setup: function (props, {emit: $emit}) {
    const config = inject("config");
    const modal = ref(null);
    const header = ref(null);
    const ready = ref(false);
    onMounted(async () => {
      modal.value.style.top = `calc(${window.scrollY}px + var(--top-offset) + 10pt)`;
      modal.value.style.left = `calc((100% - 80%) / 2)`;
      draggableElement(modal.value, header.value);
      ready.value = true;
    });
    if (!props.blocking) {
      emit(Events.BACK_CLICKED);
      setupEventBusListener(Events.BACK_CLICKED, () => $emit('close'))
    }
    return {config, modal, header, ready, ...useCommon()}
  }
}
</script>

<style scoped lang="scss">
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
  opacity: 0.95;
  z-index: 5;
  .blocking & {
    z-index: 15;
  }
  position: absolute;
  background-color: #fefefe;
  width: 80%;
  padding: 0;
  border: 1px solid #888;
  box-shadow: 0 4px 8px 0 rgba(0,0,0,0.2),0 6px 20px 0 rgba(0,0,0,0.19);
  animation-name: animatetop;
  animation-duration: 0.2s;
  .night & {
    background-color: black;
    color: #bdbdbd;
  }
  border-radius: $border-radius;
}

@keyframes animatetop {
  from {opacity:0}
  to {opacity:1}
}

.modal-header {
  padding: 0.5em;
  background-color: #acacac;
  color: white;
  font-weight: bold;
  border-radius: $border-radius2 $border-radius2 0 0;

  .night & {
    background-color: #454545;
    color: #e2e2e2;
  }
}

.modal-body {
  --max-height: calc(100vh - var(--top-offset) - var(--bottom-offset) - 80pt);
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
