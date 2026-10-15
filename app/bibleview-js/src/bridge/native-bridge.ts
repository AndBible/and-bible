/*
 * Copyright (c) 2026 Martin Denham, Tuomas Airaksinen and the AndBible contributors.
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

// Makes `window.android.<method>(...args)` reach native code on both platforms (L2a spec §3.3):
// Android posts JSON text to `window.androidBridge` (AndroidBridgeEndpoint.kt), iOS posts an object
// to `webkit.messageHandlers.bridge` (IosBridgeMessageHandler.kt). Both end in the common Kotlin
// BridgeDispatcher. Imported first by main.ts so nothing touches window.android before it exists.

type Post = (method: string, args: unknown[]) => void;
type BridgeWindow = Omit<Window, "android"> & Record<string, any>;

// Captured before main.ts patches console to call window.android.console (no recursion without a transport).
const nativeWarn = console.warn.bind(console);

function transport(w: BridgeWindow): Post | null {
    const android = w.androidBridge;
    if (android) return (method, args) => android.postMessage(JSON.stringify({method, args}));
    const ios = w.webkit?.messageHandlers?.bridge;
    if (ios) return (method, args) => ios.postMessage({method, args});
    return null;
}

export function installNativeBridge(w: BridgeWindow = window as BridgeWindow): void {
    if (w.android) return;
    const post: Post = (method, args) => {
        const clean = args.map(a => (a === undefined ? null : a));
        const t = transport(w);
        if (t) t(method, clean);
        else nativeWarn(`native bridge: no transport for ${method}`);
    };
    w.android = new Proxy({}, {
        get(_target, prop) {
            // Not bridge methods: keeps the proxy from looking thenable/serialisable/iterable to the runtime.
            if (typeof prop === "symbol" || prop === "then" || prop === "toJSON") return undefined;
            if (prop === "getActiveLanguages") {
                // The one synchronous call; postMessage cannot return a value.
                return () => (w.androidBridge ? w.androidBridge.getActiveLanguages() : (w.__activeLanguages__ || '["en"]'));
            }
            return (...args: unknown[]) => post(String(prop), args);
        },
    });
    if (!w.__nativeBridgeListening__) {
        w.__nativeBridgeListening__ = true;
        w.addEventListener("error", (e: ErrorEvent) => post("console", ["error", `${e.message} (${e.filename}:${e.lineno})`]));
        w.addEventListener("unhandledrejection", (e: PromiseRejectionEvent) => post("console", ["error", `unhandled rejection: ${String(e.reason)}`]));
    }
}

installNativeBridge();
