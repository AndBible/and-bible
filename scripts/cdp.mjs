#!/usr/bin/env node
// Chrome DevTools Protocol client for a WebView on a real device.
//
// Expects a DevTools endpoint already reachable at 127.0.0.1:<port> — webview-cdp.sh
// sets that up via adb-localabstract-proxy.py. Carried by node's built-in global
// WebSocket (node >= 21), so there is no npm dependency.
//
//   node cdp.mjs <port> list
//   node cdp.mjs <port> scripts [--filter S]
//   node cdp.mjs <port> eval '<js>'
//   node cdp.mjs <port> tail [--seconds N] [--exceptions]
//   node cdp.mjs <port> break <where:line> [...] [--exceptions] [--eval '<js>']
//                             [--trigger '<js>'] [--seconds N] [--hold]
//
// `where` may be a substring of the BUNDLED script url, or — because the debug build
// ships inline sourcemaps — a path from the ORIGINAL sources (e.g. BibleView.vue:120).
// Original positions are mapped to bundled ones here, and paused frames are annotated
// with the original file:line they came from.
//   node cdp.mjs <port> screenshot <out.png>
//
// --trigger runs JS in the page once the breakpoints are set, so a scripted repro can
// fire them without a human touching the device; --eval runs in the PAUSED frame.
//
// Common flags: --target <substring>  pick the page target by title/url
//               --seconds <n>         how long the long-lived modes stay attached
//               --quiet               drop console output (also CDP_CONSOLE=0)
//
// EVERY mode exits on its own — nothing here waits for a human.

const argv = process.argv.slice(2);
const port = argv.shift() || '9222';
const cmd = argv.shift() || 'list';

const flag = (name, def = null) => {
    const i = argv.indexOf(`--${name}`);
    if (i < 0) return def;
    const v = argv[i + 1];
    argv.splice(i, 2);
    return v;
};
const bool = name => {
    const i = argv.indexOf(`--${name}`);
    if (i < 0) return false;
    argv.splice(i, 1);
    return true;
};

const targetFilter = flag('target');
const seconds = Number(flag('seconds', cmd === 'break' ? '60' : '30'));
const holdOnPause = bool('hold');
const pauseOnExceptions = bool('exceptions');
const evalOnPause = flag('eval');
const trigger = flag('trigger');
const scriptFilter = flag('filter');
const listSources = bool('sources');
const quiet = bool('quiet') || process.env.CDP_CONSOLE === '0';
const positional = argv.filter(a => !a.startsWith('--'));

const die = (msg, code = 2) => { console.error(msg); process.exit(code); };

// ------------------------------------------------------------- source maps
// The vite debug bundle carries inline sourcemaps, so breakpoints can be expressed in
// terms of the original .vue/.ts sources and paused frames can name them back. Doing
// the mapping here (a ~40-line VLQ decoder) beats pulling in an npm dependency: this
// container has no package.json for tooling and strict egress.
const B64 = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/';
function decodeVlq(segment) {
    const out = [];
    let shift = 0, value = 0;
    for (const ch of segment) {
        const digit = B64.indexOf(ch);
        if (digit < 0) throw new Error(`bad VLQ char ${ch}`);
        value += (digit & 31) << shift;
        if (digit & 32) { shift += 5; continue; }
        out.push(value & 1 ? -(value >> 1) : value >> 1);
        shift = 0; value = 0;
    }
    return out;
}
function parseSourceMap(json) {
    const { sources = [], mappings = '', sourceRoot = '' } = json;
    const entries = [];
    let srcIdx = 0, srcLine = 0, srcCol = 0;
    mappings.split(';').forEach((lineStr, genLine) => {
        let genCol = 0;
        for (const seg of lineStr.split(',')) {
            if (!seg) continue;
            const f = decodeVlq(seg);
            genCol += f[0];
            if (f.length >= 4) {
                srcIdx += f[1]; srcLine += f[2]; srcCol += f[3];
                entries.push({ genLine, genCol, srcIdx, srcLine, srcCol });
            }
        }
    });
    return { sources: sources.map(s => sourceRoot + s), entries };
}
const maps = new Map();   // scriptId -> parsed map | null
async function mapFor(scriptId) {
    if (maps.has(scriptId)) return maps.get(scriptId);
    const s = scripts.get(scriptId);
    let parsed = null;
    try {
        if (s?.sourceMapURL?.startsWith('data:')) {
            const b64 = s.sourceMapURL.slice(s.sourceMapURL.indexOf(',') + 1);
            parsed = parseSourceMap(JSON.parse(Buffer.from(b64, 'base64').toString('utf8')));
        } else if (s?.sourceMapURL && s.url) {
            // Fetch it THROUGH THE PAGE: the map lives on the page's own origin
            // (appassets.androidplatform.net), which this process cannot reach.
            const abs = new URL(s.sourceMapURL, s.url).href;
            const r = await send('Runtime.evaluate', {
                expression: `fetch(${JSON.stringify(abs)}).then(r => r.text())`,
                awaitPromise: true, returnByValue: true,
            });
            if (r.result?.value) parsed = parseSourceMap(JSON.parse(r.result.value));
        }
    } catch (e) {
        console.error(`# sourcemap for ${s?.url} unusable: ${e.message}`);
    }
    maps.set(scriptId, parsed);
    return parsed;
}
// bundled position -> "original/file.vue:line"
async function originalPosition(scriptId, genLine, genCol) {
    const m = await mapFor(scriptId);
    if (!m) return null;
    let best = null;
    for (const e of m.entries) {
        if (e.genLine !== genLine) continue;
        if (e.genCol <= genCol && (!best || e.genCol > best.genCol)) best = e;
    }
    if (!best) return null;
    return `${m.sources[best.srcIdx]}:${best.srcLine + 1}:${best.srcCol + 1}`;
}
// "BibleView.vue:120" -> the earliest bundled position for it
async function generatedPosition(fileHint, line) {
    for (const [scriptId, s] of scripts) {
        const m = await mapFor(scriptId);
        if (!m) continue;
        const idxs = m.sources
            .map((src, i) => [src, i])
            .filter(([src]) => src.endsWith(fileHint) || src.includes(fileHint))
            .map(([, i]) => i);
        if (!idxs.length) continue;
        const hits = m.entries.filter(e => idxs.includes(e.srcIdx) && e.srcLine === line - 1);
        if (!hits.length) continue;
        hits.sort((a, b) => a.genLine - b.genLine || a.genCol - b.genCol);
        return { url: s.url, scriptId, lineNumber: hits[0].genLine, columnNumber: hits[0].genCol,
                 source: m.sources[hits[0].srcIdx] };
    }
    return null;
}

// ---------------------------------------------------------------- target pick
const targets = await (await fetch(`http://127.0.0.1:${port}/json/list`)).json();
if (cmd === 'list') {
    console.log(JSON.stringify(targets.map(({ id, type, title, url }) => ({ id, type, title, url })), null, 2));
    process.exit(0);
}
const pages = targets.filter(t => t.type === 'page');
const page = targetFilter ? pages.find(t => (t.title + ' ' + t.url).includes(targetFilter)) : pages[0];
if (!page) {
    die(`no page target${targetFilter ? ` matching ${JSON.stringify(targetFilter)}` : ''}; got:\n` +
        targets.map(t => `  ${t.type}  ${t.title}`).join('\n') || '  (none)', 1);
}
console.error(`# target: ${page.title}`);
if (pages.length > 1) {
    console.error(`# NOTE ${pages.length} page targets — use --target <substring> to pick another:`);
    pages.forEach(t => console.error(`#   ${t.title}`));
}

// ------------------------------------------------------------------- session
const ws = new WebSocket(page.webSocketDebuggerUrl);
let seq = 0;
const pending = new Map();
const send = (method, params = {}) => new Promise((res, rej) => {
    const id = ++seq;
    pending.set(id, { res, rej });
    ws.send(JSON.stringify({ id, method, params }));
});
const scripts = new Map();   // scriptId -> {url, sourceMapURL}
const handlers = {};
let exitCode = 0;

const finish = code => {
    if (code !== undefined) exitCode = code;
    try { ws.close(); } catch { /* already closing */ }
    process.exit(exitCode);
};

// Render a Runtime.RemoteObject compactly. Object args come back as previews unless
// returnByValue is on, and printing "[object Object]" is exactly the logcat weakness
// this client exists to avoid.
const fmt = o => {
    if (!o || typeof o !== 'object') return String(o);
    if ('value' in o) return typeof o.value === 'object' ? JSON.stringify(o.value) : String(o.value);
    if (o.unserializableValue) return o.unserializableValue;
    if (o.preview) {
        const p = o.preview;
        if (p.subtype === 'array' || p.type === 'object' && p.subtype === 'array') {
            return `[${(p.properties || []).map(x => x.value).join(', ')}${p.overflow ? ', …' : ''}]`;
        }
        const body = (p.properties || []).map(x => `${x.name}: ${x.value}`).join(', ');
        return `${p.description && p.description !== 'Object' ? p.description + ' ' : ''}{${body}${p.overflow ? ', …' : ''}}`;
    }
    return o.description ?? o.className ?? JSON.stringify(o);
};

ws.onmessage = ev => {
    const m = JSON.parse(ev.data);
    if (m.id && pending.has(m.id)) {
        const { res, rej } = pending.get(m.id);
        pending.delete(m.id);
        m.error ? rej(new Error(JSON.stringify(m.error))) : res(m.result);
        return;
    }
    if (m.method === 'Debugger.scriptParsed') {
        scripts.set(m.params.scriptId, { url: m.params.url, sourceMapURL: m.params.sourceMapURL });
        return;
    }
    if (m.method === 'Runtime.consoleAPICalled') {
        if (!quiet) console.log(`[${m.params.type}]`, m.params.args.map(fmt).join(' '));
        return;
    }
    if (m.method === 'Runtime.exceptionThrown') {
        // Honours --quiet like console output does: Runtime.enable replays the page's
        // exception backlog too, and those replayed lines would otherwise interleave
        // with an eval result or a screenshot's output.
        if (!quiet) console.log('[exception]', m.params.exceptionDetails.exception?.description ??
            m.params.exceptionDetails.text);
        return;
    }
    handlers[m.method]?.(m.params);
};
ws.onerror = e => die(`ws error: ${e.message ?? e}`, 1);
ws.onclose = () => process.exit(exitCode);

// --------------------------------------------------------------- pause dump
const dumpPause = async params => {
    const reason = params.reason + (params.data?.description ? `: ${params.data.description}` : '');
    console.log(`\n=== PAUSED (${reason}) ===`);
    const frames = params.callFrames.slice(0, 8);
    for (const [i, f] of frames.entries()) {
        const s = scripts.get(f.location.scriptId);
        const where = `${s?.url || '?'}:${f.location.lineNumber + 1}:${f.location.columnNumber + 1}`;
        const orig = await originalPosition(f.location.scriptId, f.location.lineNumber, f.location.columnNumber);
        console.log(`  #${i} ${f.functionName || '(anonymous)'}  ${orig ? orig + `   (${where})` : where}`);
        if (i === 0) {
            for (const scope of f.scopeChain.filter(sc => ['local', 'closure', 'catch'].includes(sc.type))) {
                if (!scope.object?.objectId) continue;
                let props;
                try {
                    props = await send('Runtime.getProperties',
                        { objectId: scope.object.objectId, ownProperties: true, generatePreview: true });
                } catch { continue; }
                const shown = (props.result || []).filter(p => p.value).slice(0, 25);
                if (!shown.length) continue;
                console.log(`     ${scope.type}:`);
                for (const p of shown) console.log(`       ${p.name} = ${fmt(p.value)}`);
                if ((props.result || []).length > shown.length) console.log('       …');
            }
        }
    }
    if (evalOnPause) {
        try {
            const r = await send('Debugger.evaluateOnCallFrame',
                { callFrameId: params.callFrames[0].callFrameId, expression: evalOnPause, returnByValue: true });
            console.log(`  eval => ${r.exceptionDetails
                ? 'EXCEPTION ' + (r.exceptionDetails.exception?.description ?? r.exceptionDetails.text)
                : JSON.stringify(r.result.value ?? r.result)}`);
        } catch (e) { console.log(`  eval failed: ${e.message}`); }
    }
    if (holdOnPause) {
        console.log('  (--hold: staying paused — the WebView is FROZEN on the device until this exits)');
    } else {
        await send('Debugger.resume');
        console.log('  (resumed)');
    }
};

// ------------------------------------------------------------------- modes
ws.onopen = async () => {
    try {
        await send('Runtime.enable');   // replays the page's console backlog

        if (cmd === 'eval') {
            const expr = positional[0] || die('eval needs a JS expression');
            const r = await send('Runtime.evaluate',
                { expression: expr, returnByValue: true, awaitPromise: true, generatePreview: true });
            if (r.exceptionDetails) {
                console.error('EXCEPTION:', JSON.stringify(r.exceptionDetails.exception ?? r.exceptionDetails, null, 2));
                finish(1);
            }
            console.log('=>', JSON.stringify(r.result.value ?? r.result, null, 2));
            finish(0);
        }

        if (cmd === 'scripts') {
            await send('Debugger.enable');
            setTimeout(async () => {
                if (listSources) {
                    const seen = new Set();
                    for (const id of scripts.keys()) {
                        const m = await mapFor(id);
                        for (const s of m?.sources || []) {
                            if (!scriptFilter || s.includes(scriptFilter)) seen.add(s);
                        }
                    }
                    console.log([...seen].sort().join('\n') || '(no sourcemaps on this page)');
                    console.log(`\n# ${seen.size} original source(s) — break on one with` +
                        `\n#   break <file>:<line>   (mapped through the inline sourcemap)`);
                    finish(0);
                }
                const rows = [...scripts.values()]
                    .filter(s => s.url && (!scriptFilter || s.url.includes(scriptFilter)))
                    .map(s => `${s.url}${s.sourceMapURL ? `   [sourceMap: ${s.sourceMapURL.slice(0, 60)}${s.sourceMapURL.length > 60 ? '…' : ''}]` : ''}`);
                console.log(rows.sort().join('\n') || '(no scripts parsed — reload the page while attached)');
                console.log(`\n# ${rows.length} script(s). Breakpoints take the BUNDLED url:line — a sourceMap` +
                    `\n# here is only usable by a DevTools frontend, this client does not map for you.`);
                finish(0);
            }, 1500);
            return;
        }

        if (cmd === 'screenshot') {
            const out = positional[0] || die('screenshot needs an output path');
            await send('Page.enable');
            const r = await send('Page.captureScreenshot', { format: 'png' });
            const { writeFileSync } = await import('node:fs');
            writeFileSync(out, Buffer.from(r.data, 'base64'));
            console.log(`wrote ${out} (the WebView only — adb screencap gets the whole screen)`);
            finish(0);
        }

        if (cmd === 'tail' || cmd === 'break') {
            if (cmd === 'break' || pauseOnExceptions) {
                await send('Debugger.enable');
                handlers['Debugger.paused'] = p => { dumpPause(p).catch(e => console.error('dump failed:', e.message)); };
                if (pauseOnExceptions) {
                    await send('Debugger.setPauseOnExceptions', { state: 'uncaught' });
                    console.error('# pausing on uncaught exceptions');
                }
                if (positional.length) {
                    // scriptParsed events (and thus the sourcemaps) land asynchronously
                    // after Debugger.enable — give them a moment before mapping.
                    await new Promise(r => setTimeout(r, 1500));
                }
                for (const spec of positional) {
                    const at = spec.lastIndexOf(':');
                    if (at < 1) die(`bad breakpoint ${JSON.stringify(spec)} — want <where>:<line>`);
                    const where = spec.slice(0, at);
                    const line = Number(spec.slice(at + 1));
                    if (!Number.isInteger(line) || line < 1) die(`bad line in ${JSON.stringify(spec)}`);

                    const bundled = [...scripts.values()].some(s => s.url?.includes(where));
                    let r, how;
                    if (bundled) {
                        r = await send('Debugger.setBreakpointByUrl', {
                            lineNumber: line - 1,
                            urlRegex: where.replace(/[.*+?^${}()|[\]\\]/g, '\\$&'),
                        });
                        how = `bundled ${where}:${line}`;
                    } else {
                        const gen = await generatedPosition(where, line);
                        if (!gen) die(`no script url and no sourcemap entry matches ${JSON.stringify(where)}:${line}\n` +
                            `  run \`scripts --sources\` to see the original files this page maps to`);
                        r = await send('Debugger.setBreakpointByUrl', {
                            url: gen.url, lineNumber: gen.lineNumber, columnNumber: gen.columnNumber,
                        });
                        how = `${gen.source}:${line} -> ${gen.url.split('/').pop()}:${gen.lineNumber + 1}:${gen.columnNumber + 1}`;
                    }
                    console.error(`# breakpoint ${r.breakpointId} ${how} — ` +
                        `${r.locations.length} resolved location(s)${r.locations.length ? '' : ' (line has no executable code?)'}`);
                }
            }
            if (trigger) {
                // Fire-and-forget: this call does not resolve while a breakpoint it hit
                // keeps the page paused, and the pause handler is what resumes it.
                send('Runtime.evaluate', { expression: trigger, returnByValue: true, awaitPromise: true })
                    .then(r => console.error(`# trigger => ${r.exceptionDetails
                        ? 'EXCEPTION ' + (r.exceptionDetails.exception?.description ?? r.exceptionDetails.text).split('\n')[0]
                        : JSON.stringify(r.result?.value ?? r.result?.type)}`))
                    .catch(e => console.error(`# trigger failed: ${e.message}`));
                console.error(`# triggered: ${trigger}`);
            }
            console.error(`# attached for ${seconds}s — drive the UI now (adb shell input tap X Y)`);
            setTimeout(() => { console.error(`# ${seconds}s elapsed, detaching`); finish(0); }, seconds * 1000);
            return;
        }

        die(`unknown command ${JSON.stringify(cmd)} — one of: list scripts eval tail break screenshot`);
    } catch (e) {
        console.error('CDP error:', e.message);
        finish(1);
    }
};
