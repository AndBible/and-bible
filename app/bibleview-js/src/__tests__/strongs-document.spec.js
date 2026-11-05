import {afterEach, describe, expect, it, vi} from "vitest";
import {mount} from "@vue/test-utils";
import StrongsDocument from "@/components/documents/StrongsDocument.vue";
import {androidKey, appSettingsKey, calculatedConfigKey, configKey, customCssKey, stringsKey} from "@/types/constants";

vi.mock("@/components/documents/OsisFragment.vue", () => ({default: {props: ["fragment"], template: '<div class="fragment">{{ fragment.bookInitials }}:{{ fragment.keyName }}</div>'}}));
vi.mock("@/components/tabs/TabNavigation.vue", () => ({default: {
    props: ["tabs", "activeTab"],
    emits: ["tab-change"],
    template: '<nav><button v-for="tab in tabs" :key="tab.id" :data-active="activeTab === tab.id" @click="$emit(\'tab-change\', tab.id)">{{ tab.label }}</button></nav>',
}}));

function fragment(keyName, bookInitials, bookAbbreviation, features) {
    return {key: `${bookInitials}:${keyName}`, keyName, bookInitials, bookAbbreviation, features};
}

function mountDocument() {
    const android = {saveState: vi.fn()};
    const document = {
        id: "strongs", type: "multi", compare: false, contentType: "strongs",
        osisFragments: [
            fragment("1", "GREEK_A", "GA", {type: "greek", keyName: "1"}),
            fragment("1", "GREEK_B", "GB", {type: "greek", keyName: "1"}),
            fragment("N-NSM", "MORPH_A", "MA"),
            fragment("N-NSM", "MORPH_B", "MB"),
        ],
    };
    const wrapper = mount(StrongsDocument, {props: {document}, global: {provide: {
        [androidKey]: android,
        [appSettingsKey]: {},
        [calculatedConfigKey]: {},
        [configKey]: {},
        [customCssKey]: {},
        [stringsKey]: {findAllOccurrences: "Find all"},
    }}});
    return {wrapper, android};
}

afterEach(() => vi.restoreAllMocks());

describe("StrongsDocument.vue", () => {
    it("shows the initial dictionaries and saves changed selections", async () => {
        const {wrapper, android} = mountDocument();
        try {
            expect(wrapper.findAll(".strongs-entry .fragment").map(node => node.text())).toEqual(["GREEK_A:1"]);
            expect(wrapper.findAll(".morph-entry .fragment").map(node => node.text())).toEqual(["MORPH_A:N-NSM"]);
            expect(wrapper.findAll(".find-all a").map(node => node.attributes("href"))).toEqual([
                "ab-find-all://?type=greek&name=1",
            ]);

            await wrapper.findAll("nav button")[1].trigger("click");
            expect(wrapper.findAll(".strongs-entry .fragment").map(node => node.text())).toEqual(["GREEK_B:1"]);
            expect(wrapper.findAll("nav button")[1].attributes("data-active")).toBe("true");
            expect(android.saveState).toHaveBeenCalledWith({selectedStrongsDict: "GREEK_B", selectedMorphDict: "MORPH_A"});
        } finally {
            wrapper.unmount();
        }
    });
});
