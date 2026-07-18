# Custom background image

**Date:** 2026-07-18
**Status:** Design approved, pending implementation plan
**Origin:** GitHub issue [#3617](https://github.com/AndBible/and-bible/issues/3617) (Sponsored, Type: Feature) — let users set a wallpaper behind the Bible text instead of only a solid colour.

## Problem / goal

Today the background behind Bible text can only be a solid colour (separate day/night), optionally with a fixed noise-texture overlay whose opacity is adjustable. Users want to place a **personal image from their gallery** behind the text, with an opacity control to preserve readability, configured per workspace and per time mode (day/night).

Non-goals (decided during brainstorming):
- No built-in / curated texture library — gallery images only.
- No separate "dimming overlay" slider — a single opacity slider that fades the image toward the existing (contrast-tuned) background colour is the readability control.
- Background images are **not** rendered in monochrome / e-ink mode (a photo would look broken on e-ink; the design guidance in `CLAUDE.md` requires grayscale there).

## Key architectural decision: image = SWORD module

Each imported image becomes its **own SWORD module** (one module per image), modelled exactly on the existing manually-installed TTF **font module** mechanism (`TtfBook.kt`). This is deliberate and gives three things for free:

1. **Device sync** — module files already sync across devices via the document/device sync subsystem (`net.bible.service.cloudsync.documents.*`). A manually-installed synthetic module passes the `DocumentSync.installedSyncableBooks()` filter (`!isPseudoBook && !isMyDocument`) automatically, so it syncs as long as we do **not** tag it `AndBiblePseudoBook`.
2. **Backup/restore** — `BackupControl.addBookToZip` already packages manually-installed modules; we add a branch analogous to the existing `isManuallyInstalledTtf` branch.
3. **Reusable library** — because images persist as modules, the picker can offer a library of previously imported images (shared across workspaces, synced across devices), plus "import new from gallery".

`WorkspaceEntities.Colors` stores a **reference** (the module initials), not a file path.

Consequence accepted: image modules appear as "And Bible" **addons** in the Documents / Downloads / Cloud listings, exactly like font modules do today. This is intended — the user can see sync status and delete them there. They are not hidden.

## Data model — `WorkspaceEntities.Colors`

Add four nullable fields (they inherit at field level through `actual()` like every other colour field — window → workspace → global → default):

| Field | Type | Meaning |
|---|---|---|
| `dayBackgroundImage` | `String?` | Initials of the day image module (`null` = none) |
| `nightBackgroundImage` | `String?` | Initials of the night image module (`null` = none) |
| `dayBackgroundImageOpacity` | `Int?` | 0–100, default 100 |
| `nightBackgroundImageOpacity` | `Int?` | 0–100, default 100 |

Changes:
- `Colors.merge(override)` — extend the per-field null fallback for the four new fields.
- `TextDisplaySettings.default` — image fields `null`, opacity fields `100`.
- `Colors.toJson()` — automatic (kotlinx serialization of the data class); it already rides along in `displaySettings.toJson()` → `set_config`.
- **Room migration** — bump `WORKSPACE_DATABASE_VERSION`, new migration adding four columns (`colors_dayBackgroundImage TEXT DEFAULT NULL`, `colors_nightBackgroundImage TEXT DEFAULT NULL`, `colors_dayBackgroundImageOpacity INTEGER DEFAULT NULL`, `colors_nightBackgroundImageOpacity INTEGER DEFAULT NULL`), registered in `DatabaseContainer.kt`. Mirror the existing noise columns' migration.

## Synthetic module builder — new `BackgroundImageBook.kt`

Mirrors `net.bible.service.sword.ttf.TtfBook`:

- `addBackgroundImageBook(file: File)` — build an in-memory `.conf` string, construct `SwordBookMetaData(conf.toByteArray(), initials)` (byte-array ctor → no on-disk `.conf`), attach `NullBackend`, register with `Books.installed().addBook(book)`. Conf contents:
  ```
  [<initials>]
  Description=<displayName>
  Category=And Bible
  ModDrv=RawGenBook
  DataPath=./background/
  Encoding=UTF-8
  AndBibleProvidesBackgroundImage=<displayName>;<filename>
  AndBibleMinimumVersion=<current>
  ```
- **Initials scheme:** `"BGIMG_" + <sanitized display name>`, dedup-guarded (if a book with those initials already exists, disambiguate with a numeric suffix). Display name derived from the picked file's display name (extension stripped).
- `Book.backgroundImageFile` extension — resolve on-disk file from the `AndBibleProvidesBackgroundImage` property (`"name;filename"`, index 1) under `File(SharedConstants.modulesDir, "background")`.
- `Book.isBackgroundImageModule` — `getProperty("AndBibleProvidesBackgroundImage") != null && (bookMetaData as? SwordBookMetaData)?.configFile == null` (the `configFile == null` guard distinguishes a manually-installed synthetic module from any future downloaded add-on carrying the same marker, exactly as `isManuallyInstalledTtf` does).
- `BackgroundImageSwordDriver : AbstractBookDriver` — `delete(book)` deletes `book.backgroundImageFile` and calls `Books.installed().removeBook(book)`; `isDeletable` = file is writable.
- `addManuallyInstalledBackgroundImageBooks()` — scan `modulesDir/background` for image files, call `addBackgroundImageBook` on each. This is the startup/re-discovery + post-restore entry point.

## Addon registry — `AndBibleAddons.kt`

Add, mirroring the font entries:
- `providedBackgroundImages` / `backgroundImagesByModule` (module initials → `{displayName, filename, file}`).
- `backgroundImageModuleNames`.
- Include the new marker in cache invalidation (`clearCaches()` already recomputes lazily).

## Import + selection UI

- **Picker** — `InstallZip.kt`: add `image/png`, `image/jpeg`, `image/webp` to the `ACTION_OPEN_DOCUMENT` `EXTRA_MIME_TYPES` allow-list, and add an `installBackgroundImage` branch that copies the picked content-URI stream into `SharedConstants.modulesDir/background/<displayName>`, then calls `addManuallyInstalledBackgroundImageBooks()` + `AndBibleAddons.clearCaches()`. Dispatch by MIME/extension like the existing `installTtf` branch.
- **Chooser** — launched from `ColorSettings`. Two preferences: "Background image (day)" and "Background image (night)". Tapping one opens a chooser (dialog or lightweight activity) that lists installed background-image modules as thumbnails, plus **"None"** and **"Import from gallery…"**. On selection the chosen module's initials (or `null` for None) is written into `Colors` (day/night respectively). "Import from gallery…" runs the picker above, then selects the freshly imported module.
- **Opacity** — two `SeekBarPreference`s (max 100) in `color_settings.xml` for day/night image opacity, wired through `ColorSettingsDataStore` exactly like the existing `noise_day` / `noise_night` keys.
- The picked-image copy + module creation reuse the `installTtf` copy-and-register template.

## Serving the image to the WebView

New `/background/` `WebViewAssetLoader` `PathHandler` in `BibleView.kt` (registered alongside `/fonts/`, `/module-style/`, etc.), mirroring `FontsAssetHandler`:
- Path `<moduleInitials>` (optionally `<moduleInitials>/<file>`).
- Resolve the book by initials, look up its filename from `AndBibleAddons.backgroundImagesByModule`, return the raw image bytes via `WebResourceResponse` from `File(book.bookMetaData.location, filename)` with the correct MIME type.
- Unknown/uninstalled module → `notFound` (Vue then falls back to the background colour).

The Vue side therefore only needs the **module initials** to build the URL `/background/<initials>`.

## Rendering — `BibleView.vue`

The existing `.background` div's element-level `opacity` is already used by the noise layer, so the image gets its **own layer**:

- New `.background-image` div at `z-index: -2` (between noise `-3` and content). CSS: `background-image: url(/background/<initials>)`, `background-size: cover`, `background-position: center`, fixed to the viewport; `opacity: var(--bg-image-opacity)`. Fading toward the underlying `.background` colour div is what the opacity slider controls.
- Day/night selection mirrors the existing colour/noise logic in `topStyle` / `backgroundStyle`: choose `nightBackgroundImage` vs `dayBackgroundImage` and the matching opacity, emit `--bg-image-opacity` and the URL.
- **Monochrome / e-ink:** when `appSettings.monochromeMode || appSettings.einkMode`, emit no URL (the layer stays empty) — only the background colour shows.
- **Missing module:** the `/background/` request 404s → the layer shows nothing → background colour remains. Graceful; no active cleanup of stale references needed.
- `config.ts`: add the four fields to `Config.colors` type and reactive defaults (`dayBackgroundImage`/`nightBackgroundImage: ""` or `null`, opacities `100`). The generic `set_config` merge loop populates them with no further change.

## Backup & sync

- **`BackupControl.addBookToZip`** — add an `isBackgroundImageModule` branch: `addModuleFile(outFile, b.backgroundImageFile)` (zip entry `background/<file>`), mirroring the TTF branch.
- **`BackupControl.extractAndRegisterModuleArchive`** — call `addManuallyInstalledBackgroundImageBooks()` after extraction (alongside the existing `addManuallyInstalled*Books()` calls) so a restored/synced image module is reconstituted.
- **Sync** — automatic; no code needed beyond *not* marking the module pseudo. `BookInstallWatcher.bookAdded` auto-queues a cloud push when the module is added (if sync enabled). `DocumentArchiver` uses the same `createSingleModuleZip` / `installModuleArchive` path, so the round-trip works.
- **DocumentType** — rides the existing `SWORD` bucket like fonts (`DocumentArchiver.documentTypeOf` `else` branch). No dedicated type.

## Stale references on deletion

If a user deletes an image module (from the Documents list) while a workspace still references it, the reference in `Colors` becomes stale. This degrades gracefully (404 → background colour), so **no active reference cleanup** is implemented. Noted as an accepted, low-impact edge case.

## Testing

**Kotlin** (`app/src/test/java/...`, Robolectric):
- `BackgroundImageBook` create + register round-trip (mirror `TtfBook` / `ModuleBackupRoundTripTest`): drop a dummy image in `modulesDir/background`, assert the book registers with the marker, correct initials, `isBackgroundImageModule == true`, and `backgroundImageFile` resolves. Clean up with unique initials + `removeBook` in `@After` (per the Robolectric module-registration notes).
- Backup packaging: `addBookToZip` produces a `background/<file>` entry for an image module.
- `Colors.merge()` / `TextDisplaySettings.actual()` inheritance of the four new fields (window/workspace/global fallthrough).
- Migration applies cleanly (four columns present, defaults correct).

**Vue** (`app/bibleview-js/src/__tests__/*.spec.js`):
- The background-image layer computes the correct `/background/<initials>` URL and opacity for day vs night.
- No URL emitted when `monochromeMode` / `einkMode` is set.
- Opacity CSS custom property binds correctly.

## Files touched (for phasing the plan)

1. **Data model / migration:** `WorkspaceEntities.kt`, new migration class, `DatabaseContainer.kt`.
2. **Module + registry:** new `BackgroundImageBook.kt`, `AndBibleAddons.kt`, discovery wiring (startup + `extractAndRegisterModuleArchive`).
3. **Import + settings UI:** `InstallZip.kt`, `ColorSettings.kt`, `color_settings.xml`, chooser (new activity/dialog + layout), `strings.xml`.
4. **WebView serving:** `BibleView.kt` (`/background/` handler).
5. **Vue rendering:** `BibleView.vue`, `config.ts`.
6. **Backup:** `BackupControl.kt`.
7. **Tests:** Kotlin + Vue as above.

Per the phased-execution rule (≤5 files per phase), the implementation plan will group these into ordered phases with a verification step (compile / relevant tests) between each.

## Strings

New user-facing strings in `app/src/main/res/values/strings.xml` (English only during development): background-image day/night preference titles + summaries, image opacity titles, chooser labels ("None", "Import from gallery…", chooser screen title). No BibleView-JS (`default.yaml`) strings needed — the Vue side renders no new text.
