---
name: update-translations
description: >
  Use when translating or updating AndBible UI strings — single language or all languages at once.
  Triggers: "translate strings", "update translations", "add [language] translations",
  "translate all languages", "update all translations", "käännä stringit",
  "päivitä kaikki käännökset", missing translations, incomplete locale coverage.
---

# Update Translations

Translate missing UI strings for AndBible — Android XML, Vue.js YAML, and Play Store descriptions — with consistent terminology, correct structure, and full validation. Supports single language or all languages at once.

## Sandbox

**All commands in this pipeline work within the default sandbox.** No `dangerouslyDisableSandbox` is needed. Every script reads/writes only within the repo directory (`.`), which the sandbox allows. Do NOT request sandbox bypass for any command in this workflow.

## When to Use

- Adding or completing translations for a single language
- Bulk-translating all languages after new features land
- Fixing inconsistent terminology across a locale

## Overview

### Single Language
```
1. Extract terminology glossary (run extract_glossary.py)
2. Find missing strings (run find_missing.py)
3. Prepare agent chunks (run prepare_chunks.py)
4. Dispatch translation agents with glossary + chunks
5. Restructure Android XML (run restructure.py with chunk directory)
6. Restructure Vue.js YAML (run restructure_yaml.py)
7. Restructure Play Store YAML (run restructure_playstore.py)
8. Validate everything (run validate.py)
9. Compile Play Store descriptions (run play/compile_description.py)
```

### All Languages
```
1. Scan all languages (run scan_languages.py --needs-work --tier 1,2)
2. For each language needing work, dispatch a sub-agent (model: sonnet)
   that runs the full single-language pipeline (steps 1-7 above)
3. Batch 5-8 sub-agents at a time in parallel
4. Final validation sweep
```

## Language Codes

Scripts accept the **canonical code** (Android directory suffix). The `lang_codes.py` module handles Android ↔ Vue.js code mapping automatically.

| Canonical | Android dir | Vue.js file | Language |
|-----------|-------------|-------------|----------|
| iw | values-iw | he.yaml | Hebrew |
| pt-rBR | values-pt-rBR | pt-BR.yaml | Portuguese (Brazil) |
| zh-rCN | values-zh-rCN | zh-CN.yaml | Chinese (Simplified) |
| zh-rTW | values-zh-rTW | zh.yaml | Chinese (Traditional) |

Most languages use the same code for both (e.g., `de` → `values-de/` + `de.yaml`).

### Android mirror targets

Some languages must write to **more than one Android directory** to stay synchronized with Transifex's `lang_map` + the Makefile's `tx-pull` step. Mirror targets are defined in `lang_codes.py` (`ANDROID_MIRRORS`) and `restructure.py` automatically writes to both when translating such a language.

| Lang | Primary target | Mirror | Why |
|------|----------------|--------|-----|
| `zh-rTW` | `values-zh-rTW/strings.xml` | `values-zh/strings.xml` | Transifex `zh_TW` → `values-zh/`; Makefile then `cp` → `values-zh-rTW/`. Both must stay in sync so `tx push`/`tx pull` don't destroy new translations. |

When adding new translations to a mirror-target language, **also push to Transifex** (`tx push -t -l <transifex-code> -f -r andbible.and-bible-stringsxml`), otherwise the next `make tx-pull` will overwrite the mirror with stale Transifex content.

Some languages may not yet have a Vue.js translation file. The `restructure_yaml.py` script creates new files automatically when needed.

## File Locations

| Side | Base (English) | Target |
|------|---------------|--------|
| Android | `app/src/main/res/values/strings.xml` | `app/src/main/res/values-{android_code}/strings.xml` |
| Vue.js | `app/bibleview-js/src/lang/default.yaml` | `app/bibleview-js/src/lang/{vuejs_code}.yaml` |
| Play Store | `play/playstore-description.yml` | `play/description-translations/{playstore_code}.yml` |

Play Store descriptions use Jinja2 templates with `{{ variable }}` placeholders (e.g. `{{ title }}`, `{{ total_documents }}`). These must be preserved verbatim in translations. After translating, run `python3 play/compile_description.py` to compile YAML → fastlane text files.

## Scripts

All scripts are in the skill directory and take the language code as first argument. **Always run from the repo root.**

```bash
SKILL=.claude/skills/update-translations

# Step 1: Extract terminology glossary from existing translations
python3 $SKILL/extract_glossary.py LANG

# Step 2: Find missing strings
python3 $SKILL/find_missing.py LANG

# Step 3: Prepare agent-ready chunk files
python3 $SKILL/prepare_chunks.py LANG                    # default 130/chunk
python3 $SKILL/prepare_chunks.py LANG --chunk-size 100   # custom size

# Step 5: Restructure Android XML (merges + drops stale + fixes double-escapes)
python3 $SKILL/restructure.py LANG                                    # restructure only
python3 $SKILL/restructure.py LANG tmp/translate-chunks/                # merge from directory
python3 $SKILL/restructure.py LANG chunk1.xml chunk2.xml              # merge from files
python3 $SKILL/restructure.py LANG tmp/translate-chunks/ --keep-stale   # keep stale strings

# Step 6: Restructure Vue.js YAML (merges + drops stale + correct key order)
python3 $SKILL/restructure_yaml.py LANG                          # restructure only
python3 $SKILL/restructure_yaml.py LANG tmp/translate-results/new.yaml     # merge new + restructure

# Step 7: Restructure Play Store YAML (merges + preserves base key order + comments)
python3 $SKILL/restructure_playstore.py LANG                                    # restructure only
python3 $SKILL/restructure_playstore.py LANG tmp/translate-results/playstore.yml # merge new + restructure

# Step 8: Validate coverage, format specifiers, order, XML, Play Store lengths
python3 $SKILL/validate.py LANG

# Step 9: Compile Play Store descriptions (YAML → fastlane text files)
cd play && python3 compile_description.py && cd ..

# Scan all languages (for all-languages workflow)
python3 $SKILL/scan_languages.py                            # full table
python3 $SKILL/scan_languages.py --needs-work               # only languages with missing strings
python3 $SKILL/scan_languages.py --tier 1,2                  # include tier 2
python3 $SKILL/scan_languages.py --only de,fr,es            # specific languages only
python3 $SKILL/scan_languages.py --exclude ar,ko,hi         # exclude specific languages
python3 $SKILL/scan_languages.py --tier 1 --exclude bg,iw   # combine filters
```

## Step 1: Extract Terminology Glossary

```bash
python3 $SKILL/extract_glossary.py LANG
```

Automatically extracts term mappings from existing translations by comparing short English strings with their translations. Outputs a markdown table ready to paste into agent prompts.

Also identify **grammar patterns** for the target language:
- Button/action labels (typically imperative)
- Setting titles (noun phrases)
- Descriptions/summaries (full sentences)
- Confirmation dialogs (question form)

**Why this matters:** Without a glossary, the same English term gets translated inconsistently. "Label" might become three different words. The glossary enforces consistency.

## Step 2: Find Missing Strings

```bash
python3 $SKILL/find_missing.py LANG
```

Reports missing strings as `name|||English value` for both Android XML and Vue.js YAML. Also detects stale (extra) keys, empty values, and order mismatches.

If nothing is missing, skip to Step 7 (validate) to confirm.

## Step 3: Prepare Agent Chunks

```bash
python3 $SKILL/prepare_chunks.py LANG
```

Splits missing strings into chunk files in `tmp/translate-chunks/`. Outputs a summary telling you how many agents to dispatch. Each chunk file contains `name|||English value` lines.

## Step 4: Translate

### For small batches (<50 strings): translate directly

### For large batches (>50 strings): use parallel sub-agents

Dispatch one agent per chunk file. **Always use `model: "sonnet"` for translation agents** — translation is a straightforward task that doesn't need Opus, and Sonnet is significantly faster and cheaper for batch translation work.

Each sub-agent gets:
1. Its chunk file content (read from `tmp/translate-chunks/`)
2. The terminology glossary from Step 1
3. Grammar rules for the target language
4. Output format instructions

**Sub-agent prompt template essentials:**
- Output format: raw `<string name="...">...</string>` XML lines (Android) or YAML key-value pairs (Vue.js)
- Terminology glossary as a lookup table
- Grammar patterns with examples
- Format specifier preservation rules: `%s`, `%1$s`, `%d` must stay verbatim
- XML escaping rules: `\'`, `&amp;`, `&lt;`, `&gt;`, `\"`
- **CRITICAL: Agents MUST use the Write tool to save results to files.** Never use Bash heredocs or `cat <<EOF` for XML content — bash mangles `<!--` via history expansion, and large heredocs cause parser timeouts.

**After agents return:** Each agent saves its Android output directly to `tmp/translate-results/{LANG}_chunk_{N}.xml` using the Write tool, and Vue.js output to `tmp/translate-results/{LANG}_vuejs.yaml`. The restructure scripts handle merging.

**Sub-agent output instructions (include in every agent prompt):**
> Save your Android XML output using the Write tool to `tmp/translate-results/{LANG}_chunk_{N}.xml`.
> The file must contain one `<string name="...">...</string>` per line, no XML header, no `<resources>` wrapper.
> Do NOT use Bash heredocs or echo to write the file. Always use the Write tool.

### Critical translation rules

1. **Format specifiers** (`%s`, `%1$s`, `%2$d`) — preserve exactly, same order
2. **XML escaping** — `\'` for apostrophes, `&amp;` for &, `\"` inside quoted strings
3. **YAML quoting** — strings with `%s` or special chars need quoting
4. **Placeholders in context** — understand what each `%s` represents before translating the surrounding sentence
5. **Short UI strings** — check usage context in code for ambiguous words ("Copy", "Share", "Hidden")

### Post-translation: verify agent output count

After all agents return, **count the translated strings** and compare against the expected count. If any are missing, identify which ones and translate them manually before proceeding. Agents occasionally drop strings silently.

**To count results without sandbox prompts**, use Grep tool (not Bash) to search for `<string name=` in `tmp/translate-results/` directory, or use Glob to list the files. Avoid Bash brace expansion (`{1,2,3}`) — it triggers a sandbox confirmation prompt.

## Step 5: Restructure Android XML

```bash
# Merge translations from a directory of agent output files:
python3 $SKILL/restructure.py LANG tmp/translate-results/

# Or from individual files:
python3 $SKILL/restructure.py LANG chunk1.xml chunk2.xml chunk3.xml
```

The script:
- Accepts a directory (globs `*.xml`) or multiple files
- Fixes double-escaped XML entities (`&amp;amp;` → `&amp;`) automatically
- Drops stale strings (not in base) by default (use `--keep-stale` to preserve)
- Mirrors base file structure: same comments, grouping, blank lines, string order

### After restructure: immediate validation

**Do NOT proceed without checking the script output:**

1. `Comments: N` — must be >0 (base has ~110 section comments). If 0, the script has a bug.
2. `Strings: N` — must equal total translation count.

### CRITICAL: Never use Bash for writing XML content

The `<!--` sequence contains `!` which bash interprets as history expansion, silently corrupting it to `<\!--`. Large XML heredocs also cause parser timeouts. **Always use the Write tool or Python scripts for XML files, never Bash heredocs/echo.**

## Step 6: Restructure Vue.js YAML

```bash
# Merge new translations and restructure:
python3 $SKILL/restructure_yaml.py LANG tmp/translate-results/vuejs_new.yaml

# Or just restructure existing translations:
python3 $SKILL/restructure_yaml.py LANG
```

The script:
- Reads base key order from `default.yaml`
- Merges existing + new translations (new overrides existing)
- Drops stale keys automatically
- Handles YAML `yes` key (boolean coercion) correctly
- Preserves proper quoting for strings with `%s`, colons, special characters

## Step 7: Restructure Play Store YAML

```bash
# Merge new translations and restructure:
python3 $SKILL/restructure_playstore.py LANG tmp/translate-results/playstore.yml

# Or just restructure existing translations:
python3 $SKILL/restructure_playstore.py LANG
```

The script preserves the base file's key order and comments, drops stale keys, and handles YAML quoting.

## Step 8: Validate

```bash
python3 $SKILL/validate.py LANG
```

Checks: coverage, format specifiers, string/key order, XML well-formedness, empty values, Play Store template variables and length limits (title ≤ 30, short_description ≤ 80).

## Step 9: Compile Play Store Descriptions

```bash
cd play && python3 compile_description.py && cd ..
```

Compiles all Play Store YAML translations into `fastlane/metadata/android/` text files. Run this after all languages are updated. Warns about title > 30, short_description > 80, and full_description > 4000 chars.

### Additional validation (only when Vue.js strings changed)

```bash
cd app/bibleview-js && npm run test:ci && npm run lint
```

**IMPORTANT:** `cd app/bibleview-js` changes the working directory. Always return to the repo root afterward.

### Spot-check
Review a sample from each thematic group (AI, bookmarks, settings, etc.) for terminology consistency.

## Common Mistakes

| Mistake | Fix |
|---------|-----|
| Inconsistent terms (same English word → different translations) | Run extract_glossary.py first, use glossary in agent prompts |
| Broken format specifiers (`%1$s` changed to `%s`) | validate.py catches these |
| Double-escaped entities (`&amp;amp;`) from agent output | restructure.py fixes these automatically |
| XML via bash heredoc/echo causes parser timeout or `<!--` corruption | **Always use the Write tool for XML/YAML files, never Bash** |
| Restructure drops all comments (0 in output) | Fix the script, don't fix the output manually |
| Agent silently drops strings | Count output lines and diff against expected count |
| Working directory stuck in `app/bibleview-js` | Always `cd` back to repo root after Vue.js commands |
| YAML `yes` key parsed as boolean `True` | restructure_yaml.py handles this automatically |
| Bash brace expansion `{1,2,3}` triggers sandbox prompt | Use Grep/Glob tools or `ls`/`for f in dir/*.xml` instead |
| Stale strings appended to output | restructure.py drops stale by default now |
| Cross-language contamination when translating multiple languages in one session | restructure.py now filters chunk files by language prefix. **Always clean `tmp/translate-results/` between languages**, or rely on the `{LANG}_` prefix filtering. |
| Multiline `<string>` tags in agent output silently dropped by restructure.py | Fixed: restructure.py now parses multiline entries via XML parsing with single-line regex fallback |

## Quick Reference — Single Language

```
SKILL=.claude/skills/update-translations

# Full workflow for language XX:
1. python3 $SKILL/extract_glossary.py XX → terminology glossary
2. python3 $SKILL/find_missing.py XX → see what's missing
3. python3 $SKILL/prepare_chunks.py XX → chunk files in tmp/translate-chunks/
4. Dispatch agents (1 per chunk) with glossary + chunk content
5. Save agent output to tmp/translate-results/*.xml, *.yaml, and *_playstore.yml
6. python3 $SKILL/restructure.py XX tmp/translate-results/
7. python3 $SKILL/restructure_yaml.py XX tmp/translate-results/vuejs.yaml
8. python3 $SKILL/restructure_playstore.py XX tmp/translate-results/XX_playstore.yml
9. python3 $SKILL/validate.py XX
10. cd play && python3 compile_description.py && cd ..
```

## All Languages Workflow

For translating/updating all supported languages at once.

### Language Quality Tiers

Languages are categorized by Claude Sonnet translation quality:

| Tier | Quality | Count | Languages |
|------|---------|-------|-----------|
| 1 | Excellent | 30 | ar, bg, ca, cs, da, de, el, es, fi, fr, hi, hr, hu, it, iw, ja, ko, nl, pl, pt, pt-rBR, ro, ru, sv, th, tr, uk, vi, zh-rCN, zh-rTW |
| 2 | Good | 18 | af, b+sr+Latn, b+sr+RS, bn, eo, et, fil, in, lt, ms, nb, ne, sk, sl, sw, ta, te, ur |
| 3 | Fair | 6 | az, kk, ml, my, uz, yue |

**Default:** Tier 1 only (30 languages). Use `--tier 1,2` for tier 2 or `--all-tiers` for everything.

### Step 0: Clean tmp directories

```bash
rm -rf tmp/translate-chunks/ tmp/translate-results/
```

**Always clean before starting a new all-languages run.** Leftover files from previous runs can cause cross-language contamination because all languages share the same tmp directories (files are language-prefixed, but stale files could confuse agents).

### Step 1: Scan

```bash
SKILL=.claude/skills/update-translations
python3 $SKILL/scan_languages.py --needs-work
```

Reports a table showing each language's missing string count for Android and Vue.js.

### Step 2: Dispatch Per-Language Agents

For each language that needs work, dispatch a sub-agent using `model: "sonnet"`.

**Batching:** Dispatch 5-8 agents at a time in parallel. Wait for each batch to complete before dispatching the next. This prevents overloading the system.

**Each sub-agent runs the full single-language pipeline independently:**
1. Extract glossary
2. Find missing strings (if nothing missing, report "complete" and stop)
3. Prepare chunks
4. Translate the chunks directly (small batches) or dispatch sub-sub-agents (large batches)
5. Restructure Android XML
6. Restructure Vue.js YAML (if applicable)
7. Validate

**Sub-agent prompt template:**

```
You are translating AndBible UI strings to {LANGUAGE_NAME} ({LANG_CODE}).
You MUST ONLY translate to {LANGUAGE_NAME}. Do not translate to any other language.

IMPORTANT: All commands in this workflow run within the default sandbox.
Do NOT use dangerouslyDisableSandbox for any command.

SKILL=.claude/skills/update-translations

Follow these steps exactly. Run all commands from the repo root.

1. Extract glossary:
   python3 $SKILL/extract_glossary.py {LANG}

2. Find missing:
   python3 $SKILL/find_missing.py {LANG}
   If nothing missing, report "complete" and stop.

3. Prepare chunks:
   python3 $SKILL/prepare_chunks.py {LANG}

4. For each chunk file in tmp/translate-chunks/{LANG}_*.txt, translate the strings:
   - Read ONLY files prefixed with "{LANG}_" from tmp/translate-chunks/
   - Do NOT read or use files belonging to other languages
   - Use the glossary from step 1 for consistent terminology
   - Translate each string. Preserve format specifiers (%s, %1$s, %d) exactly.
   - Android: save as <string name="...">...</string> XML lines using the Write tool
     to tmp/translate-results/{LANG}_chunk_N.xml (one file per input chunk)
   - Vue.js: save as YAML key-value pairs using the Write tool
     to tmp/translate-results/{LANG}_vuejs.yaml
   - Play Store: save as YAML key-value pairs using the Write tool
     to tmp/translate-results/{LANG}_playstore.yml
     IMPORTANT: Preserve {{ variable }} placeholders exactly (e.g. {{ title }}, {{ total_documents }}).
     title must be ≤ 50 chars, short_description ≤ 80 chars.
   - NEVER use Bash heredocs for XML — always use the Write tool.

5. Restructure Android:
   python3 $SKILL/restructure.py {LANG} tmp/translate-results/
   (The script automatically filters for {LANG}_*.xml files only)

6. Restructure Vue.js (if Vue.js strings were translated):
   python3 $SKILL/restructure_yaml.py {LANG} tmp/translate-results/{LANG}_vuejs.yaml

7. Restructure Play Store (if Play Store strings were translated):
   python3 $SKILL/restructure_playstore.py {LANG} tmp/translate-results/{LANG}_playstore.yml

8. Validate:
   python3 $SKILL/validate.py {LANG}

Report the validation result.
```

### Step 3: Final Validation

After all batches complete, run a sweep:

```bash
SKILL=.claude/skills/update-translations
# Validate all tier 1+2 languages
for lang in $(python3 $SKILL/scan_languages.py --tier 1,2 --json | python3 -c "import sys,json; [print(l['lang']) for l in json.load(sys.stdin)]"); do
  python3 $SKILL/validate.py $lang 2>&1 | tail -1
done
```

### Step 4: Compile Play Store

After all languages are validated:

```bash
cd play && python3 compile_description.py && cd ..
```

### Quick Reference — All Languages

```
SKILL=.claude/skills/update-translations

0. rm -rf tmp/translate-chunks/ tmp/translate-results/    → clean stale files
1. python3 $SKILL/scan_languages.py --needs-work  → see what needs work
2. For each language: dispatch sub-agent (model: sonnet) with template above
3. Batch 5-8 agents at a time, wait between batches
4. Final validation sweep
```
