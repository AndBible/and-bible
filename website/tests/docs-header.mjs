// Regression check for the docs header (not part of CI; needs the built site served).
//
// Usage: NODE_PATH=<dir with node_modules/playwright> CHROME=<chromium headless shell> \
//   node website/tests/docs-header.mjs [--base http://localhost:8000] [--out <scratch dir for screenshots>]
//
// 1. The header links must not move when the search box is focused or typed into (1280, 900, 760 px).
// 2. No overflow and no wrapped header (60 px tall) at 360, 720, 760, 800, 1280 px.
// 3. Theme persistence: landing <-> docs <-> blog share `andbible-theme`, also against the OS preference,
//    with the scheme already on <body> at DOMContentLoaded (no flash), and a blocked localStorage.
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

// 1. links stay put
for (const width of [1280, 900, 760]) {
  const page = await (await browser.newContext({viewport: {width, height: 800}})).newPage();
  await page.goto(`${base}/docs/ai/`);
  const xs = () => page.$$eval('.ab-nav a, [data-theme-toggle]', els => els.map(e => Math.round(e.getBoundingClientRect().x * 10) / 10));
  const before = await xs();
  await page.click(await page.isVisible('.md-search__button') ? '.md-search__button' : 'label[for=__search]');
  await page.waitForTimeout(500);
  const focused = await xs();
  await page.keyboard.type('bookmark');
  await page.waitForTimeout(500);
  const typed = await xs();
  check(`links+toggle x unchanged at ${width}px`, JSON.stringify(before) === JSON.stringify(focused) && JSON.stringify(before) === JSON.stringify(typed),
        `before=${before} focused=${focused} typed=${typed}`);
}

// 2. overflow / wrapping
for (const width of [360, 720, 760, 800, 1280]) {
  const page = await (await browser.newContext({viewport: {width, height: 800}})).newPage();
  await page.goto(`${base}/docs/ai/`);
  const m = await page.evaluate(() => ({over: document.documentElement.scrollWidth - innerWidth,
    h: Math.round(document.querySelector('.md-header').getBoundingClientRect().height)}));
  if (out) await page.screenshot({path: `${out}/docs-header-${width}.png`, clip: {x: 0, y: 0, width, height: 120}});
  check(`no overflow, single-row header at ${width}px`, m.over <= 0 && m.h <= 62, JSON.stringify(m));
}

// 3. theme persistence
const scheme = p => p.evaluate(() => document.body.getAttribute('data-md-color-scheme'));
const theme = p => p.evaluate(() => document.documentElement.dataset.theme || null);
const withEarly = async os => {
  const c = await browser.newContext({colorScheme: os, viewport: {width: 1280, height: 800}});
  await c.addInitScript(() => document.addEventListener('DOMContentLoaded', () => { window.__early = document.body.getAttribute('data-md-color-scheme'); }));
  return c;
};
for (const os of ['light', 'dark']) {
  const opposite = os === 'light' ? 'dark' : 'light', want = {light: 'default', dark: 'slate'};
  let c = await withEarly(os), p = await c.newPage();
  await p.goto(`${base}/`);
  await p.click('[data-theme-toggle]');
  check(`os=${os}: landing toggle -> ${opposite}`, await theme(p) === opposite);
  await p.goto(`${base}/docs/ai/`);
  check(`os=${os}: docs shows stored ${opposite}`, await scheme(p) === want[opposite]);
  check(`os=${os}: scheme set at DOMContentLoaded`, await p.evaluate(() => window.__early) === want[opposite]);
  await p.click('[data-theme-toggle]');
  check(`os=${os}: docs toggle -> ${os}`, await scheme(p) === want[os] && await p.evaluate(() => localStorage.getItem('andbible-theme')) === os);
  await p.click('[data-theme-toggle]');
  await p.goto(`${base}/`); check(`os=${os}: landing follows docs`, await theme(p) === opposite);
  await p.goto(`${base}/blog/`); check(`os=${os}: blog follows docs`, await theme(p) === opposite);
  await c.close();
  c = await withEarly(os); p = await c.newPage(); await p.goto(`${base}/docs/`);
  check(`os=${os}: nothing stored -> docs follows OS`, await scheme(p) === want[os] && await p.evaluate(() => window.__early) === want[os]);
  await c.close();
}
const blocked = await withEarly('dark');
await blocked.addInitScript(() => Object.defineProperty(window, 'localStorage', {get() { throw new Error('blocked'); }}));
const bp = await blocked.newPage(); await bp.goto(`${base}/docs/`);
check('blocked localStorage -> OS preference', await scheme(bp) === 'slate');
await browser.close();
process.exit(failures ? 1 : 0);
