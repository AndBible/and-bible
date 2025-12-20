<!--
  - Copyright (c) 2020-2024 Martin Denham, Tuomas Airaksinen and the AndBible contributors.
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
  <div class="chapter-nav" :class="position">
    <!-- Previous chapter -->
    <button
      @click="$emit('navigatePrev')"
      class="nav-btn"
      :title="strings.previousChapter"
    >
      <FontAwesomeIcon :icon="faChevronLeft" />
    </button>

    <!-- Load more -->
    <button
      @click="$emit('loadMore')"
      :disabled="loading || (position === 'bottom' && reachedEnd)"
      class="nav-btn"
      :title="strings.loadMore"
    >
      <FontAwesomeIcon v-if="loading" :icon="faSpinner" spin />
      <FontAwesomeIcon v-else :icon="position === 'top' ? faAnglesUp : faAnglesDown" />
    </button>

    <!-- Next chapter -->
    <button
      @click="$emit('navigateNext')"
      :disabled="position === 'bottom' && reachedEnd"
      class="nav-btn"
      :title="strings.nextChapter"
    >
      <FontAwesomeIcon :icon="faChevronRight" />
    </button>
  </div>
</template>

<script lang="ts" setup>
import { PropType } from "vue";
import { FontAwesomeIcon } from "@fortawesome/vue-fontawesome";
import { faChevronLeft, faChevronRight, faAnglesUp, faAnglesDown, faSpinner } from "@fortawesome/free-solid-svg-icons";
import { useCommon } from "@/composables";

const props = defineProps({
  position: {
    type: String as PropType<'top' | 'bottom'>,
    required: true
  },
  loading: {
    type: Boolean,
    default: false
  },
  reachedEnd: {
    type: Boolean,
    default: false
  }
});

defineEmits(['loadMore', 'navigatePrev', 'navigateNext']);
const { strings } = useCommon();
</script>

<style scoped lang="scss">
.chapter-nav {
  display: flex;
  justify-content: center;
  gap: 16px;
  padding: 16px 8px;

  &.top {
    padding-top: 8px;
  }

  &.bottom {
    padding-bottom: 8px;
  }
}

.nav-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 44px;
  height: 44px;
  border-radius: 50%;
  border: 1px solid var(--text-color);
  background: transparent;
  color: var(--text-color);
  cursor: pointer;
  font-size: 18px;
  transition: background-color 0.2s ease;

  &:disabled {
    opacity: 0.5;
    cursor: not-allowed;
  }

  &:not(:disabled):active {
    background: rgba(128, 128, 128, 0.2);
  }
}
</style>
