---
name: issue-management
description: Use for AndBible issue and project-board work — triaging new issues or PRs, closing or relabelling issues, enriching an issue description, choosing what to work on next, moving board items, sponsorship labels, or checking the tracker for drift. Triggers: "triage issues", "käy läpi uudet tiketit", "mitä seuraavaksi", "päivitä board", "rikasta tiketti", "sulje duplikaatit", "issue triage", "pick next work", "board drift".
---

# AndBible issue and board management

Maintainer-only: if `docs/superpowers/tools/triage/` does not exist, stop and say so.

The tracker is `AndBible/and-bible`; the board is the triage org project named by `target_project` in
`docs/superpowers/triage/board-migration.yaml`; #5 ("Tuomas' project board") is the
read-only archive, closed after the new board is verified and configured. Design:
`docs/superpowers/specs/2026-10-10-issue-triage-and-project-board-design.md`. Tools:
`docs/superpowers/tools/triage/` (operation reference: `batch_schema.md`). Data: `docs/superpowers/triage/`.

## Hard rules

- The container never writes to GitHub. Every change is a batch file under `triage/batches/`, committed for the maintainer to review and apply on the host. The host dry-runs with `python3 docs/superpowers/tools/triage/apply.py docs/superpowers/triage/batches/<file>.json`, then may apply with the same command plus `--apply`, then commits `triage/applied/` and pulls it back. These commands do not authorize running them in the container.
- Never write hour estimates. Suggest `Sponsor me!` + `Estimate needed`; the maintainer estimates.
- Priority lives only on the board (Priority field). Never add `Prio:` labels.
- Labels must be in `triage/taxonomy.yaml`. Never delete a label named by any file under `.github/`.
- GitHub text is English, short and specific; comments end with `_— issue triage, Oct 2026_` as the final line.
- Never write to project #5 (validation refuses it). Board batches name their `project`.
- The board export needs the Projects permission. If the container token lacks it, ask the maintainer to run `python3 docs/superpowers/tools/triage/github.py board-export --project <target_project> > docs/superpowers/triage/snapshot/board.json` on the host.

For the initial new-board migration, follow Part 2 O2/O5/O6 of the implementation runbook:
`stage.py board-migration --out batches/002-new-board.json`, then verify the host-applied
batch against a fresh target export with `stage.py board-verify --batch batches/002-new-board.json`.
The maintainer sets `target_project` before staging; never guess the project number.

## Board model

Status (workflow): Needs triage → Backlog → Next up → In progress → In review → Done; Waiting.
Priority: P0 Blocker, P1, P2, P3, Someday. Size: S, M, L. Views: Priorities, Quick wins, Work,
Triage inbox, PRs, Sponsor me. Automation adds new items as Needs triage and closes to Done.

## Verdicts

Issues: done / duplicate / obsolete / wontfix / stale-info (close with a comment) ·
keep / candidate (labels + Priority; candidates also require P1 or P2, Size, and enrichment).
PRs: reimplement (close with thanks; idea goes to an issue) · reject · review (stays open, In review;
the maintainer's own PRs land here) · stale-draft.
Decision file format: see "Decision file format" in
`docs/superpowers/plans/2026-10-10-issue-triage-and-project-board.md`, Task 8.

## Flows

### 1. Inbox triage (new issues and PRs)
1. `cd docs/superpowers/tools/triage && uv run --no-project --with pyyaml python fetch.py snapshot`; get a fresh `board.json` (see Hard rules).
2. Items with Status Needs triage are the inbox. Read issues from `snapshot/issues/<n>.md` and
   PRs from `snapshot/prs/<n>.md`; check `closed-index.tsv` and same-area open items for
   duplicates. Check the code for "already done".
3. Write `triage/decisions/inbox-<date>.yaml`, run `report.py`, and show the maintainer the
   report (Finnish summary, English report). Apply their overrides.
4. `stage.py decisions decisions/inbox-<date>.yaml --out batches/NNN-inbox-<date>.json`. Commit the
   submodule and the gitlink bump. Give the maintainer the host commands.

### 2. Enrich an issue

Write `triage/enrich/<n>-body.md`: Summary / Current behavior (bugs: Steps to reproduce, Expected,
Actual) / Proposed change (code pointers, Compose vs legacy view) / Open questions / Related, then
`<details><summary>Original description by @author</summary>` + the complete original body verbatim +
`</details>`. Preserve every original character in order; never paraphrase, omit, or normalize it. Add a short notification `comment`. `stage.py` refuses an enrichment that drops the original or exceeds 65536 chars.

### 3. Pick next work

From the Priorities and Quick wins views (P1–P2, Size S first, demand from `index.tsv`), propose
2–5 items with AskUserQuestion. Move the chosen ones to Next up through a batch. Each picked item then
starts its own superpowers chain: brainstorming → spec/plan → SDD, with the issue as input.

### 4. Close the loop

Fix commits start `Fixes #NNN (short description)`. When work is in a PR: Status In review; after
merge, the board automation moves it to Done. Check after each release with flow 5.

### 5. Drift check

Re-fetch the snapshot and board. Report: open items missing from the board, open items without
Priority, closed items whose Status is not Done, labels outside the taxonomy, new issues that
duplicate older ones. Stage fixes as a batch.

## Operational cautions

- A journal is a completion log, not an exactly-once transaction. If a remote write may have succeeded but its journal entry is missing, stop and manually inspect the live GitHub state before any retry; blind reruns can duplicate comments or issues. Use one host operator and preserve reviewed batches.
- Candidate enrichment must include Priority P1 or P2 and Size as well as the enriched body; do not mark a candidate ready if any are missing.


## Staging safety contracts

- Taxonomy deletion checks complete live label membership immediately before the
  host write. Merge sources must be empty after transfer; retired labels must match
  the reviewed snapshotted members. Added open/closed issue or PR associations
  block deletion and require restaging. Legacy deletion defaults to empty members.
- Decision staging validates the actual exported board SINGLE_SELECT fields and
  option names, including unchanged requested values. Desired migration YAML is
  not evidence the new-board migration has happened. Missing/incompatible exports
  refuse staging before any content batch is emitted.
- `reimplement` must hand off to an enriched P1/P2 candidate with size. `carry_to`
  requires that target's candidate decision in the same batch, containing the
  source PR URL; new_issue requires that URL and the verbatim PR description in
  its original-description details block. PR closure depends on completion of
  target enrichment and board setup, not just issue creation. No already-candidate
  shortcut is implemented. Reconcile partial new-issue creation after board failure.

## Calibration notes (O7, area sync-backup, 2026-10-16)

Learned from the calibration area: 43 items, maintainer-reviewed. Decision file:
`docs/superpowers/triage/decisions/sync-backup.yaml`. It is the worked example; read it before
drafting an area.

### Policies the maintainer set

- **Age policy for bugs.** If a bug has had no new report or comment for **12 months**, close it as
  `stale-info` with a friendly "please reopen or comment if it still happens with a current version"
  comment. Do not investigate it further. Support tickets (osTicket) about a known bug are noted on
  its GitHub issue, so the latest comment date is the latest report.
  **Exception:** if you verified in the dev code that the defect is still there *and* the fix is
  Size S, keep it at **P3** (not P1/P2). Examples: #3039 zip duplicate entry (last report 2024-03),
  #2518 global `lastForceSyncAll`. The age policy is for **user-reported** bugs only. The
  maintainer's own technical notes (no user report; for example #3392 Nextcloud error handling) and
  feature or improvement requests are judged on value.
- **Prefill the age policy before drafting an area:**
  `cd docs/superpowers/tools/triage && uv run --no-project --with pyyaml python prefill.py <area>`.
  - It appends a `stale-info` decision with the standard comment ("last reported in <Month YYYY> ...")
    for every user-reported bug of the area with no report or comment for 12 months.
  - It prints the maintainer notes (left for judgement) and the recent bugs.
  - Existing decisions are kept verbatim, and a rerun adds nothing.
  - Then read only the prefilled items quickly. Override one only for the exception (verified in dev +
    Size S -> keep P3) or when it duplicates a newer issue. Spend the real reading time on the recent
    bugs, the maintainer notes and the feature requests.
- **Judge against `dev`, not `current-stable`.** The Compose port on dev changed a lot of code and
  probably fixed old problems. Little bug-fixing goes into current-stable any more; a Compose beta
  from dev is next. Verify every "still there" and "done" claim in dev. Phrase comments for the
  current or upcoming version, never "fixed in stable".
- **Implemented but unreleased** (on dev, not an ancestor of `origin/current-stable`) is not `done`.
  It is `keep` + `status: In review` + a short comment ("implemented, being tested, closed when it
  ships"). Drop `Sponsor me!` from it. Example: #3837 WebDAV. Close it with flow 5 after the release.
- The maintainer reviews from a Finnish summary. For each item it gives **what the issue is about**
  in one sentence, then the verdict and why, so nobody needs to open the tickets. Lead with the
  ranking principle (P1/P2/P3/Someday) and end with a "look at these first" list (low/medium
  confidence, wontfix, merges).

### Verdict boundaries, with examples from this area

- `done`: name the commit and the first release tag containing it. Check with
  `git merge-base --is-ancestor <sha> origin/current-stable` and
  `git tag --contains <sha> | grep ^v | head -1`. Examples:
  - #657: restore asks what to restore (`f6f4dd1dd`, 5.0.740), and per-database reset (`d00534fad`,
    5.1.1098).
  - #2634: sync on network restore (`d816a834f`, 5.0.840).
  
  If the fix only partly covers the request (#2097: notes export per StudyPad, not all at once),
  use `confidence: medium` and invite a new issue for the rest.
- `duplicate`: when several issues report the same crash, pick one **target that stays open**
  (usually the one with the clearest trace or the most tickets), enrich it, and close the rest into
  it. #1726, #3138 and #3230 → #3039. A crash with several entry points where most are already
  fixed also becomes a duplicate of the issue for the remaining one: #1899, #3026 and #3159 →
  #3135, because only the welcome-screen restore is still unguarded. Say in the comment which path
  is already fixed.
- `obsolete`: the platform or path is gone. Examples: #2748, where Windows Subsystem for Android is
  discontinued; #1972, a crash upgrading straight from a pre-4.0 database. Point to the alternative
  if there is one.
- `wontfix`: only where the maintainer already said "not on the roadmap", or the idea goes against
  the design. Example: #1931, search indexes in the module backup. Always `confidence: medium` or
  lower, so the maintainer sees it.
- `stale-info`: cannot be reproduced, the reporter has gone quiet or confirmed it works, or the age
  policy applies. Examples: #1822 (reporter confirmed it works), #2076 (not reproducible), #3089
  (age policy).
- `keep` vs `candidate`: `candidate` = P1/P2 **and** worth an enriched body. A recent report
  that is already precise and structured (timbze's #3863 and #3884) stays `keep` P2/S **without**
  rewriting; it still appears in Quick wins through its board fields.
- **Misassigned area:** decide the item anyway and set the right `Area:` label. Example: #940 and
  #2518 are about *window* sync, not cloud sync, so they move to `Area: Windows & workspaces`.

### Labels

- Labels are the full target set: always one `Type:` and one `Area:`.
- Remove `Affects: Alpha` from bugs whose alpha is long released.
- Remove `Waiting for information` when nobody is actually asked anything.
- Remove `Type: Discussion` from a concrete feature request.
- A maintainer note that is really a refactor or deprecation gets `Type: Code improvement`.
  Example: #2301 `fullBackupContent`.
- `Sponsor me!` + `Estimate needed` go only on feature requests that are larger and wanted by
  users. Examples: #3757 preference sync, #3425 sync subscriptions. Never on bugs.

### Good closing comments (quoted)

> Both parts of this are in the app now. Restoring a database backup asks which parts to restore
> (bookmarks, workspaces, reading plans, ...) since 5.0, and Backup & Restore has a per-database
> reset since 5.1.1098, which can reset just the workspaces and windows while keeping bookmarks
> and notes. See https://andbible.org/docs/backup_restore/

> Installing a zip from such a file now shows an error instead of crashing (5.0.874, #3160). The
> remaining crash, restoring a database from the welcome screen when the file cannot be read, is
> tracked in #3135.

These comments are specific: they name the version and what the user can do now. Do not write
the marker line yourself; stage.py appends `_— issue triage, Oct 2026_`.

### A good enriched body

See `docs/superpowers/triage/enrich/3039-body.md`. It merges four issues and seven tickets in the
Summary, gives the failing call chain under Steps to reproduce (honest: "not reproduced locally
yet"), names the exact functions and two likely root causes under Proposed change, notes the
second caller that the fix also protects (document sync), and has one real open question. Other
good ones: `enrich/3135-body.md` (crash with fixed and unfixed entry points) and
`enrich/3079-body.md` (two crash paths, one of them already mitigated).

### Common mistakes caught in calibration

- Claiming `done` from a commit on dev without checking it is released. Use `In review` instead.
- Getting a function's owner wrong in an enriched body: `determineFileType` is `CommonUtils`, not
  `BackupControl`. Grep every symbol you name.
- Taking the area from the snapshot as a given. Read what the issue is actually about: "sync" can
  mean window sync.
- Closing a crash as fixed because one code path now catches it. Check every entry point; the
  welcome-screen restore had no `try`.
- Spending time investigating an old bug the age policy would close anyway.
- Staging after changing decisions the maintainer has not seen. Any change after a review round
  (new verdicts, new comment texts, priority changes) needs another review round: a table of the
  changes and the new comment texts, then approval, then staging.

### How to check "done" in this codebase

- Code: `app/src/main/java/net/bible/...`. Sync is in `service/cloudsync/` (adapters `googledrive`,
  `nextcloud`, `webdav`; document sync in `documents/`). Backup is
  `android/control/backup/BackupControl.kt` and `service/backup/BackupServiceImpl.kt`. Migrations
  are in `android/database/migrations/`.
- History: `git log -S'<symbol>' --format='%h %ad %s' --date=short -- app/src/main/java | tail -1`
  finds when a symbol was introduced. `git log --grep='#NNN'` finds commits that name the issue
  (fix commits start with `Fixes #NNN`).
- Released or not: the `merge-base` and `tag --contains` commands under `done` above.
- User docs (`website/content/en/docs/*.md`, live at `https://andbible.org/docs/<page>/`) give a
  link for the closing comment. A docs page for a dev-only feature is not live yet.
- Closed issues: grep `snapshot/closed-index.tsv` for the exception or feature name. A crash
  closed as fixed that is reported again is a recurrence: keep it and link the old issues
  (#3204 → #3029, #3075).
