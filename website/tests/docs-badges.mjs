// Regression check for the install badges on /docs/getting_started/ (not part of CI; needs the built site served).
//
// Usage: NODE_PATH=<dir with node_modules/playwright> CHROME=<chromium headless shell> \
//   node website/tests/docs-badges.mjs [--base http://localhost:8000] [--out <scratch dir for screenshots>]
//
// At 360 and 1280 px, light and dark: the four badges must have equal rendered heights (0.5 px), the
// same top (one row), and the page must not overflow horizontally. Exits 1 on any failure.
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
for (const width of [360, 1280]) for (const scheme of ['light', 'dark']) {
  const page = await (await browser.newContext({viewport: {width, height: 800}, colorScheme: scheme})).newPage();
  await page.goto(`${base}/docs/getting_started/`);
  await page.waitForLoadState('networkidle');
  const r = await page.evaluate(() => ({
    boxes: [...document.querySelectorAll('.ab-badges img')].map(i => i.getBoundingClientRect()).map(b => ({h: b.height, top: b.top})),
    overflow: document.documentElement.scrollWidth > innerWidth,
  }));
  const hs = r.boxes.map(b => b.h), tops = r.boxes.map(b => b.top);
  const ok = r.boxes.length === 4 && Math.max(...hs) - Math.min(...hs) <= 0.5 &&
    Math.max(...tops) - Math.min(...tops) <= 0.5 && !r.overflow && Math.min(...hs) > 0;
  console.log(ok ? 'PASS' : 'FAIL', `${width} ${scheme}`, `heights=${hs.map(h => h.toFixed(2))} tops=${tops.map(t => t.toFixed(1))} overflow=${r.overflow}`);
  if (!ok) failures++;
  if (out) await page.locator('.ab-badges').screenshot({path: `${out}/badges-${width}-${scheme}.png`});
}
await browser.close();
process.exit(failures ? 1 : 0);
