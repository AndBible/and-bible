<template>
  <ModalDialog v-if="show" @close="saveSettings" blocking locate-top>
    <template #title>
      <slot name="title">
        {{ strings.bookmarkSettingsTitle }}
      </slot>
    </template>
    
    <div class="settings-container">
      <TabContainer
          :tabs="tabsConfig"
          :default-tab="activeTab"
          container-class="bookmark-settings-tabs"
          content-class="bookmark-settings-content"
          @tab-change="handleTabChange"
      >
        <!-- Custom Icon Tab -->
        <template #icons>
          <div class="icon-list">
            <div
              v-for="[key, icon] in Array.from(customIconMap.entries())"
              :key="key"
              class="icon-item"
              :class="{selected: key === selectedIcon}"
              @click="selectIcon(key)">
              <FontAwesomeIcon :icon="icon" />
            </div>
            <div
               class="icon-item"
               :class="{selected: selectedIcon === null}"
               @click="selectIcon(null)">
               <FontAwesomeIcon :icon="faTimes" />
            </div>
          </div>
        </template>

        <!-- Edit Action Tab -->
        <template #editAction>
          <div class="edit-action-controls">
            <!-- Experimental Features Notice -->
            <div class="experimental-notice">
              <div class="experimental-header">
                <FontAwesomeIcon :icon="faExclamationTriangle" class="experimental-icon" />
                <span class="experimental-title">{{ strings.experimentalFeatureTitle }}</span>
                <button
                    type="button"
                    class="help-button"
                    @click="showExperimentalHelp"
                    :title="strings.experimentalFeatureHelpTitle">
                  <FontAwesomeIcon :icon="faQuestionCircle" />
                </button>
              </div>
            </div>
            
            <div v-if="selectedEditAction.mode" class="content-input">
              <label>{{ strings.editActionContentLabel }}:</label>

              <!-- Formatting Buttons -->
              <div class="formatting-buttons">
                <button
                    type="button"
                    class="format-button"
                    @click="insertParagraphBreak"
                    :title="strings.insertParagraphBreak">
                  <FontAwesomeIcon :icon="faParagraph" />
                </button>

                <button
                    type="button"
                    class="format-button"
                    @click="insertSubtitle"
                    :title="strings.insertSubtitle">
                  <FontAwesomeIcon :icon="faHeading" />
                </button>
              </div>

              <textarea
                  ref="contentTextarea"
                  v-model="selectedEditAction.content"
                  @input="validateContent"
                  :placeholder="strings.editActionContentPlaceholder"
                  class="content-textarea"
                  :class="{ 'has-error': validationError }"
              >
              </textarea>

              <!-- Validation Error -->
              <div v-if="validationError" class="validation-error">
                <FontAwesomeIcon :icon="faExclamationTriangle" />
                <span>{{ validationError }}</span>
              </div>
            </div>
            <div class="mode-selection">
              <label>{{ strings.editActionModeLabel }}:</label>
              <div class="mode-toggle-buttons">
                <button
                  type="button"
                  class="mode-toggle"
                  :class="{ active: selectedEditAction.mode === null }"
                  @click="selectedEditAction.mode = null"
                  :title="strings.editActionModeNone">
                  <FontAwesomeIcon :icon="faBan" />
                  <span>{{ strings.editActionModeNone }}</span>
                </button>
                <button
                    type="button"
                    class="mode-toggle"
                    :class="{ active: selectedEditAction.mode === EditActionMode.PREPEND }"
                    @click="selectedEditAction.mode = EditActionMode.PREPEND"
                    :title="strings.editActionModePrepend">
                  <FontAwesomeIcon :icon="faArrowUp" />
                  <span>{{ strings.editActionModePrepend }}</span>
                </button>
                <button
                  type="button"
                  class="mode-toggle"
                  :class="{ active: selectedEditAction.mode === EditActionMode.APPEND }"
                  @click="selectedEditAction.mode = EditActionMode.APPEND"
                  :title="strings.editActionModeAppend">
                  <FontAwesomeIcon :icon="faArrowDown" />
                  <span>{{ strings.editActionModeAppend }}</span>
                </button>
              </div>
            </div>
          </div>
        </template>
      </TabContainer>
    </div>

    <div class="dialog-buttons">
      <button @click="cancel" class="cancel-button">{{ strings.cancel }}</button>
      <button 
        @click="saveSettings" 
        class="save-button"
        :disabled="!!validationError"
        :class="{ 'disabled': !!validationError }">
        {{ strings.ok }}
      </button>
    </div>
  </ModalDialog>
</template>

<script setup lang="ts">
import {computed, nextTick, reactive, ref, watch} from "vue";
import ModalDialog from "@/components/modals/ModalDialog.vue";
import {FontAwesomeIcon} from "@fortawesome/vue-fontawesome";
import {Deferred} from "@/utils";
import {useCommon} from "@/composables";
import {isExperimentalFeatureEnabled} from "@/composables/config";
import TabContainer from "@/components/tabs/TabContainer.vue";
import {
    faArrowDown,
    faArrowUp,
    faBan,
    faEdit,
    faExclamationTriangle,
    faHeading,
    faIcons,
    faParagraph,
    faQuestionCircle,
    faTimes
} from "@fortawesome/free-solid-svg-icons";
import {customIconMap} from "@/composables/fontawesome";
import {validateBookmarkEditActionContent} from "@/utils/xml-validation";
import {EditAction, EditActionMode} from "@/types/client-objects";
import {Tab} from "@/components/tabs/TabContainer.vue";

const { strings, appSettings, android } = useCommon();

interface BookmarkSettings {
    customIcon: string | null;
    editAction: EditAction;
}

const show = ref(false);
const activeTab = ref<'icons' | 'editAction'>('icons');
const selectedIcon = ref<null | string>(null);
const selectedEditAction = reactive<EditAction>({
    mode: null,
    content: null
});
const contentTextarea = ref<HTMLTextAreaElement | null>(null);
const validationError = ref<string | null>(null);

let deferred: Deferred<BookmarkSettings | null> | null = null;

// Tab configuration for the TabContainer
const tabsConfig = computed<Tab[]>(() => {
    const tabs = [
        { 
            id: 'icons', 
            label: strings.customIconLabel, 
            icon: selectedIcon.value ? customIconMap.get(selectedIcon.value) : faIcons
        }
    ];
    
    // Only show edit action tab if bookmark edit actions experimental feature is enabled
    if (isExperimentalFeatureEnabled(appSettings, 'bookmark_edit_actions')) {
        tabs.push({ 
            id: 'editAction', 
            label: strings.editActionLabel, 
            icon: faEdit 
        });
    }
    
    return tabs;
});

// Handle tab change events
function handleTabChange(tabId: string) {
    activeTab.value = tabId as 'icons' | 'editAction';
}

function selectIcon(key: null | string) {
    selectedIcon.value = key;
}

function showExperimentalHelp() {
    android.helpDialog(strings.experimentalFeatureHelpContent, strings.experimentalFeatureHelpTitle);
}

async function insertParagraphBreak() {
    const textarea = contentTextarea.value;
    if (!textarea) return;
    
    const start = textarea.selectionStart;
    const end = textarea.selectionEnd;
    const currentValue = selectedEditAction.content || '';
    
    // Insert <br/> at cursor position
    const beforeCursor = currentValue.substring(0, start);
    const afterCursor = currentValue.substring(end);
    selectedEditAction.content = beforeCursor + '<br/>' + afterCursor;

    await nextTick();
    // Move cursor after the inserted tag
    textarea.selectionStart = textarea.selectionEnd = start + 5;
    textarea.focus();
}

async function insertSubtitle() {
    const textarea = contentTextarea.value;
    if (!textarea) return;
    
    const start = textarea.selectionStart;
    const end = textarea.selectionEnd;
    const currentValue = selectedEditAction.content || '';
    
    // Get selected text or use placeholder
    const selectedText = currentValue.substring(start, end);
    const subtitleText = selectedText || strings.subtitlePlaceholder;
    
    // Insert <subtitle>...</subtitle> at cursor position
    const beforeCursor = currentValue.substring(0, start);
    const afterCursor = currentValue.substring(end);
    selectedEditAction.content = beforeCursor + `<subtitle>${subtitleText}</subtitle>` + afterCursor;

    await nextTick();

    // Select the subtitle text for easy editing
    const subtitleStart = start + 10; // after '<subtitle>'
    const subtitleEnd = subtitleStart + subtitleText.length;
    textarea.selectionStart = subtitleStart;
    textarea.selectionEnd = subtitleEnd;
    textarea.focus();
}

function validateContent() {
    const content = selectedEditAction.content;
    if (!content || selectedEditAction.mode === null) {
        validationError.value = null;
        return true;
    }
    
    const error = validateBookmarkEditActionContent(content, strings);
    validationError.value = error;
    return error === null;
}

function saveSettings() {
    if (!validateContent()) {
        return;
    }
    
    const result: BookmarkSettings = {
        customIcon: selectedIcon.value,
        editAction: {
            mode: selectedEditAction.mode,
            content: selectedEditAction.content
        }
    };
    deferred?.resolve(result);
    show.value = false;
}

function cancel() {
    deferred?.resolve(null);
    show.value = false;
}

async function askBookmarkSettings(currentIcon: null | string, currentEditAction: EditAction): Promise<BookmarkSettings | null> {
    if (currentIcon !== null) {
        activeTab.value = 'icons';
    }
    else if (currentEditAction.mode !== null && isExperimentalFeatureEnabled(appSettings, 'bookmark_edit_actions')) {
        activeTab.value = 'editAction';
    } else {
        activeTab.value = 'icons'; // Default to icons tab if nothing is set or experimental features disabled
    }
    selectedIcon.value = currentIcon ?? null;
    
    // Reset edit action to null if experimental features are disabled
    if (!isExperimentalFeatureEnabled(appSettings, 'bookmark_edit_actions')) {
        selectedEditAction.mode = null;
        selectedEditAction.content = null;
    } else {
        selectedEditAction.mode = currentEditAction.mode;
        selectedEditAction.content = currentEditAction.content;
    }
  
    show.value = true;
    deferred = new Deferred<BookmarkSettings | null>();
    const result = await deferred.wait();
    return result ?? null;
}

defineExpose({ askBookmarkSettings });

// Clear validation errors when mode changes to null
watch(() => selectedEditAction.mode, (newMode) => {
    if (newMode === null) {
        validationError.value = null;
        selectedEditAction.content = null;
    }
});
</script>

<style scoped lang="scss">
@use "@/common.scss" as *;

.settings-container {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.bookmark-settings-tabs {
  flex: 1;
}

.bookmark-settings-content {
  min-height: 200px;
}

.setting-section {
  h3 {
    margin: 0 0 12px 0;
    font-size: 16px;
    font-weight: 600;
    color: #333;
    
    .night & {
      color: #ccc;
    }
  }
}

.icon-list {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.icon-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 10px;
  border: 1px solid #ccc;
  cursor: pointer;
  border-radius: 5px;
  
  &.selected {
    border-color: #007bff;
    background-color: #e7f1ff;
  }
  
  .night & {
    border: 1px solid #555;
    background-color: #222;
    
    &.selected {
      border-color: #1e90ff;
      background-color: #333;
    }
  }
}

.edit-action-controls {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.mode-selection {
  display: flex;
  flex-direction: column;
  gap: 12px;
  
  label {
    font-weight: 500;
    color: #333;
    
    .night & {
      color: #ccc;
    }
  }
}

.mode-toggle-buttons {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 8px;
}

.mode-toggle {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  padding: 12px 8px;
  border: 2px solid #ccc;
  border-radius: 8px;
  background: white;
  cursor: pointer;
  font-size: 12px;
  font-weight: 500;
  transition: all 0.2s ease;
  .noAnimation & {
    transition: none;
  }
  min-height: 60px;
  
  .night & {
    background: #222;
    border-color: #555;
    color: #ccc;
  }
  
  &:hover {
    background: #f8f9fa;
    border-color: #007bff;
    
    .night & {
      background: #333;
      border-color: #1e90ff;
    }
  }
  
  &.active {
    background: #007bff;
    border-color: #007bff;
    color: white;
    
    .night & {
      background: #1e90ff;
      border-color: #1e90ff;
    }
  }
  
  svg {
    font-size: 16px;
  }
  
  span {
    text-align: center;
    line-height: 1.2;
  }
}

.mode-select {
  padding: 8px 12px;
  border: 1px solid #ccc;
  border-radius: 4px;
  background: white;
  font-size: 14px;
  
  .night & {
    background: #222;
    border-color: #555;
    color: #ccc;
  }
}

.content-input {
  display: flex;
  flex-direction: column;
  gap: 8px;
  
  label {
    font-weight: 500;
    color: #333;
    
    .night & {
      color: #ccc;
    }
  }
}

.content-textarea {
  padding: 8px 12px;
  border: 1px solid #ccc;
  border-radius: 4px;
  resize: vertical;
  font-family: inherit;
  font-size: 14px;
  
  .night & {
    background: #222;
    border-color: #555;
    color: #ccc;
  }
  
  &.has-error {
    border-color: #dc3545;
    box-shadow: 0 0 0 2px rgba(220, 53, 69, 0.25);
    
    .night & {
      border-color: #dc3545;
    }
  }
  
  &::placeholder {
    color: #999;
    
    .night & {
      color: #666;
    }
  }
}

.validation-error {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 12px;
  background: #f8d7da;
  border: 1px solid #f5c6cb;
  border-radius: 4px;
  color: #721c24;
  font-size: 14px;
  margin-top: 8px;
  
  .night & {
    background: #2d1b1b;
    border-color: #5a2d2d;
    color: #f5c6cb;
  }
  
  svg {
    color: #dc3545;
    
    .night & {
      color: #f5c6cb;
    }
  }
}

.format-help {
  margin-top: 8px;
  color: #6c757d;
  
  .night & {
    color: #adb5bd;
  }
  
  small {
    font-size: 12px;
  }
}

.formatting-buttons {
  display: flex;
  gap: 8px;
  margin-bottom: 8px;
}

.format-button {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 6px 10px;
  border: 1px solid #ccc;
  border-radius: 4px;
  background: white;
  cursor: pointer;
  font-size: 12px;
  font-weight: 500;
  transition: all 0.2s ease;
  .noAnimation & {
    transition: none;
  }
  
  .night & {
    background: #333;
    border-color: #555;
    color: #ccc;
  }
  
  &:hover {
    background: #f8f9fa;
    border-color: #007bff;
    
    .night & {
      background: #444;
      border-color: #1e90ff;
    }
  }
  
  svg {
    font-size: 14px;
  }
}

.format-help {
  margin-top: 4px;
  
  small {
    color: #666;
    font-size: 11px;
    
    .night & {
      color: #999;
    }
  }
}

.dialog-buttons {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  margin-top: 24px;
  padding-top: 16px;
  border-top: 1px solid #eee;
  
  .night & {
    border-top-color: #444;
  }
}

.cancel-button,
.save-button {
  padding: 8px 16px;
  border: 1px solid #ccc;
  border-radius: 4px;
  background: white;
  cursor: pointer;
  font-size: 14px;
  
  .night & {
    background: #333;
    border-color: #555;
    color: #ccc;
  }
  
  &:hover {
    background: #f5f5f5;
    
    .night & {
      background: #444;
    }
  }
}

.save-button {
  background: #007bff;
  color: white;
  border-color: #007bff;
  
  &:hover:not(:disabled) {
    background: #0056b3;
    border-color: #0056b3;
  }
  
  &:disabled {
    background: #6c757d;
    border-color: #6c757d;
    cursor: not-allowed;
    opacity: 0.65;
    
    &:hover {
      background: #6c757d;
      border-color: #6c757d;
    }
  }
}
</style>
