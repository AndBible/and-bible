// Regression check for the docs page layout (not part of CI; needs the built site served).
//
// Usage: NODE_PATH=<dir with node_modules/playwright> CHROME=<chromium headless shell> \
//   node website/tests/docs-layout.mjs [--base http://localhost:8000] [--out <scratch dir for screenshots>]
//
// 1. Left sidebar permanently visible (hamburger hidden) from 60em (960 px); drawer + hamburger below it.
// 2. Right-hand TOC only from 76.25em; the text column is at most 640 px (and over 560 px) at 1280 px.
// 3. No horizontal overflow at 360, 800, 1000 px (docs page and landing).
// 4. The left nav stays between the header and the footer / viewport bottom, at the top and the end of the page.
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
const open = async (width, path, colorScheme = 'light', height = 800) => {
  const page = await (await browser.newContext({viewport: {width, height}, colorScheme})).newPage();
  await page.goto(`${base}${path}`);
  return page;
};
const visible = (page, sel) => page.evaluate(s => { const e = document.querySelector(s); if (!e) return false;
  const r = e.getBoundingClientRect(); return getComputedStyle(e).display !== 'none' && r.width > 0 && r.right > 0 && r.left < innerWidth; }, sel);

// 1. + 2. sidebars and hamburger (below 60em the theme turns the TOC into a floating button, not checked here)
for (const [width, nav, burger, toc] of [[800, false, true, null], [959, false, true, null], [1000, true, false, false], [1100, true, false, false],
                                          [1280, true, false, true], [1440, true, false, true]]) {
  const page = await open(width, '/docs/windows/');
  const got = [await visible(page, '.md-sidebar--primary'), await visible(page, '.md-header__button[for=__drawer]'), await visible(page, '.md-sidebar--secondary')];
  check(`${width}px: left nav ${nav ? 'shown' : 'hidden'}, hamburger ${burger ? 'shown' : 'hidden'}, TOC ${toc === null ? 'floating button' : toc ? 'shown' : 'hidden'}`,
        got[0] === nav && got[1] === burger && (toc === null || got[2] === toc), got.join());
}

// The drawer still works below the breakpoint.
{
  const page = await open(800, '/docs/windows/');
  await page.click('.md-header__button[for=__drawer]');
  await page.waitForTimeout(500);
  check('800px: hamburger opens the drawer', await visible(page, '.md-sidebar--primary'));
}

// Text column
for (const width of [1280, 1440, 1920]) {
  const page = await open(width, '/docs/windows/');
  const w = await page.evaluate(() => Math.round(document.querySelector('.md-typeset p').getBoundingClientRect().width));
  check(`${width}px: text column 560 < ${w} <= 700`, w > 560 && w <= 700);
  if (width === 1280) check('1280px: text column <= 640', w <= 640, String(w));
}

// 4. left nav inside header..footer (it ran over the footer between 60em and 76.25em at the end of the page)
for (const [width, height] of [[1000, 700], [1100, 1500], [1280, 700]]) {
  for (const path of ['/docs/', '/docs/support/']) {
    const page = await open(width, path, 'light', height);
    for (const where of ['top', 'end']) {
      if (where === 'end') { await page.evaluate(() => scrollTo(0, document.body.scrollHeight)); await page.waitForTimeout(300); }
      const r = await page.evaluate(() => {
        const nav = document.querySelector('.md-sidebar--primary .md-sidebar__scrollwrap').getBoundingClientRect();
        return {top: Math.round(nav.top), bottom: Math.round(nav.bottom), header: Math.round(document.querySelector('.md-header').getBoundingClientRect().bottom),
                footer: Math.round(document.querySelector('.md-footer').getBoundingClientRect().top), vh: innerHeight};
      });
      check(`${width}x${height} ${path} ${where}: left nav between header and footer`,
            r.top >= r.header && r.bottom <= Math.min(r.footer, r.vh), JSON.stringify(r));
    }
  }
}

// 3. overflow, including a wide table, code block and the landing page
for (const width of [360, 800, 1000]) {
  for (const path of ['/docs/windows/', '/docs/', '/docs/getting_started/']) {
    const page = await open(width, path);
    const over = await page.evaluate(() => document.documentElement.scrollWidth - innerWidth);
    check(`no overflow at ${width}px on ${path}`, over <= 0, String(over));
  }
}

// Screenshots
if (out) for (const [width, scheme] of [[1000, 'light'], [1280, 'light'], [1280, 'dark']]) {
  for (const [path, name] of [['/docs/windows/', 'windows'], ['/docs/', 'landing']]) {
    const page = await open(width, path, scheme);
    await page.screenshot({path: `${out}/docs-layout-${name}-${width}${width === 1280 ? '-' + scheme : ''}.png`});
  }
}
await browser.close();
process.exit(failures ? 1 : 0);
