/*
 * Copyright (c) 2026 Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
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

import {App, ComponentPublicInstance} from "vue";

/**
 * Vue dev-build warnings we deliberately drop.
 *
 * `Missing required prop`: BibleView renders whatever OSIS a module happens to contain, and the
 * OSIS components accept those attributes loosely on purpose -- explicitly declaring every
 * possible OSIS attribute is a non-goal. Vue's dev build therefore emits one of these for
 * practically every element of every document load (dozens of lines per chapter, e.g. `Title`'s
 * `canonical`/`short`), which floods logcat and buries the warnings that do matter. Since the
 * warnings are pure noise here and not actionable, filtering the whole class is correct.
 *
 * Anything not listed keeps Vue's normal behaviour, so a genuine new warning still shows up.
 */
const SUPPRESSED_WARNING_PREFIXES = [
    "Missing required prop",
];

/** True when [msg] (a Vue warning message, without the `[Vue warn]: ` prefix) should be dropped. */
export function shouldSuppressVueWarning(msg: string): boolean {
    return SUPPRESSED_WARNING_PREFIXES.some(prefix => msg.startsWith(prefix));
}

/**
 * Installs the filter above as the app's `warnHandler`.
 *
 * Note this only ever runs in a debug build: Vue compiles warnings (and `warnHandler` support)
 * out of production builds entirely.
 *
 * Non-suppressed warnings are re-emitted in Vue's own default format so they keep reaching Android
 * logcat unchanged as `bibleview-js: WARNING [Vue warn]: ...` -- that is the only JS diagnostic
 * channel available on-device (see docs/compose-ondevice-findings.md F42).
 */
export function installVueWarningFilter(app: App): void {
    const previous = app.config.warnHandler;
    app.config.warnHandler = (msg: string, instance: ComponentPublicInstance | null, trace: string) => {
        if (shouldSuppressVueWarning(msg)) return;
        if (previous) previous(msg, instance, trace);
        else console.warn(`[Vue warn]: ${msg}${trace}`);
    };
}
