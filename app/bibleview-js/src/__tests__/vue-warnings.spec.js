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

import {describe, expect, it, vi} from "vitest";
import {installVueWarningFilter, shouldSuppressVueWarning} from "@/composables/vue-warnings";

describe("shouldSuppressVueWarning", () => {
    it("drops the OSIS prop-declaration noise, verbatim as Vue words it", () => {
        // These are the exact messages logcat filled up with on every document load.
        expect(shouldSuppressVueWarning(`Missing required prop: "canonical"`)).toBe(true);
        expect(shouldSuppressVueWarning(`Missing required prop: "short"`)).toBe(true);
    });

    it("keeps warnings that indicate a real defect", () => {
        // The injection warning is a different class and stays visible on purpose.
        expect(shouldSuppressVueWarning(`injection "Symbol(verseInfo)" not found.`)).toBe(false);
        expect(shouldSuppressVueWarning("Unhandled error during execution of native event handler")).toBe(false);
        expect(shouldSuppressVueWarning("Failed to resolve component: Foo")).toBe(false);
        expect(shouldSuppressVueWarning(`Invalid prop: type check failed for prop "canonical"`)).toBe(false);
    });

    it("only matches at the start, so a message merely mentioning the phrase survives", () => {
        expect(shouldSuppressVueWarning("Component emitted: Missing required prop: hack")).toBe(false);
    });
});

describe("installVueWarningFilter", () => {
    function fakeApp(existingHandler) {
        return {config: {warnHandler: existingHandler}};
    }

    it("swallows a suppressed warning instead of forwarding it", () => {
        const previous = vi.fn();
        const app = fakeApp(previous);
        installVueWarningFilter(app);

        app.config.warnHandler(`Missing required prop: "short"`, null, " at <Title>");

        expect(previous).not.toHaveBeenCalled();
    });

    it("forwards everything else to the handler that was already installed", () => {
        const previous = vi.fn();
        const app = fakeApp(previous);
        installVueWarningFilter(app);

        app.config.warnHandler("Failed to resolve component: Foo", null, " at <BibleView>");

        expect(previous).toHaveBeenCalledWith("Failed to resolve component: Foo", null, " at <BibleView>");
    });

    it("re-emits in Vue's default format when there is no previous handler, so logcat is unchanged", () => {
        const app = fakeApp(undefined);
        installVueWarningFilter(app);
        const warn = vi.spyOn(console, "warn").mockImplementation(() => {});

        try {
            app.config.warnHandler("Failed to resolve component: Foo", null, " at <BibleView>");
            expect(warn).toHaveBeenCalledWith("[Vue warn]: Failed to resolve component: Foo at <BibleView>");
        } finally {
            warn.mockRestore();
        }
    });
});
