import {onMounted, onUpdated, Ref} from "vue";

/** Multi documents can repeat Bible/paragraph ordinals across fragments. Give their
 * reading positions a document-local sequence without changing the source ordinals
 * used by verse actions and highlights. Fragment starts cover headers/empty entries.
 * A compare heading (`data-multi-heading`) above the first fragment is index 0, so that
 * ordinal 0 (a fresh load) shows the top of the document.
 *
 * The local index is a best-effort position: it can shift when settings (e.g. chapter
 * numbers) or module content change between save and restore. It is re-indexed only on
 * the multi/strongs component's own updates (onMounted/onUpdated), not on changes made
 * deeper in child components that do not re-render this one.
 */
export function useMultiDocumentScroll(root: Ref<HTMLElement | null>) {
    function indexAnchors() {
        root.value?.querySelectorAll<HTMLElement>("[data-multi-heading], [data-multi-fragment], .verse, .ordinal")
            .forEach((element, index) => element.dataset.scrollOrdinal = String(index));
    }
    onMounted(indexAnchors);
    onUpdated(indexAnchors);
}
