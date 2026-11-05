#!/usr/bin/env node
'use strict';

const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const sourcePath = path.resolve(__dirname, '../iosApp/ReadingAccessibilityProbe.swift');
const swiftSource = fs.readFileSync(sourcePath, 'utf8');
const marker = 'private static let metricsScript = #"""';
const scriptStart = swiftSource.indexOf(marker);
assert.notEqual(scriptStart, -1, 'metricsScript raw-string declaration exists');
const contentStart = scriptStart + marker.length;
const scriptEnd = swiftSource.indexOf('"""#', contentStart);
assert.notEqual(scriptEnd, -1, 'metricsScript raw-string terminator exists');
const metricsScript = swiftSource.slice(contentStart, scriptEnd).trim();

let JSDOM;
try {
  ({JSDOM} = require(require.resolve('jsdom', {paths: [path.resolve(__dirname, '../../app/bibleview-js')]})));
} catch {
  console.error('jsdom is not installed under app/bibleview-js; no dependencies were installed.');
  process.exit(2);
}

let assertions = 0;
function check(condition, message) {
  assert.ok(condition, message);
  assertions += 1;
}

function createFixture({bookInitials = 'K4GenBook', includeVerse = true, hiddenGenesisOnly = false, hiddenGenesisStyle = 'display:none', hiddenVerseDecoy = false, hiddenTargetText = false, emptyRects = false, offscreen = false} = {}) {
  const genesis = 'In the beginning God created the heaven and the earth';
  const dom = new JSDOM(`<!doctype html><html><body>
    ${hiddenGenesisOnly ? `<div style="${hiddenGenesisStyle}" data-book-initials="${bookInitials}">${genesis}</div>` : `<div data-book-initials="${bookInitials}">${genesis}</div>`}
    ${hiddenVerseDecoy ? '<p class="verse" style="display:none">For God so loved the world</p>' : ''}
    ${includeVerse ? `<p class="verse">${hiddenTargetText ? '<span style="visibility:hidden">For God so loved the world</span>' : '<span>For God </span><em>so loved</em><span> the world</span>'}</p>` : ''}
    <p class="verse">For God so loved an unrelated passage</p>
  </body></html>`, {runScripts: 'outside-only'});
  const {window} = dom;
  // jsdom has no layout or rendered visibility; fixture visibility is defined by inline styles.
  Object.defineProperty(window.HTMLElement.prototype, 'innerText', {
    configurable: true,
    get() { return this.textContent; },
  });
  window.HTMLElement.prototype.getClientRects = function () {
    let node = this;
    while (node instanceof window.HTMLElement) {
      if (node.style.display === 'none' || node.style.visibility === 'hidden') return [];
      node = node.parentElement;
    }
    if (emptyRects) return [];
    return [{width: 10, height: 8, top: offscreen ? 5000 : 0}];
  };
  window.Range.prototype.getBoundingClientRect = function () {
    return {width: this.toString().length * 3, height: 8, top: 4};
  };
  window.getComputedStyle = element => ({
    color: element.style.color || 'rgb(20, 40, 60)',
    display: element.style.display || 'block',
    visibility: element.style.visibility || 'visible',
  });
  return {dom, window};
}

function runProbe(fixture) {
  return JSON.parse(fixture.window.eval(metricsScript));
}

for (const bookInitials of ['K4GenBook', 'H2Maps']) {
  const fixture = createFixture({bookInitials});
  fixture.window.document.querySelector('.verse span').style.color = 'rgb(20, 40, 60)';
  const result = runProbe(fixture);
  check(result.genesisFixture === bookInitials, `identifies Genesis fixture ${bookInitials}`);
  check(result.verse16.length === 4, 'reports four measured words');
  check(result.verse16.every(metric => metric !== null), 'measures words split across text spans');
  check(result.verse16.every(metric => metric.color === 'rgb(20, 40, 60)'), 'reports computed text color');
  check(result.verse16.every((metric, index) => metric.width === ['For', 'God', 'so', 'loved'][index].length * 3 && metric.height === 8 && metric.top === 4), 'reports per-word Range geometry');
  fixture.dom.window.close();
}

const missingVerse = createFixture({includeVerse: false});
const missingResult = runProbe(missingVerse);
check(missingResult.genesisFixture === 'K4GenBook', 'keeps fixture detection without target verse');
check(missingResult.verse16.every(metric => metric === null), 'returns null measurements without target verse');
missingVerse.dom.window.close();

const missingGenesis = createFixture({bookInitials: 'OtherBook'});
const missingGenesisResult = runProbe(missingGenesis);
check(missingGenesisResult.genesisFixture === null, 'returns null when no Genesis fixture exists');
missingGenesis.dom.window.close();

const noFixture = new JSDOM('<!doctype html><html><body></body></html>', {runScripts: 'outside-only'});
Object.defineProperty(noFixture.window.HTMLElement.prototype, 'innerText', {
  configurable: true,
  get() { return this.textContent; },
});
noFixture.window.HTMLElement.prototype.getClientRects = function () { return [{width: 10, height: 8}]; };
noFixture.window.Range.prototype.getBoundingClientRect = function () {
  return {width: this.toString().length * 3, height: 8, top: 4};
};
noFixture.window.getComputedStyle = () => ({color: 'rgb(20, 40, 60)', display: 'block', visibility: 'visible'});
const noFixtureResult = runProbe({window: noFixture.window});
check(noFixtureResult.genesisFixture === null, 'returns null when page has no fixture');
check(noFixtureResult.verse16.every(metric => metric === null), 'returns null measurements when page has no fixture');
noFixture.window.close();

const hiddenGenesis = createFixture({hiddenGenesisOnly: true});
const hiddenGenesisResult = runProbe(hiddenGenesis);
check(hiddenGenesisResult.genesisFixture === null, 'rejects hidden Genesis fixture marker');
hiddenGenesis.dom.window.close();

const hiddenVerse = createFixture({includeVerse: false, hiddenVerseDecoy: true});
const hiddenVerseResult = runProbe(hiddenVerse);
check(hiddenVerseResult.verse16.every(metric => metric === null), 'rejects hidden target verse');
hiddenVerse.dom.window.close();

const hiddenTargetText = createFixture({hiddenTargetText: true});
const hiddenTargetTextResult = runProbe(hiddenTargetText);
check(hiddenTargetTextResult.verse16.every(metric => metric === null), 'rejects hidden selected-text descendant');
hiddenTargetText.dom.window.close();

const hiddenVisibilityGenesis = createFixture({hiddenGenesisOnly: true, hiddenGenesisStyle: 'visibility:hidden'});
const hiddenVisibilityGenesisResult = runProbe(hiddenVisibilityGenesis);
check(hiddenVisibilityGenesisResult.genesisFixture === null, 'rejects visibility-hidden Genesis fixture marker');
hiddenVisibilityGenesis.dom.window.close();

const emptyRects = createFixture({emptyRects: true});
const emptyRectsResult = runProbe(emptyRects);
check(emptyRectsResult.genesisFixture === null, 'rejects elements with no client rects');
emptyRects.dom.window.close();

const offscreen = createFixture({offscreen: true});
const offscreenResult = runProbe(offscreen);
check(offscreenResult.genesisFixture === 'K4GenBook', 'accepts offscreen rendered fixture');
offscreen.dom.window.close();

const negativeControl = createFixture();
negativeControl.window.document.querySelector('.verse').textContent = 'No selected words remain';
const negativeResult = runProbe(negativeControl);
check(negativeResult.verse16.every(metric => metric === null), 'negative control has no measurements when selected text is absent');
negativeControl.dom.window.close();

console.log(`PASS: ${assertions} assertions across 11 fixture scenarios.`);
