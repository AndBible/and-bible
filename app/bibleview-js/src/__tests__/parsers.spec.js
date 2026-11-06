import {afterEach, describe, expect, it, vi} from "vitest";
import {useParsers} from "@/composables/parsers";

describe("optional feature parsers", () => {
    afterEach(() => vi.unstubAllGlobals());

    it.each(["not-found", "network-error"])("keeps English references usable after a %s language parser", async (failure) => {
        // Only the file transport is replaced; the bundled English parser and feature loader are real.
        vi.stubGlobal("fetch", failure === "not-found"
            ? vi.fn().mockResolvedValue(new Response("", {status: 404}))
            : vi.fn().mockRejectedValue(new TypeError("network unavailable")));
        const {initialize, parse, parsers} = useParsers({getActiveLanguages: () => ["en", "fi"]});
        await initialize();
        await vi.waitFor(() => expect(parsers).toHaveLength(1));
        expect(parse("John 3:16")).toBe("John.3.16");
        expect(parse("not a Bible reference")).toBe("");
    });
});
