import {afterEach, describe, expect, it, vi} from "vitest";
import {mount} from "@vue/test-utils";
import {defineComponent, nextTick, ref} from "vue";
import {useKeyboard} from "@/composables/keyboard";

const wrappers = [];
afterEach(() => { for (const wrapper of wrappers.splice(0)) wrapper.unmount(); });

function keyboard() {
    const commands = [];
    let api;
    wrappers.push(mount(defineComponent({
        setup() {
            api = useKeyboard({onKeyDown: key => commands.push(key), setEditing: vi.fn()},
                {doScrolling: vi.fn()}, ref(20));
            return () => null;
        },
    })));
    const press = (code, modifiers = {}) => {
        const event = new KeyboardEvent("keydown", {code, bubbles: true, cancelable: true, ...modifiers});
        document.dispatchEvent(event);
        return event;
    };
    return {commands, api, press};
}

describe("reading shortcut ownership", () => {
    it("keeps editor copy, bold and Option navigation in the editor", async () => {
        const {commands, api, press} = keyboard();
        api.editorMode.value++;
        await nextTick();
        for (const [code, modifiers] of [
            ["KeyB", {ctrlKey: true}], ["KeyC", {ctrlKey: true}],
            ["ArrowDown", {altKey: true}], ["ArrowLeft", {altKey: true}],
        ]) expect(press(code, modifiers).defaultPrevented).toBe(false);
        expect(commands).toEqual([]);
        expect(press("KeyF", {ctrlKey: true}).defaultPrevented).toBe(true);
        expect(commands).toEqual(["CtrlKeyF"]);
    });

    it("does not reinterpret Command or mixed Control+Option as reading chords", () => {
        const {commands, press} = keyboard();
        for (const modifiers of [{metaKey: true, ctrlKey: true}, {ctrlKey: true, altKey: true}]) {
            expect(press("KeyF", modifiers).defaultPrevented).toBe(false);
        }
        expect(commands).toEqual([]);
    });

    it("does not swallow retired Option+W and unmapped Option+G", () => {
        const {commands, press} = keyboard();
        expect(press("KeyW", {altKey: true}).defaultPrevented).toBe(false);
        expect(press("KeyG", {altKey: true}).defaultPrevented).toBe(false);
        expect(commands).toEqual([]);
    });

    it("uses one native owner on iOS even when WebKit also sends the DOM keydown", () => {
        window.webkit = {messageHandlers: {bridge: {}}};
        try {
            const {commands, press} = keyboard();
            press("KeyF", {ctrlKey: true});
            expect(commands).toEqual([]);
            window.dispatchEvent(new CustomEvent("andbible-native-reading-key", {
                detail: {code: "KeyF", ctrlKey: true},
            }));
            expect(commands).toEqual(["CtrlKeyF"]);
        } finally { delete window.webkit; }
    });

    it("preserves the key value required by priority handlers on the native route", () => {
        const {commands, api} = keyboard();
        let receivedKey;
        // Register within a component so the composable's mount/unmount owns the handler.
        wrappers.push(mount(defineComponent({setup() {
            api.setupKeyboardListener(e => { receivedKey = e.key; return e.key === "b"; });
            return () => null;
        }})));
        window.dispatchEvent(new CustomEvent("andbible-native-reading-key", {
            detail: {code: "KeyB", ctrlKey: true},
        }));
        expect(receivedKey).toBe("b");
        expect(commands).toEqual([]);
    });

    it("checks editor ownership at dispatch time for the native iOS route", async () => {
        const {commands, api} = keyboard();
        // Prove the native route is live before checking the editor gate.
        window.dispatchEvent(new CustomEvent("andbible-native-reading-key", {
            detail: {code: "KeyB", ctrlKey: true},
        }));
        expect(commands).toEqual(["CtrlKeyB"]);
        api.editorMode.value++;
        // No nextTick: a delayed native setEditing notification must not decide ownership.
        window.dispatchEvent(new CustomEvent("andbible-native-reading-key", {
            detail: {code: "KeyB", ctrlKey: true},
        }));
        expect(commands).toEqual(["CtrlKeyB"]);
    });

    it("delivers each supported reading chord once", () => {
        const {commands, press} = keyboard();
        for (const code of ["KeyF", "KeyB", "KeyC", "KeyW"]) press(code, {ctrlKey: true});
        for (const code of ["ArrowDown", "ArrowUp", "ArrowLeft", "ArrowRight", "KeyM", "KeyO"]) {
            press(code, {altKey: true});
        }
        expect(commands).toEqual(["CtrlKeyF", "CtrlKeyB", "CtrlKeyC", "CtrlKeyW", "AltArrowDown",
            "AltArrowUp", "AltArrowLeft", "AltArrowRight", "AltKeyM", "AltKeyO"]);
    });
});
