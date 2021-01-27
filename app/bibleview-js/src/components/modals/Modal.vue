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
      <div @click.stop :class="{'modal-content': true, blocking}"
      >
        <div @click="$emit('close')" class="modal-header">
          <span class="title">
            <slot name="title"/>
          </span>
        </div>
        <div class="modal-body">
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

import {inject} from "@vue/runtime-core";
import {useCommon} from "@/composables";
import {Events, emit, setupEventBusListener} from "@/eventbus";

export default {
  name: "Modal",
  emits: ["close"],
  props: {blocking: {type: Boolean, default: false}},
  setup(props, {emit: $emit}) {
    const config = inject("config")
    if(!props.blocking) {
      emit(Events.CLOSE_MODAL);
      setupEventBusListener(Events.CLOSE_MODAL, () => $emit('close'))
    }
    return {config, ...useCommon()}
  }
}
</script>

<style scoped lang="scss">
.modal-backdrop {
  display: block;
  position: fixed;
  z-index: 2;
  padding-top: 10px;
  left: 0;
  top: 0;
  width: 100%;
  height: 100%;
  overflow: auto;
  background-color: rgba(0,0,0,0.2);
}

.modal-content {
  z-index: 1;
  .blocking & {
    z-index: 3;
  }
  position: fixed;
  background-color: #fefefe;
  top: calc(var(--top-offset) + 10pt);
  margin-left: -40%;
  width: 80%;
  left: 50%;
  padding: 0;
  border: 1px solid #888;
  box-shadow: 0 4px 8px 0 rgba(0,0,0,0.2),0 6px 20px 0 rgba(0,0,0,0.19);
  animation-name: animatetop;
  animation-duration: 0.2s;
  .night & {
    background-color: black;
    color: #bdbdbd;
  }
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

  .night & {
    background-color: #454545;
    color: #bdbdbd;
  }
}

.modal-body {
  --max-height: calc(100vh - var(--top-offset) - var(--bottom-offset) - 85pt);
  max-height: var(--max-height);
  padding: 5px 5px;
  margin: 5pt 5pt;
}

.modal-footer {
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
