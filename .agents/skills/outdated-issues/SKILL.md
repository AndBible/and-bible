---
name: outdated-issues
description: >
  Find old open AndBible GitHub issues that are likely outdated (area rewritten,
  already implemented, or no longer relevant). Use when the user asks to find
  outdated issues, the next 10 old issues, close-as-outdated candidates, or
  runs /outdated-issues.
---

Find open `AndBible/and-bible` issues that are **likely outdated**. Default: the oldest 10 that pass the bar. If the user says they already handled a batch, skip those and continue from the next oldest.

Do **not** close issues unless the user explicitly asks.

This is **Android repo** issue hygiene. iOS-native tickets belong in `AndBible/and-bible-ios`. Shared Vue.js/BibleView symptoms may apply to both.

## What “outdated” means

Most likely **no longer relevant**, or the **area changed so much** the ticket no longer describes the current app.

**Count it** when:

- The bug is against a renderer/UI/storage/CI path that was replaced (especially pre-Vue 3.x Bible view).
- Maintainers said it was fixed / done, and current code agrees.
- The named API, product, or workflow is gone (Ivona, ARC Welder, `$SDCARD/jSword` as the real module home, `dedicatedLinksWindow`, old `.java` select-mode action bar).
- A one-line “check this” or firebot dump from a dead stack, with no remaining signal.

**Do not count it** just because it is old. Still-valid feature requests and bugs stay off the list (genbook tree selector, DisplayLevel, search autocorrect, Images category, `AlarmManager.set()`, TalkBack on the current book grid, stylus + current label spinner).

Age is a search order, not a verdict.

## Workflow

1. List oldest open issues:
   `gh issue list --repo AndBible/and-bible --state open --limit 120 --search "is:issue is:open sort:created-asc"`
2. Walk **oldest first**. Skip closed, skip issues the user already handled this session, skip ones that still look current.
3. Fetch body + comments for candidates (`gh issue view N`).
4. **Verify against current code** before listing. Grep the named class/setting/UI. A 2019 title is not enough.
5. Stop at **10** you would really close as outdated (or fewer if the well is dry). Mention 1–2 close cousins after the list if useful.
6. Optionally note a few old issues you **skipped because they still look real**, so they are not closed by accident.

## How to verify

Match the ticket’s world to a **milestone** below, then to **today’s code**.

| Ticket talks about | Check now |
|---|---|
| White screen, HISB hang, `jsword/css` on SD card, old “select mode” action bar, special/link window HTML | Vue reader (4.0). Pre-4.0 repro is stale unless you can still do it in Vue. |
| Bookmark OSIS+v11n lookup, `getBookmarkVerseText()`, WebViews in bookmark list, `dedicatedLinksWindow` | Room bookmarks + `kjvOrdinal*` + Vue bookmark UI. That lateinit field is gone. |
| `$SDCARD/jSword` as module home + dropping Lucene indexes there | `SharedConstants.modulesDir` (app-specific). `/sdcard/jsword` is only a manual-install augment path. |
| “Use X from Maven instead of a bundled jar” | `*.gradle.kts` dependencies. |
| Night-mode 3-dot toggle vs WebView | Manual / Automatic / System + workspace colors. |
| Book chooser LTR only in landscape | `row_order_opt` / `isLeftToRightEnabled` in `GridChoosePassageBook`. |
| 2020 CI tagging / versionName uniqueness | `.github/workflows/` + `scripts/increment-version.sh`. |
| Crash stack naming a class/property | That symbol still exists? |

Comments like “fixed in build N”, “done in #N”, “leave open for 3.3 backport” are hints — still confirm in code.

## Output

Operator list, oldest first. For each:

```
### N. #ID — title (year)
One or two sentences: why outdated, which milestone/code. Link the issue.
```

No close comments unless asked. If asked to close, use a short “Outdated” (or the user’s wording) and only for issues you verified.

## Milestones

Use these as the default dating lens. If a date might have moved, confirm with `git` / tags / `fastlane/metadata/android/en-US/changelogs/` rather than inventing.

### Releases (Android)

| When | What |
|---|---|
| 2019-01 ~ **3.0** | Post-Java→Kotlin era (conversion late 2018–early 2019). Tickets citing `*.java` paths are location-stale; the bug may still exist in the `.kt` file. |
| 2019-02 ~ **3.1** | Label `Affects: 3.1` often means the old native Bible/select UI. |
| 2020-01 ~ **3.2** | Last major pre-workspace line (e.g. 3.2.333). |
| 2020-06–2021-04 **3.3** | Workspaces, per-window/per-workspace display settings, pinned windows, download-docs overhaul. Last 3.3 prod ~ **3.3.400** (2021-04). Reader is still **jQuery/HTML WebView**. |
| **2021-09-09 4.0.601** | Vue.js Bible view ships. Study Pads, character-level bookmarks, My Notes merged into bookmarks, click-for-Strong’s, new verse/bookmark modals. [PR #888](https://github.com/AndBible/and-bible/pull/888) (Vue 3 frontend) merged **2021-01-21**; betas through 2021 already had Vue. |
| **2023-11 5.0** (741+) | EPUB, generic/non-Bible bookmarks, cloud sync (Google Drive first), discrete mode in the main app, workspace colors, chained link windows, sync groups, MyBible/MySword, custom repos, deep links. |

Build numbers: 3.x ≈ 280–400, 4.0 ≈ 600s, 5.0 ≈ 740+. A report on **3.3.3xx** is pre-Vue.

### Reader (highest leverage)

| When | What |
|---|---|
| 2019-10/11 | First `bibleview-js`: **jQuery + webpack** on the old HTML WebView (scroll/offset). Not Vue. |
| 2020-11-05 | Vue CLI scaffolding starts. |
| 2020-11-12 | PR #888 opened (“new vue3 bibleview-js frontend”). Vue 3 on 2020-11-13. |
| 2021-01-21 | #888 merged. |
| 2021-09-09 | **Users** get it in 4.0. |

Treat as pre-Vue unless the report is 4.0+ or a 2021 Vue beta: WebView white screens, HISB/Chromium hang, `jsword/css` on the card, 3.1 select-mode toolbar, “special window” Strong’s/search state, double-load flicker of minimised HTML windows.

### Bookmarks & windows

| When | What |
|---|---|
| 2019-11–2020 | Room for workspaces; reading plans → Room (2020-04); bookmark tables → Room (**2020-10**). |
| 4.0 | `kjvOrdinalStart` / `kjvOrdinalEnd`; Study Pads; char-accurate highlights; Vue bookmark modal/buttons. Cross-v11n “delete doesn’t show” (old OSIS+v11n store) is the old model. |
| 5.0 | Bookmarks on non-Bible docs; chained **links windows** (list of `isLinksWindow`, not `dedicatedLinksWindow`). |

### Storage, SDK, night mode, CI

| When | What |
|---|---|
| Current | Modules live in app-specific `SharedConstants.modulesDir`. `/sdcard/jsword` and `/sdcard/sword` are optional manual-install paths (storage permission). Lucene indexes go under the app module dir. |
| minSdk | Raised over time to **23 (Android 6+)**. Tickets about API 14–19-only devices or KitKat-as-current are era-stale; a still-valid code fix (e.g. `AlarmManager.setExact`) is not automatically outdated. |
| Night mode | Manual / Automatic / System + **workspace colors** (5.0). Old 3-dot toggle vs WebView (#488 family) is 3.x. |
| Discrete | Flavor **2021-10**; in the main 5.0 app as well as the calculator disguise. |
| CI / versions | GitHub Actions; `make increment-version` → `scripts/increment-version.sh` (`production-X` / `test-X`). 2020 “how do we tag master vs feature?” threads are process-stale. |

### Other cutovers (use when the ticket is in that area)

- **Book chooser:** 4.0 added LTR/row order, grouping, long names (`GridChoosePassageBook` menu). A 3.2 “no LTR in portrait” request is done; TalkBack on the custom `ButtonGrid` may still be real.
- **Search:** Strong’s “find all occurrences” + `strong:` queries exist; a dedicated Find-dialog Strong’s field may still be open. 3.3 firebot “occurrences failed” dumps are usually the old link-window path.
- **TTS:** `replaceDivineName` / `OsisToBibleSpeak` exist. Ivona-the-product is gone; Selah-pause / pronunciation polish can still be valid.
- **iOS:** Separate native app (`and-bible-ios`), **same Vue BibleView**. Don’t file or close Android-native issues against iOS.

When adding a new milestone later, put it in **this table**, not in a second copy elsewhere.
