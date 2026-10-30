import {onMounted, onUpdated, Ref} from "vue";

/** Multi documents can repeat Bible/paragraph ordinals across fragments. Give their
 * reading positions a document-local sequence without changing the source ordinals
 * used by verse actions and highlights. Fragment starts cover headers/empty entries.
 */
export function useMultiDocumentScroll(root: Ref<HTMLElement | null>) {
    function indexAnchors() {
        root.value?.querySelectorAll<HTMLElement>("[data-multi-fragment], .verse, .ordinal")
            .forEach((element, index) => element.dataset.scrollOrdinal = String(index));
    }
    onMounted(indexAnchors);
    onUpdated(indexAnchors);
}
