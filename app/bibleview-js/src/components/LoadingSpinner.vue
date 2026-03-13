<!--
  - Copyright (c) 2020-2026 Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
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
  <div v-if="appSettings.disableAnimations" class="loading-icon">
    <FontAwesomeIcon :size="small ? '1x' : '2x'" icon="fa-regular fa-clock"/>
  </div>
  <div v-else class="lds-ring" :class="{ small }"><div/><div/><div/><div/></div>
</template>

<script lang="ts" setup>
import {FontAwesomeIcon} from "@fortawesome/vue-fontawesome";
import {inject} from "vue";
import {appSettingsKey} from "@/types/constants";

withDefaults(defineProps<{
    small?: boolean
}>(), {
    small: false
});

const appSettings = inject(appSettingsKey)!;
</script>

<style lang="scss" scoped>
@use "@/common.scss" as *;

$ring-size: 35px;
$ring-thickness: calc(#{$ring-size} / 12);

.loading-icon {
  border-radius: 50%;
  background: white;
  .night & {
    background: black;
  }
}

$ring-color: $button-grey;

.lds-ring {
  display: inline-block;
  position: relative;
  width: $ring-size;
  height: $ring-size;

  & div {
    box-sizing: border-box;
    display: block;
    position: absolute;
    width: $ring-size;
    height: $ring-size;
    margin: 8px;
    border: $ring-thickness solid $ring-color;
    border-radius: 50%;
    animation: lds-ring 1.2s cubic-bezier(0.5, 0, 0.5, 1) infinite;
    border-color: $ring-color transparent transparent transparent;

    &:nth-child(1) {
      animation-delay: -0.45s;
    }

    &:nth-child(2) {
      animation-delay: -0.3s;
    }

    &:nth-child(3) {
      animation-delay: -0.15s;
    }
  }

  &.small {
    $small-size: 24px;
    width: $small-size;
    height: $small-size;

    & div {
      width: $small-size;
      height: $small-size;
      margin: 4px;
      border-width: 2px;
    }
  }
}

@keyframes lds-ring {
  0% {
    transform: rotate(0deg);
  }
  100% {
    transform: rotate(360deg);
  }
}
</style>
