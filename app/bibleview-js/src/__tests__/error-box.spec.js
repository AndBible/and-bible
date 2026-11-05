import {afterEach, describe, expect, it, vi} from "vitest";
import {mount} from "@vue/test-utils";
const {clearLog, enableLogSync, logEntries} = vi.hoisted(() => ({
    clearLog: vi.fn(),
    enableLogSync: vi.fn(),
    logEntries: [],
}));
vi.mock("@/composables/android", () => ({clearLog, enableLogSync, logEntries}));

import ErrorBox from "@/components/ErrorBox.vue";
import {appSettingsKey, calculatedConfigKey, configKey, stringsKey, androidKey} from "@/types/constants";

function mountErrorBox() {
    const wrapper = mount(ErrorBox, {global: {provide: {
        [appSettingsKey]: {},
        [calculatedConfigKey]: {},
        [configKey]: {},
        [stringsKey]: {reportError: "Report error", clearLog: "Clear log"},
        [androidKey]: {},
    }}});
    return wrapper;
}

afterEach(() => {
    logEntries.splice(0);
    clearLog.mockReset();
    enableLogSync.mockReset();
});

describe("ErrorBox.vue", () => {
    it("opens the log, styles errors, and clears it", async () => {
        logEntries.push({type: "ERROR", msg: "Broken", count: 2});
        const wrapper = mountErrorBox();
        try {
            expect(wrapper.find(".logbox-button").text()).toBe("1");
            expect(wrapper.find(".logbox-button").classes()).toContain("error");
            await wrapper.find(".logbox-button").trigger("click");
            expect(wrapper.find(".error-ERROR").text()).toBe("ERROR Broken (2)");
            expect(wrapper.find("a.error-link").attributes("href")).toBe("ab-error://error");
            await wrapper.find("button").trigger("click");
            expect(clearLog).toHaveBeenCalledOnce();
        } finally {
            wrapper.unmount();
        }
    });

    it("uses warning styling and enables then disables log synchronization", () => {
        logEntries.push({type: "WARN", msg: "Careful", count: 1});
        const wrapper = mountErrorBox();
        try {
            expect(wrapper.find(".logbox-button").classes()).toContain("warn");
            expect(enableLogSync).toHaveBeenCalledWith(true);
            wrapper.unmount();
            expect(enableLogSync).toHaveBeenLastCalledWith(false);
        } finally {
            if (wrapper.exists()) wrapper.unmount();
        }
    });
});
