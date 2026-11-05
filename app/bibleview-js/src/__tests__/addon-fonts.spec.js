import {afterEach, describe, expect, it} from "vitest";
import {mount} from "@vue/test-utils";
import {useAddonFonts} from "@/composables/addon-fonts";
import {emit} from "@/eventbus";

let wrapper;
afterEach(() => {
    wrapper?.unmount();
    document.querySelectorAll("link[href^='/fonts/']").forEach(link => link.remove());
});

describe("host installed fonts", () => {
    it("loads fonts from host config, keeps unchanged links and removes uninstalled fonts", () => {
        wrapper = mount({setup() { useAddonFonts(); }, template: "<div />"});
        emit("set_config", {appSettings: {fontModuleNames: ["TTF_Study Font"]}});
        const link = document.querySelector("link[href^='/fonts/']");
        expect(link?.getAttribute("href")).toBe("/fonts/TTF_Study%20Font/fonts.css");
        emit("set_config", {appSettings: {fontModuleNames: ["TTF_Study Font"]}});
        expect(document.querySelector("link[href^='/fonts/']")).toBe(link);
        emit("set_config", {appSettings: {fontModuleNames: []}});
        expect(document.querySelector("link[href^='/fonts/']")).toBeNull();
    });

    it("leaves Android URL and reload_addons fonts alone when config has no host font list", () => {
        wrapper = mount({setup() { useAddonFonts(); }, template: "<div />"});
        emit("reload_addons", {fontModuleNames: ["Fonts"]});
        const link = document.querySelector("link[href='/fonts/Fonts/fonts.css']");
        expect(link).not.toBeNull();
        emit("set_config", {appSettings: {}});
        expect(document.querySelector("link[href='/fonts/Fonts/fonts.css']")).toBe(link);
        emit("reload_addons", {fontModuleNames: ["Fonts"]});
        expect(document.querySelector("link[href='/fonts/Fonts/fonts.css']")).not.toBe(link);
    });
});
