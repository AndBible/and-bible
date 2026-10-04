// Regression check for the landing hero (not part of CI; needs the built site served).
//
// Usage: NODE_PATH=<dir with node_modules/playwright> CHROME=<chromium headless shell> \
//   node website/tests/hero-layout.mjs [--base http://localhost:8000] [--out <scratch dir for screenshots>]
//
// 1. At 1280x800 the whole hero panel is above the fold (bottom <= viewport height).
// 2. The "Get the app" card is at most 420 px tall at 1280 and 1440 px.
// 3. No horizontal overflow at 360, 768 and 1024 px.
// Exits 1 on any failure.
import {createRequire} from 'node:module';
import {mkdirSync} from 'node:fs';

const require = createRequire(import.meta.url);
const {chromium} = require(require.resolve('playwright', {paths: [process.cwd(), ...(process.env.NODE_PATH || '').split(':').filter(Boolean)]}));
const args = process.argv.slice(2);
let base = 'http://localhost:8000', out = null;
for (let i = 0; i < args.length; i++) {
  if (args[i] === '--base') base = args[++i];
  else if (args[i] === '--out') out = args[++i];
}
if (out) mkdirSync(out, {recursive: true});
const browser = await chromium.launch(process.env.CHROME ? {executablePath: process.env.CHROME} : {});
let failures = 0;
const check = (name, ok, detail = '') => { console.log(ok ? 'PASS' : 'FAIL', name, detail); if (!ok) failures++; };
const open = async (width, height, colorScheme = 'light') => {
  const page = await (await browser.newContext({viewport: {width, height}, colorScheme})).newPage();
  await page.goto(`${base}/`);
  await page.waitForTimeout(300);
  return page;
};
const box = (page, sel) => page.evaluate(s => { const r = document.querySelector(s).getBoundingClientRect();
  return {top: r.top, bottom: r.bottom, height: r.height}; }, sel);

for (const [w, h] of [[1280, 800], [1440, 900]]) {
  const page = await open(w, h);
  const card = await box(page, '.getapp');
  const panel = await box(page, '.hero__panel');
  check(`${w}: card height <= 420`, card.height <= 420, `${Math.round(card.height)}px`);
  check(`${w}: hero panel above the fold`, panel.bottom <= h, `bottom ${Math.round(panel.bottom)} of ${h}`);
  if (out && w === 1280) {
    await page.screenshot({path: `${out}/hero-v2-1280-light.png`});
    const dark = await open(w, h, 'dark');
    await dark.screenshot({path: `${out}/hero-v2-1280-dark.png`});
  }
}
for (const w of [360, 768, 1024]) {
  const page = await open(w, 800);
  const over = await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth);
  check(`${w}: no horizontal overflow`, over <= 0, `${over}px`);
  if (out && w !== 1024) await page.screenshot({path: `${out}/hero-v2-${w}-light.png`, fullPage: false});
}
await browser.close();
process.exit(failures ? 1 : 0);
