<!--
  - Copyright (c) 2021-2022 Martin Denham, Tuomas Airaksinen and the AndBible contributors.
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
  <div class="tab-container" :class="containerClass">
    <TabNavigation
        v-if="showNavigation"
        :tabs="tabs"
        :active-tab="activeTab"
        :navigation-class="navigationClass"
        @tab-change="handleTabChange"
    />
    
    <div class="tab-content" :class="contentClass">
      <TabPanel
          v-for="tab in tabs"
          :key="tab.id"
          :tab-id="tab.id"
          :active="activeTab === tab.id"
          :panel-class="panelClass"
      >
        <slot :name="tab.id" :tab="tab" :active="activeTab === tab.id"></slot>
      </TabPanel>
    </div>
  </div>
</template>

<script setup lang="ts">
import {computed, provide, ref, watch} from 'vue';
import TabNavigation from './TabNavigation.vue';
import TabPanel from './TabPanel.vue';
import {IconDefinition} from "@fortawesome/fontawesome-svg-core";
import {activeTabKey, setActiveTabKey} from "@/types/constants";

export interface Tab {
  id: string;
  label: string;
  icon?: string | IconDefinition;
  disabled?: boolean;
}

const props = withDefaults(defineProps<{
  tabs: Tab[];
  defaultTab?: string;
  showNavigation?: boolean;
  containerClass?: string;
  navigationClass?: string;
  contentClass?: string;
  panelClass?: string;
}>(), {
  showNavigation: true,
  containerClass: '',
  navigationClass: '',
  contentClass: '',
  panelClass: ''
});

const emit = defineEmits<{
  tabChange: [tabId: string, tab: Tab];
}>();

const activeTab = ref<string>(
    props.defaultTab || 
    props.tabs.find(tab => !tab.disabled)?.id || 
    props.tabs[0]?.id || 
    ''
);

const tabs = computed(() => {
  return props.tabs.filter(tab => tab.id && tab.label);
});

provide(activeTabKey, activeTab);
provide(setActiveTabKey, (tabId: string) => {
  if (tabId !== activeTab.value) {
    const tab = tabs.value.find(t => t.id === tabId);
    if (tab && !tab.disabled) {
      activeTab.value = tabId;
    }
  }
});

function handleTabChange(tabId: string) {
  const tab = tabs.value.find(t => t.id === tabId);
  if (tab && !tab.disabled && tabId !== activeTab.value) {
    activeTab.value = tabId;
    emit('tabChange', tabId, tab);
  }
}

watch(() => props.tabs, (newTabs) => {
  if (!newTabs.find(tab => tab.id === activeTab.value)) {
    const firstAvailable = newTabs.find(tab => !tab.disabled);
    if (firstAvailable) {
      activeTab.value = firstAvailable.id;
    }
  }
}, { immediate: true });

watch(() => props.defaultTab, (newDefaultTab) => {
  if (newDefaultTab && newDefaultTab !== activeTab.value) {
    const tab = tabs.value.find(t => t.id === newDefaultTab);
    if (tab && !tab.disabled) {
      activeTab.value = newDefaultTab;
    }
  }
});

defineExpose({
  setActiveTab: (tabId: string) => handleTabChange(tabId),
  getActiveTab: () => activeTab.value,
  getTabs: () => tabs.value
});
</script>

<style scoped lang="scss">
.tab-container {
  display: flex;
  flex-direction: column;
}

.tab-content {
  flex: 1;
  padding-top: 1em;
}
</style>