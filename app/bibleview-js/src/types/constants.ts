/*
 * Copyright (c) 2022 Martin Denham, Tuomas Airaksinen and the AndBible contributors.
 *
 * This file is part of AndBible: Bible Study (http://github.com/AndBible/and-bible).
 *
 * AndBible is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software Foundation,
 * either version 3 of the License, or (at your option) any later version.
 *
 * AndBible is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with AndBible.
 * If not, see http://www.gnu.org/licenses/.
 */

import {InjectionKey, Ref} from "vue";
import {OsisFragment} from "@/types/client-objects";
import {useAndroid} from "@/composables/android";
import {BibleDocumentInfo, FootNoteCount, VerseInfo} from "@/types/common";
import {useVerseHighlight} from "@/composables/verse-highlight";
import {useGlobalBookmarks} from "@/composables/bookmarks";
import {AppSettings, CalculatedConfig, Config} from "@/composables/config";
import {useCustomCss} from "@/composables/custom-css";
import {useScroll} from "@/composables/scroll";
import {useModal} from "@/composables/modal";
import {useStrings} from "@/composables/strings";
import {useCustomFeatures} from "@/composables/features";
import {useReferenceCollector} from "@/composables";
import {useKeyboard} from "@/composables/keyboard";

export const osisFragmentKey: InjectionKey<OsisFragment> = Symbol("osisFragment");
export const androidKey: InjectionKey<ReturnType<typeof useAndroid>> = Symbol("android");
export const bibleDocumentInfoKey: InjectionKey<BibleDocumentInfo> = Symbol("bibleDocumentInfo");
export const verseInfoKey: InjectionKey<VerseInfo> = Symbol("verseInfo");
export const modalKey: InjectionKey<ReturnType<typeof useModal>> = Symbol("modal");
export const keyboardKey: InjectionKey<ReturnType<typeof useKeyboard>> = Symbol("keyboard");
export const footnoteCountKey: InjectionKey<FootNoteCount> = Symbol("footnoteCount");
export const referenceCollectorKey: InjectionKey<ReturnType<typeof useReferenceCollector>> = Symbol("referenceCollector");
export const verseHighlightKey: InjectionKey<ReturnType<typeof useVerseHighlight>> = Symbol("verseHighlight");
export const globalBookmarksKey: InjectionKey<ReturnType<typeof useGlobalBookmarks>> = Symbol("globalBookmarks");
export const configKey: InjectionKey<Config> = Symbol("config");
export const appSettingsKey: InjectionKey<AppSettings> = Symbol("appSettings");
export const calculatedConfigKey: InjectionKey<CalculatedConfig> = Symbol("calculatedConfig");
export const customCssKey: InjectionKey<ReturnType<typeof useCustomCss>> = Symbol("customCss");
export const scrollKey: InjectionKey<ReturnType<typeof useScroll>> = Symbol("scroll");
export const stringsKey: InjectionKey<ReturnType<typeof useStrings>> = Symbol("strings");
export const exportModeKey: InjectionKey<Ref<boolean>> = Symbol("exportMode");
export const customFeaturesKey: InjectionKey<ReturnType<typeof useCustomFeatures>> = Symbol("customFeatures");
export const locateTopKey: InjectionKey<Ref<boolean>> = Symbol("locateTop");
