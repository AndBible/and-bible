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
  <teleport to="#notes">
    <div @click="$emit('close')" class="modal-backdrop">
      <div @click="$event.stopPropagation()" class="modal-content"
      >
        <div @click="$emit('close')" class="modal-header">
          <span class="title">
            <slot name="title"/>
          </span>
        </div>
        <div class="modal-body">
          <p><slot/></p>
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
import {ref} from "@vue/reactivity";
import {useCommon} from "@/composables";

export default {
  name: "Modal",
  emits: ["close"],
  setup() {
    const config = inject("config")
    const myModal = ref(null);
    return {config, myModal, ...useCommon()}
  }
}
</script>

<style scoped>
.modal-backdrop {
  display: block;
  position: fixed;
  z-index: 1;
  padding-top: 10px;
  left: 0;
  top: 0;
  width: 100%;
  height: 100%;
  overflow: auto;
  background-color: rgba(0,0,0,0.2);
}

.modal-content {
  z-index: 3;
  position: relative;
  background-color: #fefefe;
  margin: var(--toolbar-offset) auto auto;
  padding: 0;
  border: 1px solid #888;
  width: 80%;
  box-shadow: 0 4px 8px 0 rgba(0,0,0,0.2),0 6px 20px 0 rgba(0,0,0,0.19);
  animation-name: animatetop;
  animation-duration: 0.4s
}

@keyframes animatetop {
  from {top:-300px; opacity:0}
  to {top:0; opacity:1}
}

.modal-header {
  padding: 0.5em;
  background-color: #acacac;
  color: white;
  font-weight: bold;
}

.modal-body {
  max-height: calc(100vh - var(--toolbar-offset) - 70pt);
  overflow: auto;
  padding: 2px 16px;
}

.modal-footer {
  padding: 2px 16px;
  background-color: #acacac;
  color: white;
}

</style>
