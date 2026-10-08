import {describe, expect, it} from "vitest";
import {mount} from "@vue/test-utils";
import {defineComponent, h} from "vue";
import {useReadingProgressSettings} from "@/composables/reading-progress-settings";
import {emit} from "@/eventbus";

// The listener is registered in onMounted, so the composable must run inside a mounted component.
function setup(initial) {
    let settings;
    const Host = defineComponent({
        setup() {
            settings = useReadingProgressSettings(initial, {setReadingProgressSettings: () => {}}).settings;
            return () => h("div");
        },
    });
    const wrapper = mount(Host);
    return {settings, wrapper};
}

describe("useReadingProgressSettings update listener", () => {
    it("applies a full bundle that turns a field back to its default", () => {
        const {settings, wrapper} = setup({memorizeIncludeReference: false});
        expect(settings.memorizeIncludeReference).toBe(false);

        emit("update_reading_progress_settings", {
            autoMarkMemorized: true,
            memorizeTypeFullWords: false,
            memorizeWordVisibility: "light",
            memorizeErrorHeatmap: true,
            memorizeScrambleHideUsed: false,
            memorizeIncludeReference: true,
        });

        expect(settings.memorizeIncludeReference).toBe(true);
        wrapper.unmount();
    });

    it("an empty update leaves old values (why Android must send the full bundle)", () => {
        const {settings, wrapper} = setup({memorizeIncludeReference: false});
        emit("update_reading_progress_settings", {});
        expect(settings.memorizeIncludeReference).toBe(false);
        wrapper.unmount();
    });
});
