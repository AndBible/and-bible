import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import {after, before, test} from 'node:test';
import {chromium} from 'playwright';

// Run with node --test tests/pell-paste.browser.mjs; CHROMIUM_PATH may select an installed browser.
const source = await readFile(process.env.PELL_SOURCE_PATH || new URL('../src/lib/pell/pell.js', import.meta.url), 'utf8');
const moduleUrl = `data:text/javascript;base64,${Buffer.from(source).toString('base64')}`;
let browser;
before(async () => {
    browser = await chromium.launch({executablePath: process.env.CHROMIUM_PATH, headless: true});
});
after(async () => browser?.close());

async function editor(run) {
    const page = await browser.newPage();
    try {
        await page.setContent('<div id="editor"></div>');
        await page.evaluate(async url => {
            const {init} = await import(url);
            const element = document.querySelector('#editor');
            window.changes = [];
            window.content = init({element, actions: [], onChange: html => window.changes.push(html)}).content;
            window.transfer = (text, type = 'paste') => {
                const data = new DataTransfer();
                data.setData('text/plain', text);
                data.setData('text/html', '<span style="font-size:40px">Foreign HTML</span>');
                const event = type === 'paste'
                    ? new ClipboardEvent(type, {clipboardData: data, cancelable: true})
                    : new DragEvent(type, {dataTransfer: data, cancelable: true});
                window.content.dispatchEvent(event);
                return event.defaultPrevented;
            };
        }, moduleUrl);
        await page.locator('.pell-content').focus();
        await run(page);
    } finally {
        await page.close();
    }
}

for (const newline of ['\n', '\r\n', '\r']) {
    test(`paste matches typed Enter paragraphs for ${JSON.stringify(newline)}`, () => editor(async page => {
        await page.keyboard.type('First');
        await page.keyboard.press('Enter');
        await page.keyboard.type('Second');
        await page.keyboard.press('Enter');
        await page.keyboard.type('Third');
        const typed = await page.locator('.pell-content').innerHTML();
        assert.equal(typed, 'First<div>Second</div><div>Third</div>');
        const result = await page.evaluate(text => {
            window.content.innerHTML = '';
            window.content.focus();
            window.transfer(text);
            return window.content.innerHTML;
        }, ['First', 'Second', 'Third'].join(newline));
        assert.equal(result, typed);
        console.log(`Chromium ${JSON.stringify(newline)} paste: ${result}`);
    }));
}

test('fallback preserves selected text surroundings, literal HTML, and onChange', () => editor(async page => {
    const result = await page.evaluate(() => {
        window.content.innerHTML = '<div>Before selected after</div>';
        const range = document.createRange();
        range.setStart(window.content.firstChild.firstChild, 7);
        range.setEnd(window.content.firstChild.firstChild, 15);
        window.getSelection().removeAllRanges();
        window.getSelection().addRange(range);
        document.execCommand = () => false;
        window.transfer('<tag>\r\nnext');
        return {html: window.content.innerHTML, changes: window.changes};
    });
    assert.equal(result.html, '<div>Before &lt;tag&gt;<br>next after</div>');
    assert.equal(result.changes.at(-1), result.html);
}));

test('a failed paragraph command does not lose or duplicate successful text', () => editor(async page => {
    const result = await page.evaluate(() => {
        const exec = document.execCommand.bind(document);
        document.execCommand = (command, ...args) => command === 'insertParagraph' ? false : exec(command, ...args);
        window.transfer('First\nSecond\nThird');
        return {html: window.content.innerHTML, changes: window.changes};
    });
    assert.equal(result.html, 'First<br>Second<br>Third');
    assert.equal(result.changes.at(-1), result.html);
}));

test('plain-text drops reject foreign formatting and produce Enter paragraphs', () => editor(async page => {
    const result = await page.evaluate(() => ({
        prevented: window.transfer('First <tag>\nSecond', 'drop'),
        html: window.content.innerHTML,
    }));
    assert.equal(result.prevented, true);
    assert.equal(result.html, 'First &lt;tag&gt;<div>Second</div>');
}));

test('fallback trailing newline at the end of existing text keeps typing on the next line', () => editor(async page => {
    await page.evaluate(() => {
        window.content.innerHTML = '<div>abc</div>';
        const range = document.createRange();
        range.setStart(window.content.firstChild.firstChild, 3);
        range.collapse(true);
        window.getSelection().removeAllRanges();
        window.getSelection().addRange(range);
        document.execCommand = () => false;
        window.transfer('X\n');
    });
    await page.keyboard.type('Y');
    assert.equal(await page.locator('.pell-content').innerText(), 'abcX\nY');
}));

test('fallback trailing newline remains visible and accepts typing on the empty line', () => editor(async page => {
    await page.evaluate(() => {
        document.execCommand = () => false;
        window.transfer('First\n');
    });
    assert.equal(await page.locator('.pell-content').innerHTML(), 'First<br><br>');
    await page.keyboard.type('Last');
    assert.equal(await page.locator('.pell-content').innerText(), 'First\nLast');
}));
