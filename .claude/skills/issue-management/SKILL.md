---
name: issue-management
description: Use for AndBible issue and project-board work — triaging new issues or PRs, closing or relabelling issues, enriching an issue description, choosing what to work on next, moving board items, sponsorship labels, or checking the tracker for drift. Triggers: "triage issues", "käy läpi uudet tiketit", "mitä seuraavaksi", "päivitä board", "rikasta tiketti", "sulje duplikaatit", "issue triage", "pick next work", "board drift".
---

# AndBible issue and board management

Maintainer-only: if `docs/superpowers/tools/triage/` does not exist, stop and say so.

The tracker is `AndBible/and-bible`; the board is org project #5 ("Tuomas' project board"). Design:
`docs/superpowers/specs/2026-10-10-issue-triage-and-project-board-design.md`. Tools:
`docs/superpowers/tools/triage/` (operation reference: `batch_schema.md`). Data: `docs/superpowers/triage/`.

## Hard rules

- The container never writes to GitHub. Every change is a batch file under `triage/batches/`, committed for the maintainer to review and apply on the host. The host dry-runs with `python3 docs/superpowers/tools/triage/apply.py docs/superpowers/triage/batches/<file>.json`, then may apply with the same command plus `--apply`, then commits `triage/applied/` and pulls it back. These commands do not authorize running them in the container.
- Never write hour estimates. Suggest `Sponsor me!` + `Estimate needed`; the maintainer estimates.
- Priority lives only on the board (Priority field). Never add `Prio:` labels.
- Labels must be in `triage/taxonomy.yaml`. Never delete a label named by any file under `.github/`.
- GitHub text is English, short and specific; comments end with `_— issue triage, Oct 2026_` as the final line.
- Board migration is BLOCKED. Do not run `smoke_board.py` or any migration until there is a separately supported, host-validated strategy for preserving selected values during option replacement. The current GraphQL option input has no `id`; the current smoke test is expected to fail at widening, and that failure does not mean the migration is ready. See the "Unsupported populated-field replacement" section in `docs/superpowers/tools/triage/batch_schema.md` for the implemented guard and operator constraints.
- The board export needs the Projects permission. If the container token lacks it, ask the maintainer to run `python3 docs/superpowers/tools/triage/github.py board-export > docs/superpowers/triage/snapshot/board.json` on the host.

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
- Container tests use fakes and cannot establish that the real GitHub API preserves board selections. Do not interpret a green test suite or the current expected smoke failure as migration approval.
