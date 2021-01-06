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
  <div v-if="logEntries.length > 0" @click="showLog=true" :class="`logbox-button ${buttonStyle}`">
    {{logEntries.length}}
  </div>
  <div v-if="showLog" @click="showLog=false" class="logbox">
    <div class="errorbox">
      <a class="error-link" href="ab-error://error">{{ strings.reportError }}</a>
      <ul>
        <li
            v-for="({type, msg, count}, index) in logEntries"
            :class="`error-${type}`"
            :key="index">
          {{type}} {{msg}} ({{count}})
        </li>
      </ul>
    </div>
  </div>
</template>

<script>
import {useCommon} from "@/composables";
import {enableLogSync} from "@/composables/android";
import {onMounted, onUnmounted} from "@vue/runtime-core";

export default {
  name: "ErrorBox",
  props: {
    logEntries: {type: Object, required: true}
  },
  computed: {
    buttonStyle({logEntries}) {
      if (logEntries.find(v => v.type === "ERROR")) return "error";
      return "warn";
    }

  },
  setup() {
    onMounted(() => enableLogSync(true))
    onUnmounted(() => enableLogSync(false))
    return useCommon();
  },
  data() {
    return {
      showLog: false
    }
  }
}
</script>

<style scoped lang="scss">
.logbox {
  font-size: 8pt;
  color: white;
  position: fixed;
  width: 100%;
  top: var(--top-offset);
  bottom: var(--bottom-offset);
  background-color: rgba(88, 57, 57, 0.9);
}

.errorbox {
  overflow: scroll;
  width: 100%;
  height: calc(100vh - var(--bottom-offset) - var(--top-offset));
}

.logbox-button {
  border-radius: 5pt;
  top: var(--top-offset);
  position: fixed;
  padding: 0.5em;
  color: white;
  right:50%;
  width:1em;
  height: 1em;
  animation-name: animatetop;
  animation-duration: 0.4s;
  &.error {
    background-color: rgba(200, 0, 0, 0.5);
  }
  &.warn {
    background-color: rgba(200, 133, 0, 0.5);
  }
}

@keyframes animatetop {
  from {top:-300px; opacity:0}
  to {top:var(--top-offset); opacity:1}
}

.error-ERROR {
  color: #ff0000;
}

.error-WARN {
  color: #ffdf38;
}

.error-link {
  font-size: 200%;
  color: red;
  padding-left: 1.5em;
}

</style>
