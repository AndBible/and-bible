import assert from 'node:assert/strict';
import {after, before, test} from 'node:test';
import {chromium} from 'playwright';
import {createServer} from 'vite';

// jsdom does not lay out text or apply media queries. Exercise the real Vue page in Chromium instead.
// Run: CHROMIUM_PATH=/opt/google/chrome/chrome node --test tests/memorize-tabs.browser.mjs
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

test('word typing input follows the active word through phone resize and scrolling', async () => {
    const page = await browser.newPage({viewport: {width: 320, height: 640}});
    try {
        await page.goto(`${origin}?lang=fi`);
        await page.waitForFunction(() => window.bibleViewDebug?.documents);
        const longText = Array.from({length: 180}, (_, i) => `word${i}`).join(' ');
        await page.evaluate(text => {
            window.bibleViewDebug.documents.splice(0, Infinity, {
                id: 'typing-position-test', type: 'memorize', title: 'Typing position',
                texts: [{key: 'long-verse', text}],
                state: {memorize: {mode: 'type', modeConfig: {typeConfig: {currentWordIndex: 130}}}},
            });
        }, longText);
        const textArea = page.locator('.type-text');
        await textArea.waitFor();
        const input = page.locator('.type-hidden-input');
        await textArea.click();

        const assertInputTracksWord = async () => {
            const metrics = await page.evaluate(() => {
                const input = document.querySelector('.type-hidden-input');
                const word = document.querySelector('.type-word.type-current');
                const inputRect = input.getBoundingClientRect();
                const wordRect = word.getBoundingClientRect();
                return {input: {left: inputRect.left, top: inputRect.top}, word: {left: wordRect.left, top: wordRect.top}};
            });
            assert.ok(Math.abs(metrics.input.left - metrics.word.left) < 1, JSON.stringify(metrics));
            assert.ok(Math.abs(metrics.input.top - metrics.word.top) < 1, JSON.stringify(metrics));
        };

        await assertInputTracksWord();
        await page.setViewportSize({width: 360, height: 640});
        await assertInputTracksWord();
        await page.evaluate(() => window.scrollTo(0, 800));
        await assertInputTracksWord();
        assert.equal(await input.evaluate(el => el === document.activeElement), true);

        await input.press('w');
        await page.waitForFunction(() => document.querySelector('.type-word.type-current')?.textContent === 'word131');
        await assertInputTracksWord();
        assert.equal(await input.evaluate(el => el === document.activeElement), true);

        await page.evaluate(() => {
            window.scrollTo(0, 0);
            document.querySelector('.type-text').dispatchEvent(new MouseEvent('click', {bubbles: true}));
        });
        const restoredWordIsVisible = await page.locator('.type-word.type-current').evaluate(el => {
            const rect = el.getBoundingClientRect();
            return rect.top >= 0 && rect.bottom <= window.innerHeight;
        });
        assert.equal(restoredWordIsVisible, true, 'focusing a restored word must reveal it');
        await assertInputTracksWord();
    } finally {
        await page.close();
    }
});

for (const width of [320, 360, 800]) {
    test(`memorize labels stay inside their tabs at ${width}px`, async () => {
        const page = await browser.newPage({viewport: {width, height: 800}});
        try {
            await page.goto(`${origin}?lang=fi`);
            await page.waitForFunction(() => window.bibleViewDebug?.documents);
            await page.evaluate(() => {
                window.bibleViewDebug.documents.splice(0, Infinity, {
                    id: 'wrapping-test', type: 'memorize', title: 'John 3:16',
                    texts: [{key: 'verse', text: 'For God so loved the world.'}],
                    state: {memorize: {mode: 'blur'}},
                });
            });
            const labels = page.locator('.memorize-mode-selector .tab-label');
            await labels.first().waitFor();
            // Old Finnish phrases plus an unbroken word deliberately exceed a phone tab's width.
            await labels.evaluateAll(elements => elements.forEach((el, i) => {
                el.textContent = ['Sanan sumennus', 'Sanojen sekoitus', 'Kirjoittaminen',
                    'AnExtremelyLongUnbrokenTranslatedLabelThatMustBeClipped'][i];
            }));
            const metrics = await labels.evaluateAll(elements => elements.map(el => {
                const rect = el.getBoundingClientRect();
                const button = el.closest('button').getBoundingClientRect();
                const style = getComputedStyle(el);
                return {left: rect.left, right: rect.right, height: rect.height,
                    buttonLeft: button.left, buttonRight: button.right,
                    whiteSpace: style.whiteSpace, lineClamp: style.webkitLineClamp,
                    lineHeight: parseFloat(style.lineHeight), overflow: style.overflow};
            }));
            assert.equal(metrics.length, 4);
            for (const m of metrics) {
                assert.ok(m.left >= m.buttonLeft - 0.5 && m.right <= m.buttonRight + 0.5,
                    `label overflows its tab: ${JSON.stringify(m)}`);
                if (width <= 480) {
                    assert.equal(m.whiteSpace, 'normal');
                    assert.equal(m.lineClamp, '2');
                    assert.equal(m.overflow, 'hidden');
                    assert.ok(m.height <= 2 * m.lineHeight + 0.5);
                } else {
                    assert.equal(m.whiteSpace, 'nowrap');
                }
            }
        } finally {
            await page.close();
        }
    });
}
