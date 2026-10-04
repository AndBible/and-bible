// Browser check for the animated phone in "Get the app" (not part of CI; needs the built site served).
//
// Usage: NODE_PATH=<dir with node_modules/playwright> CHROME=<chromium> \
//   node website/tests/appshots.mjs [--base http://localhost:8000] [--out <scratch dir for screenshots>]
//
// 1. With motion allowed exactly one screen is active, the screen scrolls up while it is shown, and the
//    next screen takes over after one full cycle; the card does not grow (<= 420 px) and nothing overflows.
// 2. With reduced motion the first screen stays static (no script classes, no running animation).
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
const state = (page) => page.evaluate(() => {
  const shots = [...document.querySelectorAll('.phone__shot')];
  const active = shots.findIndex((s) => s.classList.contains('is-active'));
  const img = active < 0 ? null : shots[active].querySelector('img');
  return {active, count: shots.length, animated: document.querySelector('.phone').classList.contains('is-animated'),
    y: img ? new DOMMatrix(getComputedStyle(img).transform).m42 : 0,
    overflow: document.documentElement.scrollWidth - innerWidth,
    card: document.querySelector('.getapp').getBoundingClientRect().height};
});

for (const scheme of ['light', 'dark']) {
  const page = await (await browser.newContext({viewport: {width: 1280, height: 800}, colorScheme: scheme})).newPage();
  const errors = [];
  page.on('pageerror', (e) => errors.push(String(e)));
  await page.goto(`${base}/`);
  await page.waitForTimeout(500);
  const first = await state(page);
  check(`${scheme}: animated, one active screen`, first.animated && first.active === 0 && first.count >= 2, JSON.stringify(first));
  await page.waitForTimeout(5000);
  const mid = await state(page);
  check(`${scheme}: screen scrolls up`, mid.active === 0 && mid.y < -40, `y=${mid.y}`);
  if (out) await page.locator('.hero').screenshot({path: `${out}/appshots-${scheme}-mid.png`});
  await page.waitForTimeout(6500);
  const next = await state(page);
  check(`${scheme}: next screen after one cycle`, next.active === 1, `active=${next.active}`);
  check(`${scheme}: card height <= 420 and no overflow`, next.card <= 420 && next.overflow <= 0, `${next.card} ${next.overflow}`);
  check(`${scheme}: no script errors`, errors.length === 0, errors.join(';'));
  await page.context().close();
}

for (const width of [360, 768]) {
  const page = await (await browser.newContext({viewport: {width, height: 800}})).newPage();
  await page.goto(`${base}/`);
  await page.waitForTimeout(500);
  const s = await state(page);
  check(`no horizontal overflow at ${width}px`, s.overflow <= 0, String(s.overflow));
  await page.context().close();
}

const calm = await (await browser.newContext({viewport: {width: 1280, height: 800}, reducedMotion: 'reduce'})).newPage();
await calm.goto(`${base}/`);
await calm.waitForTimeout(1500);
const still = await calm.evaluate(() => ({
  animated: document.querySelector('.phone').classList.contains('is-animated'),
  visible: [...document.querySelectorAll('.phone__shot')].filter((s) => getComputedStyle(s).display !== 'none').length,
  running: document.getAnimations().filter((a) => a.effect && a.effect.target && a.effect.target.closest && a.effect.target.closest('.phone')).length,
}));
check('reduced motion: static first screen', !still.animated && still.visible === 1 && still.running === 0, JSON.stringify(still));

await browser.close();
process.exit(failures ? 1 : 0);
