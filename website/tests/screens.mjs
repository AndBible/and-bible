// Visual review helper for the built site (not part of CI).
//
// Usage (site served on :8000 via `make site-serve`):
//   NODE_PATH=<dir with node_modules/playwright> node website/tests/screens.mjs \
//       --out <scratch>/screens [--base http://localhost:8000] /blog/ /privacy/ ...
//   (or run it with `npx -y playwright@latest`-installed playwright resolvable from NODE_PATH)
// Set CHROME=<path to chromium/headless shell> to use a specific browser binary.
//
// Writes <out>/<slug>-<width>-<scheme>.png for widths 360 and 1280 and schemes dark and light,
// prints whether each page overflows horizontally, and exits 1 if any page does.
import {createRequire} from 'node:module';
import {mkdirSync} from 'node:fs';

const require = createRequire(import.meta.url);
const {chromium} = require(require.resolve('playwright', {paths: [process.cwd(), ...(process.env.NODE_PATH || '').split(':').filter(Boolean)]}));

const args = process.argv.slice(2);
let out = 'screens', base = 'http://localhost:8000';
const paths = [];
for (let i = 0; i < args.length; i++) {
  if (args[i] === '--out') out = args[++i];
  else if (args[i] === '--base') base = args[++i];
  else paths.push(args[i]);
}
mkdirSync(out, {recursive: true});

const browser = await chromium.launch(process.env.CHROME ? {executablePath: process.env.CHROME} : {});
let overflow = false;
for (const path of paths) {
  const slug = path.replace(/^\/|\/$/g, '').replace(/\//g, '-') || 'home';
  for (const width of [360, 1280]) {
    for (const scheme of ['dark', 'light']) {
      const ctx = await browser.newContext({viewport: {width, height: 900}, colorScheme: scheme});
      const page = await ctx.newPage();
      await page.goto(base + path, {waitUntil: 'networkidle'});
      const [sw, iw] = await page.evaluate(() => [document.documentElement.scrollWidth, window.innerWidth]);
      const bad = sw > iw;
      overflow ||= bad;
      await page.screenshot({path: `${out}/${slug}-${width}-${scheme}.png`, fullPage: true});
      console.log(`${path} ${width} ${scheme} scrollWidth=${sw} inner=${iw} ${bad ? 'OVERFLOW' : 'ok'}`);
      await ctx.close();
    }
  }
}
await browser.close();
process.exit(overflow ? 1 : 0);
