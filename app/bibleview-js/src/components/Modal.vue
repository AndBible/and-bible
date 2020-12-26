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
    <div ref="myModal" class="modal">
      <div class="modal-content" :style="`margin-top: ${config.toolbarOffset}px;`">
        <div class="modal-header">
          <span class="title">
            <slot name="title"/>
          </span>
        </div>
        <div class="modal-body">
          <p><slot/></p>
        </div>
        <div class="modal-footer">
        </div>
      </div>

    </div>
  </teleport>

</template>

<script>

import {inject} from "@vue/runtime-core";
import {ref} from "@vue/reactivity";
import {setupElementEventListener} from "@/utils";

export default {
  name: "Modal",
  setup(props, {emit}) {
    const config = inject("config")
    const myModal = ref(null);
    setupElementEventListener(myModal, "click", () => {
        emit("close");
    });
    return {config, myModal}
  }
}
</script>

<style scoped>
.modal {
  display: block;
  position: fixed;
  z-index: 1;
  padding-top: 10px;
  left: 0;
  top: 0;
  width: 100%;
  height: 100%;
  overflow: auto;
  background-color: rgb(0,0,0);
  background-color: rgba(0,0,0,0.1);
}

.modal-content {
  position: relative;
  background-color: #fefefe;
  margin: auto;
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

.close {
  color: white;
  float: right;
  font-size: 28px;
  font-weight: bold;
}

.close:hover,
.close:focus {
  color: #000;
  text-decoration: none;
  cursor: pointer;
}

.modal-header {
  padding: 0.5em;
  background-color: #acacac;
  color: white;
  font-weight: bold;
}

.modal-body {padding: 2px 16px;}

.modal-footer {
  padding: 2px 16px;
  background-color: #acacac;
  color: white;
}

</style>
