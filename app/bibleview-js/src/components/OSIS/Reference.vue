<!--
  - Copyright (c) 2020-2022 Martin Denham, Tuomas Airaksinen and the AndBible contributors.
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
  <a class="reference" :class="{clicked, highlight: isHighlighted}" @click.prevent="openLink($event, link)" :href="link" ref="content"><span ref="slot"><slot/></span><template v-if="slotEmpty">{{osisRef}}&nbsp;</template></a>
</template>

<script setup lang="ts">
import {checkUnsupportedProps, useCommon} from "@/composables";
import {addEventFunction, EventPriorities, formatExportLink} from "@/utils";
import {computed, inject, ref} from "vue";
import {exportModeKey, osisFragmentKey, referenceCollectorKey, ordinalHighlightKey} from "@/types/constants";

const props = defineProps<{
    osisRef?: string
    target?: string
    source?: string
    type?: string
}>();

checkUnsupportedProps(props, "type");
const clicked = ref(false);
const isHighlighted = ref(false);
const {strings} = useCommon();
const {addCustom, resetHighlights} = inject(ordinalHighlightKey)!;
const referenceCollector = inject(referenceCollectorKey);
const content = ref<HTMLElement | null>(null);
const osisFragment = inject(osisFragmentKey)!;
const slot = ref<HTMLElement | null>(null);
const slotEmpty = computed(() => {
    if (slot.value === null) return true;
    return slot.value.innerText.trim() === "";
});

const osisRef = computed(() => {
    if ((!props.osisRef && !props.target) && content.value) {
        return `${osisFragment.bookInitials}:${content.value.innerText}`;
    } else if (props.target) {
        return props.target;
    } else {
        return props.osisRef!;
    }
});

const queryParams = computed(() => {
    let paramString = "osis=" + encodeURI(osisRef.value)
    if (osisFragment.v11n) {
        paramString += "&v11n=" + encodeURI(osisFragment.v11n)
    }
    return paramString
})

const exportMode = inject(exportModeKey, ref(false));

const link = computed(() => {
    if(exportMode.value) {
        return formatExportLink({ref: osisRef.value, v11n: osisFragment.v11n, doc: osisFragment.bookInitials})
    } else {
        return `osis://?${queryParams.value}`
    }
});

if (referenceCollector) {
    referenceCollector.collect(osisRef);
}

function openLink(event: MouseEvent, url: string) {
    addEventFunction(event, () => {
        window.location.assign(url)
        clicked.value = true;
        resetHighlights();
        isHighlighted.value = true;
        addCustom(() => isHighlighted.value = false);
    }, {title: strings.referenceLink, priority: EventPriorities.REFERENCE});
}

</script>

<style scoped lang="scss">
@import "~@/common.scss";
.highlight {
  @extend .isHighlighted;
  .monochrome & {
    background-color: transparent;
  }
}

</style>

<style lang="scss">
@import "~@/common.scss";

.reference {
  @extend .highlight-transition;
  border-radius: 5pt;
}

a {
  .noAnimation & {
    -webkit-tap-highlight-color: transparent;
  }
  &.clicked {
    color: #8b00ee;
    font-weight: normal;
  }

  .night & {
    &.clicked {
      color: #bf7eff;
    }
  }
}

</style>
