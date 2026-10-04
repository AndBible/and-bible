// Check that the "Watch on YouTube" link is hidden on video cards only when JavaScript runs
// (not part of CI; needs the built site served).
//
// Usage: NODE_PATH=<dir with node_modules/playwright> CHROME=<chromium headless shell> \
//   node website/tests/video-card-link.mjs [--base http://localhost:8000] [--out <scratch dir>] [--post <blog path>]
import {createRequire} from 'node:module';
import {mkdirSync} from 'node:fs';

const require = createRequire(import.meta.url);
const {chromium} = require(require.resolve('playwright', {paths: [process.cwd(), ...(process.env.NODE_PATH || '').split(':').filter(Boolean)]}));
const args = process.argv.slice(2);
let base = 'http://localhost:8000', out = null,
  post = '/2024/02/01/how-to-change-how-a-bookmark-is-visualized-with-main-label/';
for (let i = 0; i < args.length; i++) {
  if (args[i] === '--base') base = args[++i];
  else if (args[i] === '--out') out = args[++i];
  else if (args[i] === '--post') post = args[++i];
}
if (out) mkdirSync(out, {recursive: true});
const browser = await chromium.launch(process.env.CHROME ? {executablePath: process.env.CHROME} : {});
let failures = 0;
const check = (name, ok, detail = '') => { console.log(ok ? 'PASS' : 'FAIL', name, detail); if (!ok) failures++; };

async function links(path, javaScriptEnabled, shot) {
  const context = await browser.newContext({viewport: {width: 1280, height: 900}, colorScheme: 'light', javaScriptEnabled});
  const page = await context.newPage();
  await page.goto(base + path, {waitUntil: 'networkidle'});
  const sel = path === post ? '.yt:not(.yt--card) .yt__link' : '.yt--card .yt__link';
  const all = await page.$$(sel);
  const visible = [];
  for (const a of all) visible.push(await a.isVisible());
  if (shot && out) await page.screenshot({path: `${out}/${shot}.png`, fullPage: true});
  await context.close();
  return {total: all.length, visible: visible.filter(Boolean).length};
}

for (const [path, shot] of [['/videos/', 'videos-nolink-1280-light'], ['/', 'home-nolink-1280-light']]) {
  const on = await links(path, true, shot);
  check(`${path} cards exist`, on.total > 0, `n=${on.total}`);
  check(`${path} JS on: card links hidden`, on.visible === 0, `visible=${on.visible}`);
  const off = await links(path, false);
  check(`${path} JS off: card links visible`, off.total > 0 && off.visible === off.total, `${off.visible}/${off.total}`);
}
const blog = await links(post, true);
check('blog embed keeps its link with JS', blog.total > 0 && blog.visible === blog.total, `${blog.visible}/${blog.total}`);
await browser.close();
process.exit(failures ? 1 : 0);
