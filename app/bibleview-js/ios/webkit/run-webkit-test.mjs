// Loads the production build in headless WebKit with the iOS shim and a stub messageHandlers,
// replays the start-up sequence the Kotlin controller sends, and checks the bridge round trip.
import {readFileSync, createReadStream, statSync} from "node:fs";
import {createServer} from "node:http";
import {resolve, extname, sep} from "node:path";

const root = resolve(import.meta.dirname, "../..");
const shim = readFileSync(resolve(root, "ios/ios-shim.js"), "utf8");
const doc = readFileSync(resolve(root, "ios/poc-document.json"), "utf8");

const MIME = {
    ".html": "text/html; charset=utf-8", ".js": "text/javascript; charset=utf-8", ".mjs": "text/javascript; charset=utf-8",
    ".css": "text/css; charset=utf-8", ".json": "application/json", ".svg": "image/svg+xml",
    ".woff2": "font/woff2", ".woff": "font/woff", ".ttf": "font/ttf", ".png": "image/png",
    ".jpg": "image/jpeg", ".jpeg": "image/jpeg", ".gif": "image/gif", ".ico": "image/x-icon",
    ".map": "application/json", ".wasm": "application/wasm", ".txt": "text/plain; charset=utf-8",
};

/** Tiny static file server for `dir`; rejects anything resolving outside it. */
export function serveDir(dir) {
    const base = resolve(dir);
    const server = createServer((req, res) => {
        let path;
        try {
            path = decodeURIComponent(new URL(req.url, "http://x").pathname);
        } catch {
            res.writeHead(400).end();
            return;
        }
        if (path.includes("\0")) { res.writeHead(400).end(); return; }
        if (path.endsWith("/")) path += "index.html";
        const file = resolve(base, "." + path);
        if (file !== base && !file.startsWith(base + sep)) { res.writeHead(403).end(); return; }
        let st;
        try { st = statSync(file); } catch { res.writeHead(404).end(); return; }
        if (!st.isFile()) { res.writeHead(404).end(); return; }
        res.writeHead(200, {"Content-Type": MIME[extname(file).toLowerCase()] ?? "application/octet-stream"});
        createReadStream(file).pipe(res);
    });
    return new Promise(ok => server.listen(0, "127.0.0.1", () => ok(server)));
}

if (process.argv[2] === "--serve-only") {
    // Server sanity check without Playwright: node run-webkit-test.mjs --serve-only <dir>
    const s = await serveDir(process.argv[3]);
    console.log(s.address().port);
} else {
await run();
}

async function run() {
const {webkit} = await import("playwright"); // lazy so --serve-only works without it installed
// The iOS app uses loadFileURL with read access to dist/; Playwright's file:// has no equivalent and
// module scripts (Vite emits type="module" crossorigin) over file:// are blocked/unreliable, so serve over http.
const server = await serveDir(resolve(root, "dist"));
const fail = async (m) => {
    console.error("FAIL:", m);
    await browser?.close().catch(() => {});
    server.close();
    process.exit(1);
};
let browser;
try {
browser = await webkit.launch();
const page = await browser.newPage({viewport: {width: 390, height: 844}});
const errors = [];
page.on("pageerror", e => errors.push(String(e)));
await page.addInitScript(() => {
    window.__posted__ = [];
    window.webkit = {messageHandlers: {bridge: {postMessage: m => window.__posted__.push(m)}}};
});
await page.addInitScript(shim);
await page.goto(`http://127.0.0.1:${server.address().port}/index.html?lang=en&night=false`);
await page.waitForFunction(() => window.__posted__.some(m => m.method === "setClientReady"), null, {timeout: 10000})
    .catch(() => fail("setClientReady never posted"));
await page.evaluate(([d]) => {
    window.bibleView.emit("set_config", {config: {}, appSettings: {activeWindow: true, nightMode: false, windowId: "w1"}, initial: true});
    window.bibleView.emit("clear_document");
    window.bibleView.emit("add_documents", JSON.parse(d));
}, [doc]);
await page.waitForFunction(() => document.body.innerText.includes("quickened"), null, {timeout: 10000})
    .catch(() => fail("document did not render"));
// Stay inside the text: the fixture (Eph 2) is ~2600 px tall, and a 2000 px wheel scrolls past the last verse
// into the #bottom spacer, where the verse notifier finds no .ordinal under its probe and posts nothing.
await page.mouse.move(195, 422);
await page.mouse.wheel(0, 400);
await page.waitForFunction(() => window.__posted__.some(m => m.method === "scrolledToOrdinal"), null, {timeout: 5000})
    .catch(() => fail("scrolledToOrdinal never posted"));
await page.screenshot({path: resolve(root, "ios/webkit/last-run.png")});
await browser.close();
browser = undefined;
if (errors.length) await fail("page errors: " + errors.join(" | "));
console.log("PASS: WebKit shim round trip");
} catch (e) {
    await fail(String(e?.stack ?? e));
}
server.close();
}
