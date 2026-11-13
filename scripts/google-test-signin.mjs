#!/usr/bin/env node
// Google test-account sign-in through a persistent Chrome profile, driven over CDP.
// Wrapper with the defaults and the display handling: scripts/google-test-signin.sh.
//
//   node google-test-signin.mjs status    --profile DIR --account FILE [--shots DIR] [--timeout S]
//   node google-test-signin.mjs authorize --profile DIR --account FILE [--shots DIR] [--timeout S] '<auth url>'
//
// status     waits until the profile is signed in to the account (prints SIGNED-IN), reporting
//            any challenge screen on the way. The sign-in itself is NOT done here: Google refuses
//            a sign-in typed into a CDP-controlled browser ("This browser or app may not be
//            secure"), so the wrapper types it into a plain Chrome without a debugging port.
// authorize  opens an app's OAuth URL in the signed-in profile, picks the account, accepts the
//            consent screens, and catches the loopback redirect (http://127.0.0.1:<port>/?code=...
//            or http://localhost:<port>/...) BEFORE it reaches any listener. The redirect URL is
//            printed on stdout as `REDIRECT <url>`; the caller replays it where the app listens
//            (the container for Linux, the VM for Windows). The code is single-use.
//
// The account file holds the e-mail on line 1 (used to pick the account); this script never reads
// the password on line 2.
// Any screen the script does not recognise (2-step challenge, captcha, "verify it's you") is
// saved as a screenshot and reported once as `WAITING <title> <screenshot>`; the script keeps
// polling until the screen goes away (someone solved it) or --timeout expires (exit 3).
// Exit codes: 0 done, 2 usage, 3 timeout, 4 Chrome/CDP failure.

import { spawn } from 'node:child_process';
import { readFileSync, existsSync, mkdirSync, writeFileSync, rmSync } from 'node:fs';
import { join } from 'node:path';

const argv = process.argv.slice(2);
const cmd = argv.shift();
const opt = (name, def = null) => {
    const i = argv.indexOf(`--${name}`);
    if (i < 0) return def;
    const v = argv[i + 1];
    argv.splice(i, 2);
    return v;
};
const die = (msg, code = 2) => { console.error(msg); process.exit(code); };

const profile = opt('profile') ?? die('--profile is required');
const accountFile = opt('account') ?? die('--account is required');
const shots = opt('shots', join(profile, '..', 'signin-shots'));
const timeoutS = Number(opt('timeout', '1800'));
const chromeBin = opt('chrome', '/opt/google/chrome/chrome');
const authUrl = argv.find(a => !a.startsWith('--'));
if (!['status', 'authorize'].includes(cmd)) die('usage: status|authorize ... (see header)');
if (cmd === 'authorize' && !authUrl) die('authorize needs the auth URL');

const email = readFileSync(accountFile, 'utf8').split('\n')[0].trim();
if (!email.includes('@')) die(`${accountFile}: expected the e-mail on line 1`);

const log = (...a) => console.error('#', ...a);
const sleep = ms => new Promise(r => setTimeout(r, ms));

// ------------------------------------------------------------------ Chrome
mkdirSync(shots, { recursive: true, mode: 0o700 });
const portFile = join(profile, 'DevToolsActivePort');
rmSync(portFile, { force: true });
const chrome = spawn(chromeBin, [
    `--user-data-dir=${profile}`, '--remote-debugging-port=0', '--remote-allow-origins=*',
    '--no-first-run', '--disable-blink-features=AutomationControlled', '--no-default-browser-check', '--password-store=basic',
    '--disable-background-networking', '--disable-sync', '--window-size=1100,900', 'about:blank',
], { stdio: ['ignore', 'ignore', 'pipe'], detached: true });
let chromeErr = '';
chrome.stderr.on('data', d => { chromeErr = (chromeErr + d).slice(-4000); });
const killChrome = () => { try { process.kill(-chrome.pid, 'SIGTERM'); } catch { /* gone */ } };
process.on('exit', killChrome);
for (const sig of ['SIGINT', 'SIGTERM']) process.on(sig, () => process.exit(130));

let port = null;
for (let i = 0; i < 100 && !port; i++) {
    await sleep(200);
    if (existsSync(portFile)) port = readFileSync(portFile, 'utf8').split('\n')[0];
}
if (!port) die(`Chrome did not open a DevTools port (profile in use?)\n${chromeErr}`, 4);
const targets = await (await fetch(`http://127.0.0.1:${port}/json/list`)).json();
const page = targets.find(t => t.type === 'page') ?? die('no page target', 4);

// ------------------------------------------------------------------ CDP
const ws = new WebSocket(page.webSocketDebuggerUrl);
await new Promise((res, rej) => { ws.onopen = res; ws.onerror = rej; });
let nextId = 1;
const pending = new Map();
const listeners = [];
ws.onmessage = ev => {
    const msg = JSON.parse(ev.data);
    if (msg.id && pending.has(msg.id)) {
        const { res, rej } = pending.get(msg.id);
        pending.delete(msg.id);
        msg.error ? rej(new Error(msg.error.message)) : res(msg.result);
    } else if (msg.method) listeners.forEach(l => l(msg));
};
const send = (method, params = {}) => new Promise((res, rej) => {
    const id = nextId++;
    pending.set(id, { res, rej });
    ws.send(JSON.stringify({ id, method, params }));
});
const evaluate = async expr => {
    const r = await send('Runtime.evaluate', { expression: expr, returnByValue: true, awaitPromise: true });
    return r.result?.value;
};
await send('Page.enable');

let redirect = null;
if (cmd === 'authorize') {
    // Catch the loopback redirect before Chrome sends it: the app (or nothing) listens there.
    await send('Fetch.enable', { patterns: [
        { urlPattern: 'http://127.0.0.1:*', requestStage: 'Request' },
        { urlPattern: 'http://localhost:*', requestStage: 'Request' },
    ] });
    listeners.push(msg => {
        if (msg.method !== 'Fetch.requestPaused') return;
        const url = msg.params.request.url;
        if (/[?&](code|error)=/.test(url)) redirect = url;
        send('Fetch.failRequest', { requestId: msg.params.requestId, errorReason: 'Aborted' }).catch(() => {});
    });
}

// ------------------------------------------------------------------ page helpers
// Visible-element lookups run in the page; clicks are real mouse events at the element centre,
// because Google's buttons ignore synthetic el.click() in places.
const EMAIL_SEL = '#identifierId, input[type=email], input[name=identifier]';
const PAGE_HELPERS = `
  const EMAIL_SEL = ${JSON.stringify(EMAIL_SEL)};
  const vis = e => { const r = e.getBoundingClientRect(); const s = getComputedStyle(e);
                     return r.width > 0 && r.height > 0 && s.visibility !== 'hidden' && s.display !== 'none'; };
  const rect = e => { e.scrollIntoView({block: 'center'}); const r = e.getBoundingClientRect();
                      return {x: r.x + r.width / 2, y: r.y + r.height / 2}; };
  const byText = (sel, re) => [...document.querySelectorAll(sel)].filter(vis)
                      .find(e => re.test((e.innerText || e.value || e.getAttribute('aria-label') || '').trim()));
`;
async function click(pt) {
    for (const type of ['mouseMoved', 'mousePressed', 'mouseReleased'])
        await send('Input.dispatchMouseEvent', { type, x: pt.x, y: pt.y, button: 'left', clickCount: 1 });
}
async function shot(name) {
    const { data } = await send('Page.captureScreenshot', { format: 'png' });
    const file = join(shots, `${new Date().toISOString().replace(/[:.]/g, '-')}-${name}.png`);
    writeFileSync(file, Buffer.from(data, 'base64'), { mode: 0o600 });
    return file;
}

/** One look at the current screen: what it is and, when actionable, where to click. */
const inspect = () => evaluate(`(() => { ${PAGE_HELPERS}
  const host = location.host, title = document.title;
  const emailIn = [...document.querySelectorAll(EMAIL_SEL)].find(vis);
  if (emailIn) return {kind: 'email', host, title};
  const pwIn = [...document.querySelectorAll('input[type=password][name=Passwd]')].find(vis);
  if (pwIn) return {kind: 'password', host, title};
  if (host === 'myaccount.google.com') return {kind: 'signed-in', host, title};
  const acct = [...document.querySelectorAll('[data-identifier], [data-email]')].filter(vis)
        .find(e => (e.getAttribute('data-identifier') || e.getAttribute('data-email') || '').toLowerCase()
                   === ${JSON.stringify(email.toLowerCase())});
  if (acct) return {kind: 'account', host, title, at: rect(acct)};
  const selectAll = byText('input[type=checkbox], [role=checkbox]', /^(select all|valitse kaikki)$/i)
        || [...document.querySelectorAll('input[type=checkbox][aria-label]')].filter(vis)
           .find(e => /select all|valitse kaikki/i.test(e.getAttribute('aria-label')));
  if (selectAll && !(selectAll.checked || selectAll.getAttribute('aria-checked') === 'true'))
      return {kind: 'select-all', host, title, at: rect(selectAll)};
  const ok = byText('button, [role=button]', /^(continue|allow|jatka|salli)$/i);
  if (ok && ok.getAttribute('aria-disabled') !== 'true' && !ok.disabled)
      return {kind: 'consent', host, title, at: rect(ok), label: (ok.innerText || '').trim()};
  return {kind: 'unknown', host, title, text: (document.body?.innerText || '').slice(0, 300)};
})()`);

// ------------------------------------------------------------------ main loop
const target = cmd === 'status' ? 'https://myaccount.google.com/' : authUrl;
await send('Page.navigate', { url: target });

const deadline = Date.now() + timeoutS * 1000;
let lastKey = '', sameCount = 0, waitingReported = '';
while (Date.now() < deadline) {
    await sleep(1500);
    if (redirect) {
        console.log(`REDIRECT ${redirect}`);
        process.exit(0);
    }
    let s;
    try { s = await inspect(); } catch { continue; }   // mid-navigation
    if (!s) continue;
    const key = `${s.kind}|${s.host}|${s.title}`;
    sameCount = key === lastKey ? sameCount + 1 : 0;
    if (key !== lastKey) log(`screen: ${s.kind} on ${s.host} "${s.title}"`);
    lastKey = key;
    // An actionable screen that stays after the action gets a few retries, then is treated as unknown.
    // E-mail and password screens mean the profile is not signed in: run the wrapper's `login`.
    const stuck = ['email', 'password'].includes(s.kind) || (s.kind !== 'unknown' && s.kind !== 'signed-in' && sameCount >= 6);
    // Signed out, myaccount.google.com lands on the www.google.com/account/about marketing page.
    if (cmd === 'status' && (['email', 'password'].includes(s.kind) || s.host === 'www.google.com')) {
        console.log('NOT-SIGNED-IN');
        process.exit(3);
    }
    if (s.kind === 'signed-in') {
        if (cmd === 'status') { console.log(`SIGNED-IN ${email}`); process.exit(0); }
        continue;
    }
    if (!stuck && sameCount % 3 === 0) {
        switch (s.kind) {
            case 'account':
            case 'select-all':
            case 'consent':
                log(`clicking ${s.kind}${s.label ? ` (${s.label})` : ''} on ${s.host}`);
                await click(s.at);
                continue;
        }
    }
    if (s.kind === 'unknown' || stuck) {
        if (waitingReported !== key) {
            waitingReported = key;
            const file = await shot(s.kind);
            console.log(`WAITING ${s.host} "${s.title}" ${file}`);
        }
    }
}
const file = await shot('timeout');
console.log(`TIMEOUT ${file}`);
process.exit(3);
