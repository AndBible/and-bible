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
  <div @click.stop="expanded=!expanded" :class="{'edit-buttons': expanded, 'menu': !expanded}">
    <div class="between" v-show="expanded">
      <slot/>
    </div>
    <div class="journal-button">
      <FontAwesomeIcon icon="ellipsis-h"/>
    </div>
  </div>
</template>

<script>
import {FontAwesomeIcon} from "@fortawesome/vue-fontawesome";
import {ref} from "@vue/reactivity";
import {watch} from "@vue/runtime-core";

let cancel = () => {}

export default {
  name: "JournalEditButtons",
  components: {FontAwesomeIcon},
  setup() {
    const expanded = ref(false);
    function close() {
      expanded.value = false
    }
    watch(expanded, v => {
      if(v) {
        cancel()
        cancel = close
      } else {
        if(cancel === close) {
          cancel = () => {}
        }
      }
    })
    return {expanded};
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
  .night & {
    border-color: rgba(255, 255, 255, 0.3);
  }
  position: absolute;
  right: 0;
  display: flex;
  justify-content: flex-end;
  z-index: 1;
  top: 0;
  opacity: 0.8;
}

.menu {
  position: absolute;
  right: 0;
  top: 0;
  z-index: 1;
}

</style>
