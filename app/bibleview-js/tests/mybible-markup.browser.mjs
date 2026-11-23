import assert from 'node:assert/strict';
import {after, before, test} from 'node:test';
import {mkdir} from 'node:fs/promises';
import {chromium} from 'playwright';
import {createServer} from 'vite';

// Output contract of MyBibleMarkupTest's generated SQLite fixture. Exercise real text layout,
// which jsdom and Roborazzi's WebView stand-in cannot observe.
const title = '<title canonical="false" subType="x-preverse">(' +
    '<reference osisRef="Mark.1.2-Mark.1.8">Mark 1:2-8</reference>; ' +
    '<reference osisRef="Luke.3.1-Luke.3.18">Luke 3:1-18</reference>; ' +
    '<reference osisRef="John.1.19-John.1.28">John 1:19-28</reference>) The preaching</title>';
const fixed = title + '<verse osisID="Matt.3.1" verseOrdinal="100">In those days.</verse>' +
    '<div subType="x-preverse"><pb/></div>' +
    '<verse osisID="Matt.3.2" verseOrdinal="101">Repent.<pb/>Second paragraph.</verse>';
const broken = '<verse osisID="Matt.3.1" verseOrdinal="100"><title canonical="false">' +
    '(<x>480 1:2-8; 490 3:1-18; 500 1:19-28</x>) The preaching</title>In those days.</verse>' +
    '<verse osisID="Matt.3.2" verseOrdinal="101"><pb/>Repent.<pb/>Second paragraph.</verse>';
let server;
let browser;
let origin;
before(async () => {
    server = await createServer({define: {'process.env.NODE_ENV': JSON.stringify('debug')}, server: {port: 0}});
    await server.listen();
    origin = server.resolvedUrls.local[0];
    browser = await chromium.launch({executablePath: process.env.CHROMIUM_PATH, headless: true, args: ['--no-sandbox']});
});
after(async () => {
    await browser?.close();
    await server?.close();
});

for (const mode of ['light', 'dark', 'monochrome', 'no-animations']) {
    test(`MyBible headings link to Scripture and verse number follows paragraph break (${mode})`, async () => {
        const page = await browser.newPage({viewport: {width: 360, height: 640}});
        try {
            await page.goto(origin);
            await page.waitForFunction(() => window.bibleViewDebug?.documents);
            const render = async xml => {
                const id = xml === fixed ? 'mybible-fixed' : 'mybible-before';
                await page.evaluate(({xml, mode, id}) => {
                    const {documents, config, appSettings} = window.bibleViewDebug;
                    config.showVerseNumbers = true;
                    config.showSectionTitles = true;
                    appSettings.nightMode = mode === 'dark';
                    appSettings.disableAnimations = mode === 'no-animations';
                    appSettings.monochromeMode = mode === 'monochrome';
                    appSettings.pureMonochromeMode = mode === 'monochrome';
                    documents.splice(0, Infinity, {
                        id, type: 'bible', bookInitials: 'MyBible-Fixture', bibleBookName: 'Matthew',
                        chapterNumber: 3, ordinalRange: [100, 101], bookmarks: [], aiDocMarkers: [],
                        osisFragment: {xml, key: 'fixture', features: {}, bookCategory: 'BIBLE', bookInitials: 'MyBible-Fixture'},
                    });
                }, {xml, mode, id});
                await page.locator(`#doc-${id} #v-101 .verseNumber`).waitFor();
                // The native host paints the WebView background; emulate that host surface here.
                await page.evaluate(() => {
                    const root = document.querySelector('#content').parentElement;
                    document.body.style.backgroundColor = getComputedStyle(root).getPropertyValue('--background-color');
                });
            };
            if (process.env.EVIDENCE_DIR) {
                await mkdir(process.env.EVIDENCE_DIR, {recursive: true});
                await render(broken);
                await page.screenshot({path: `${process.env.EVIDENCE_DIR}/before-${mode}.png`});
            }
            await render(fixed);
            const links = page.locator('.titleStyle a.reference');
            assert.equal(await links.count(), 3);
            assert.deepEqual(await links.evaluateAll(elements => elements.map(link => link.getAttribute('href'))), [
                'osis://?osis=Mark.1.2-Mark.1.8',
                'osis://?osis=Luke.3.1-Luke.3.18',
                'osis://?osis=John.1.19-John.1.28',
            ]);
            assert.ok(!(await page.locator('.titleStyle').innerText()).match(/480|490|500/));
            const positions = await page.locator('#v-101').evaluate(verse => {
                const number = verse.querySelector('.verseNumber').getBoundingClientRect();
                const text = [...verse.querySelector('.highlight-transition').childNodes].find(n => n.nodeType === Node.TEXT_NODE && n.textContent.includes('Repent.'));
                const range = document.createRange();
                range.selectNode(text);
                const words = range.getBoundingClientRect();
                return {numberTop: number.top, numberBottom: number.bottom, textTop: words.top, textBottom: words.bottom};
            });
            assert.ok(positions.numberBottom >= positions.textTop && positions.numberTop <= positions.textBottom,
                `verse number stranded on previous line: ${JSON.stringify(positions)}`);
            if (process.env.EVIDENCE_DIR) {
                await page.screenshot({path: `${process.env.EVIDENCE_DIR}/after-${mode}.png`});
            }
        } finally {
            await page.close();
        }
    });
}
