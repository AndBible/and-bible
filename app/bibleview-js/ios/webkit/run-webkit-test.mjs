// Loads the production build in headless WebKit with the iOS shim and a stub messageHandlers,
// replays the start-up sequence the Kotlin controller sends, and checks the bridge round trip.
import {webkit} from "playwright";
import {readFileSync} from "node:fs";
import {resolve} from "node:path";
import {pathToFileURL} from "node:url";

const root = resolve(import.meta.dirname, "../..");
const shim = readFileSync(resolve(root, "ios/ios-shim.js"), "utf8");
const doc = readFileSync(resolve(root, "ios/poc-document.json"), "utf8");
const fail = (m) => { console.error("FAIL:", m); process.exit(1); };

const browser = await webkit.launch();
const page = await browser.newPage({viewport: {width: 390, height: 844}});
const errors = [];
page.on("pageerror", e => errors.push(String(e)));
await page.addInitScript(() => {
    window.__posted__ = [];
    window.webkit = {messageHandlers: {bridge: {postMessage: m => window.__posted__.push(m)}}};
});
await page.addInitScript(shim);
await page.goto(pathToFileURL(resolve(root, "dist/index.html")).href + "?lang=en&night=false");
await page.waitForFunction(() => window.__posted__.some(m => m.method === "setClientReady"), null, {timeout: 10000})
    .catch(() => fail("setClientReady never posted"));
await page.evaluate(([d]) => {
    bibleView.emit("set_config", {config: {}, appSettings: {activeWindow: true, nightMode: false, windowId: "w1"}, initial: true});
    bibleView.emit("clear_document");
    bibleView.emit("add_documents", JSON.parse(d));
}, [doc]);
await page.waitForFunction(() => document.body.innerText.includes("quickened"), null, {timeout: 10000})
    .catch(() => fail("document did not render"));
await page.mouse.wheel(0, 2000);
await page.waitForFunction(() => window.__posted__.some(m => m.method === "scrolledToOrdinal"), null, {timeout: 5000})
    .catch(() => fail("scrolledToOrdinal never posted"));
await page.screenshot({path: resolve(root, "ios/webkit/last-run.png")});
await browser.close();
if (errors.length) fail("page errors: " + errors.join(" | "));
console.log("PASS: WebKit shim round trip");
