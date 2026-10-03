// Regression check for the home page reviews carousel (not part of CI; needs the built site served).
//
// Usage: NODE_PATH=<dir with node_modules/playwright> CHROME=<chromium headless shell> \
//   node website/tests/reviews-carousel.mjs [--base http://localhost:8000] [--out <scratch dir for screenshots>]
//
// Uses Playwright's fake clock for the 10 s auto-advance. Exits 1 on any failure.
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

const errors = [];
async function open(options = {}) {
  const context = await browser.newContext({viewport: {width: 1280, height: 900}, ...options});
  const page = await context.newPage();
  page.on('console', m => { if (m.type() === 'error') errors.push(m.text()); });
  page.on('pageerror', e => errors.push(String(e)));
  await page.clock.install();
  await page.goto(`${base}/`);
  return page;
}
const SEL = '[data-reviews-carousel]';
const active = page => page.$$eval(`${SEL} .review.is-active`, els => els.map(e => e.getAttribute('aria-label')));
const label = async page => (await active(page)).join('|');
const visibleCount = page => page.$$eval(`${SEL} .review`, els => els.filter(e => getComputedStyle(e).visibility !== 'hidden').length);

// 1. basics: one visible slide, controls, wrapping
let page = await open();
check('one active slide initially', (await active(page)).length === 1 && await label(page) === '1 of 20', await label(page));
check('only one slide visible/accessible', await visibleCount(page) === 1);
check('others are aria-hidden and inert', await page.$$eval(`${SEL} .review:not(.is-active)`, els => els.length === 19 && els.every(e => e.getAttribute('aria-hidden') === 'true' && e.inert)));
check('region roledescription + label', await page.$eval(SEL, e => e.getAttribute('aria-roledescription') === 'carousel' && !!e.getAttribute('aria-label')));
check('indicator reads 1 / 20', (await page.textContent('.review-position')) === '1 / 20');
const sizes = await page.$$eval('.review-controls button', bs => bs.map(b => [b.offsetWidth, b.offsetHeight]));
check('buttons are >= 44px', sizes.every(([w, h]) => w >= 44 && h >= 44), JSON.stringify(sizes));
async function clipOf(p) {
  const b = await (await p.$('.review-carousel')).boundingBox();
  const scrollY = await p.evaluate(() => window.scrollY);
  return {x: 0, y: Math.max(0, b.y + scrollY - 90), width: p.viewportSize().width, height: b.height + 190};
}
await page.click('[aria-label="Next review"]');
check('next -> 2 of 20', await label(page) === '2 of 20');
check('live region announced on user action', (await page.textContent(`${SEL} [aria-live=polite]`)) === '2 / 20');
await page.click('[aria-label="Previous review"]');
await page.click('[aria-label="Previous review"]');
check('prev wraps to 20 of 20', await label(page) === '20 of 20');
await page.click('[aria-label="Next review"]');
check('next wraps to 1 of 20', await label(page) === '1 of 20');
await page.focus(SEL);
await page.keyboard.press('ArrowRight');
check('ArrowRight moves', await label(page) === '2 of 20');
await page.keyboard.press('ArrowLeft');
check('ArrowLeft moves back', await label(page) === '1 of 20');
await page.context().close();

// 2. auto-advance, hover, user stop
page = await open();
await page.clock.runFor(10100);
check('auto-advances after 10 s', await label(page) === '2 of 20', await label(page));
check('no announcement on auto-advance', (await page.textContent(`${SEL} [aria-live=polite]`)) === '');
await page.hover(`${SEL} .review-list`);
await page.clock.runFor(35000);
check('hover pauses', await label(page) === '2 of 20', await label(page));
await page.mouse.move(5, 5);
await page.clock.runFor(10100);
check('leaving hover resumes', await label(page) === '3 of 20', await label(page));
check('pause toggle visible while rotating', await page.isVisible('.review-toggle') && (await page.getAttribute('.review-toggle', 'aria-label')).startsWith('Pause'));
await page.click('.review-toggle');
await page.mouse.move(5, 5);
await page.clock.runFor(35000);
check('pause button stops rotation', await label(page) === '3 of 20', await label(page));
await page.click('.review-toggle');
await page.mouse.move(5, 5);
await page.clock.runFor(10100);
check('play button resumes rotation', await label(page) === '4 of 20', await label(page));
await page.context().close();

page = await open();
await page.click('[aria-label="Next review"]');
await page.mouse.move(5, 5);
await page.clock.runFor(60000);
check('clicking next stops auto-advance for good', await label(page) === '2 of 20', await label(page));
check('toggle shows the play state after user stop', (await page.getAttribute('.review-toggle', 'aria-label')).startsWith('Start'));
await page.context().close();

page = await open();
await page.focus(`${SEL} [aria-label="Next review"]`);
await page.keyboard.press('Tab'); // keyboard focus inside the carousel
await page.clock.runFor(35000);
check('keyboard focus within pauses', await label(page) === '1 of 20', await label(page));
await page.context().close();

// 3. reduced motion: no auto-advance, no toggle, no transition
page = await open({reducedMotion: 'reduce'});
await page.clock.runFor(60000);
check('reduced motion: no auto-advance', await label(page) === '1 of 20');
check('reduced motion: no pause toggle', !(await page.$('.review-toggle')));
check('reduced motion: no transition', await page.$eval(`${SEL} .review`, e => parseFloat(getComputedStyle(e).transitionDuration) === 0));
await page.click('[aria-label="Next review"]');
check('reduced motion: buttons still work', await label(page) === '2 of 20');
await page.context().close();

// 4. JS disabled: plain list of all 20
{
  const context = await browser.newContext({javaScriptEnabled: false, viewport: {width: 1280, height: 900}});
  page = await context.newPage();
  await page.goto(`${base}/`);
  check('no JS: all 20 visible, no enhancement', await visibleCount(page) === 20 && !(await page.$('.is-carousel')) && !(await page.$('.review-controls')));
  await context.close();
}

// 5. overflow + screenshots
for (const [width, scheme] of [[360, 'light'], [360, 'dark'], [1280, 'dark'], [1280, 'light']]) {
  page = await open({viewport: {width, height: 900}, colorScheme: scheme});
  const over = await page.evaluate(() => document.documentElement.scrollWidth - innerWidth);
  check(`no horizontal overflow at ${width}px ${scheme}`, over <= 0, String(over));
  // the tallest slide must fit its stage: stage height equals the max card height
  const stage = await page.$eval(`${SEL} .review-list`, e => [e.getBoundingClientRect().height, Math.max(...[...e.children].map(c => c.getBoundingClientRect().height))]);
  check(`stage as tall as the tallest card at ${width}px`, Math.abs(stage[0] - stage[1]) < 1, JSON.stringify(stage));
  if (out) await page.screenshot({path: `${out}/reviews-carousel-${width}-${scheme}.png`, fullPage: true, clip: await clipOf(page)});
  await page.context().close();
}

check('no console errors', errors.length === 0, errors.join('; '));
await browser.close();
process.exit(failures ? 1 : 0);
