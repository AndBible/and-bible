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

import {computed, inject, onBeforeUnmount, onMounted, watch} from "vue";
import {faBookmark, faEllipsisV} from "@fortawesome/free-solid-svg-icons";
import {icon as faIcon, type IconDefinition} from "@fortawesome/fontawesome-svg-core";
import {globalBookmarksKey} from "@/types/constants";
import {isWholePageItem, resolveIcon} from "@/composables/bookmarks";
import {useCommon} from "@/composables";

const interactiveTags = new Set(['A', 'BUTTON', 'INPUT', 'SELECT', 'TEXTAREA', 'LABEL']);

function iconToHtml(iconDef: IconDefinition): string {
    return faIcon(iconDef).html[0];
}

function findFirstTextNode(root: Node): Text | null {
    const walker = document.createTreeWalker(root, NodeFilter.SHOW_TEXT, {
        acceptNode: (node) => node.textContent && node.textContent.trim().length > 0
            ? NodeFilter.FILTER_ACCEPT : NodeFilter.FILTER_REJECT
    });
    return walker.nextNode() as Text | null;
}

/** Find the outermost interactive ancestor between node and boundary (exclusive). */
function findInteractiveAncestor(node: Node, boundary: Element): Element | null {
    let outermost: Element | null = null;
    let current = node.parentElement;
    while (current && current !== boundary) {
        if (interactiveTags.has(current.tagName)) {
            outermost = current;
        }
        current = current.parentElement;
    }
    return outermost;
}

/**
 * Injects an inline action-menu icon into OsisFragment DOM before the first text node.
 * The icon reacts to bookmark state and config changes.
 */
export function useInlineActionIcons(
    documentId: string,
    bookInitials: string,
    annotateRef: string,
    openMenu: (anchor: HTMLElement) => void,
) {
    const globalBookmarks = inject(globalBookmarksKey)!;
    const {config, appSettings, adjustedColor} = useCommon();
    const injectedIcons: HTMLSpanElement[] = [];

    const wholePageBookmark = computed(() => {
        void config.showBookmarks;
        void config.showMyNotes;
        void appSettings.monochromeMode;
        void appSettings.colorEinkMode;

        const item = globalBookmarks.bookmarks.value.find(b =>
            isWholePageItem(b, bookInitials, annotateRef)
        );
        if (!item) return null;
        const label = globalBookmarks.bookmarkLabels.get(item.primaryLabelId || item.labels[0]);
        return label ? {item, label} : null;
    });

    const menuIcon = computed<IconDefinition>(() => {
        const bm = wholePageBookmark.value;
        if (!bm) return faEllipsisV;
        return (resolveIcon(bm.item, bm.label) ?? faBookmark) as IconDefinition;
    });

    const menuColor = computed<string | null>(() => {
        const bm = wholePageBookmark.value;
        if (!bm) return null;
        return adjustedColor((appSettings.monochromeMode && !appSettings.colorEinkMode) ? "black" : bm.label.color).string();
    });

    function applyIconStyle(span: HTMLSpanElement) {
        span.innerHTML = iconToHtml(menuIcon.value);
        const color = menuColor.value;
        if (color) {
            span.style.color = color;
            span.style.opacity = '1';
        } else {
            span.style.color = '';
            span.style.opacity = '';
        }
    }

    watch([menuIcon, menuColor], () => {
        for (const span of injectedIcons) {
            applyIconStyle(span);
        }
    });

    function createInlineIcon(): HTMLSpanElement {
        const span = document.createElement('span');
        span.className = 'inline-action-icon skip-offset';
        applyIconStyle(span);
        span.addEventListener('click', (e) => {
            e.stopPropagation();
            e.preventDefault();
            openMenu(span);
        });
        injectedIcons.push(span);
        return span;
    }

    onMounted(() => {
        const docEl = document.getElementById(`doc-${documentId}`);
        if (!docEl) return;
        const fragEl = docEl.querySelector('[id^="frag-"]');
        if (!fragEl) return;

        const firstText = findFirstTextNode(fragEl);
        if (firstText) {
            const interactive = findInteractiveAncestor(firstText, fragEl);
            if (interactive && interactive.parentNode) {
                interactive.parentNode.insertBefore(createInlineIcon(), interactive);
            } else if (firstText.parentNode) {
                firstText.parentNode.insertBefore(createInlineIcon(), firstText);
            }
        }
    });

    onBeforeUnmount(() => {
        for (const span of injectedIcons) {
            span.remove();
        }
        injectedIcons.length = 0;
    });
}
