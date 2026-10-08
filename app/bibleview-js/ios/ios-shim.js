// Injected at document start on iOS (WKUserScript). Makes BibleView's `window.android` calls reach
// Kotlin through webkit.messageHandlers.bridge, so the Vue code is unchanged. Plain ES2017, no imports:
// it runs before any module and is not processed by Vite.
(function () {
    // Idempotent on window.android (the object this shim owns), not on a flag, so a re-injection
    // after window.android was removed still installs the proxy. Error listeners attach only once.
    if (window.android) return;
    function post(method, args) {
        window.webkit.messageHandlers.bridge.postMessage({method: method, args: args});
    }
    window.android = new Proxy({}, {
        get: function (_target, prop) {
            // Not bridge methods: keeps the proxy from looking thenable/serialisable/iterable to the runtime.
            if (typeof prop === "symbol" || prop === "then" || prop === "toJSON") return undefined;
            if (prop === "getActiveLanguages") {
                // The only bridge method with a synchronous return value; postMessage cannot return.
                return function () { return window.__activeLanguages__ || '["en"]'; };
            }
            return function () { post(String(prop), Array.prototype.slice.call(arguments)); };
        },
    });
    if (!window.__iosShimListening__) {
        window.__iosShimListening__ = true;
        window.addEventListener("error", function (e) {
            post("console", ["error", e.message + " (" + e.filename + ":" + e.lineno + ")"]);
        });
        window.addEventListener("unhandledrejection", function (e) {
            post("console", ["error", "unhandled rejection: " + String(e.reason)]);
        });
    }
})();
