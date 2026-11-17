# Phos Nextcloud sync

AndBible can synchronize bookmarks, workspaces and reading plan progress with the
[Phos](https://github.com/GermanSwordModules/Phos-Nextcloud) app for Nextcloud. This is a second,
independent sync channel next to the file based cloud sync (Google Drive / Nextcloud folder):
both can be enabled at the same time.

Settings → Synchronization → *Phos Nextcloud*: server URL, user name and a Nextcloud app
password (a login password is exchanged for an app password on the first run).

## How it works

Code: `app/src/main/java/net/bible/service/cloudsync/phos/`.

- `PhosSyncEngine` talks to the OCS API `/ocs/v2.php/apps/phos/api/v1` (`/status`, `/devices`,
  `/push`, `/changes`, `/conflicts`). Records are `{collection, type, key, rev, hlc, payload}`;
  only the server merges. The client detects local changes by comparing what it renders now
  with a shadow of what the server last confirmed, pushes them with the revision they were based
  on and adopts the server's answer. State lives in `files/phos-sync-state.json`.
- Adapters map AndBible's databases to the record types the other Phos clients use:

| Record | Key | AndBible data |
|---|---|---|
| `label` | `label:<name>` | `Label` (matched by name; special labels are not synced) |
| `bookmark` | `bm:<uuid hex>` | `BibleBookmark`, `GenericBookmark`, `StudyPadTextEntry` with notes and label links |
| `workspace` | `ws:<workspace id hex>` | `Workspace`, its `Window`s and `PageManager`s (matched by position) |
| `planProgress` | `plan:<plan code>` | `ReadingPlan`, `ReadingPlanStatus` |

- Bible locations travel as module + OSIS book + chapter + verses in the module's
  versification. AndBible-only details (exact ordinals, text offsets, label styles, the HTML of
  a note, study pad layout per label) travel in an `ab` object inside the payload; other
  clients keep it.
- Notes are sent as plain text/Markdown (`noteMd`). An HTML note keeps its HTML as long as the
  text is not edited elsewhere; a note edited on another device comes back as Markdown.
- Bookmark ids of other clients that are not UUIDs are mapped to a UUID derived from them
  (`md5("phos-bookmark" + NUL + id)` with version/variant bits), the same one every Phos client
  derives; changes go back under the original key.
- `label` and `workspace` records also carry the sending AndBible device's own `Label.id` /
  `Window.id` as `abId` inside the `ab` object. An importing AndBible device reuses that id
  instead of deriving its own from the name/position, so it converges with the classic file
  based sync (which carries the true id) on one row instead of creating a duplicate once that
  sync catches up. Other Phos clients ignore the field.
- Applied records are written with plain SQL, so the sync triggers log them and the file based
  sync, when enabled, passes them on to other AndBible devices.
- A sync that would delete more than 10 records, and more than a third of that record type, is
  held back instead of sent (`PhosSyncEngine.run(confirmDeletes = true)` sends it anyway). The
  settings screen offers to confirm it, both right after a manual "Synchronize now" and whenever
  the screen is opened while a hold is still pending.

## Tests

`./gradlew :app:testStandardFdroidDebugUnitTest --tests 'net.bible.service.cloudsync.phos.*'`
