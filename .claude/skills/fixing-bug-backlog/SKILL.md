---
name: fixing-bug-backlog
description: Use when the maintainer wants a batch of AndBible GitHub bugs fixed in parallel by JailBee child containers from an orchestrator container — "käy backlogia läpi ja korjaa selkeät bugit", "jaa tiketit konteille", "fix the clear bugs in the backlog", "work through the bug backlog with child containers". Needs an orchestration grant (~/.jailbee/orchestration/grant.json).
---

# Fixing the bug backlog with child containers

The orchestrator picks clear bugs, gives each one to its own child container, reviews and merges
what comes back, keeps the containers busy, and stages a comment for every fixed issue. One child =
one issue. Children do the debugging and TDD; the orchestrator owns review, merge, gates and GitHub.

**REQUIRED SUB-SKILLS:** jailbee-orchestration for everything about children (grant, spawn,
inbox, done, import, destroy); jailbee-issue-management for the outbox manifest format;
issue-management for tracker rules. This skill only adds the AndBible-specific choices.

## 1. Pick candidates

1. Open bugs with board fields: `docs/superpowers/triage/snapshot/index.tsv` + `board.json`
   (`Type: Bug`, not Done). Re-check live state with `gh issue view <n>`.
2. Drop issues the branch already touches: `git log --oneline -E --grep="#<n>\b"`.
3. Keep a bug only when it is **self-contained**: concrete repro or stack trace, an identifiable
   code area, and a test that can show it red. Skip design debates (e.g. "Psalm vs Psalms"),
   feature requests disguised as bugs, and "maybe just my phone" reports without repro steps.
   A device/layout bug with concrete repro steps is in: give it to Opus (step 3), which can
   reproduce it on the emulator.
4. Stop when no clear bug is left; say which ones were skipped and why.

## 2. Prepare shared material

Children cannot read osTicket or (in strict egress) every download site. Put everything under a
shared batch folder, e.g. `.local/bug-batch-<date>/`:

- `issues/<n>.md`: `gh issue view <n> --json number,title,body,comments` rendered to Markdown.
- Modules the bug needs (e.g. a CrossWire zip). Tell the child to unzip into a temp SWORD dir,
  never `~/.sword`.
- **osTicket evidence, gathered by the orchestrator.** Children have no osTicket access, so for
  every candidate that links a ticket (`support.andbible.org/scp/tickets.php?id=<id>` in the body or
  comments):
  1. `ostickethelper --config ~/.osticket/osticket-config.yaml read <id>` (read-only; the
     credentials are an optional mount, see the user's ostickethelper rules). It downloads the
     ticket text and attachments into the osTicket inbox (`.local/osticket/inbox/<id>/`).
  2. Read it: the user's description, `logcat.txt.gz` / `last_crash_logcat.txt.gz`,
     screenshots, attached modules or backups. Note what matters (the exception, the app
     version, the steps) — this often decides whether the bug is clear enough to spawn.
  3. Copy the relevant files to `$BATCH/osticket/<n>/ticket-<id>/` (gunzip logcats; keep
     screenshots and attachments as they are). Never copy the credentials.
  4. In the spawn context, name these paths and say what to look for in them (e.g. "logcat
     line 1234: the SQLiteException; the reporter's module is in gbsxmpl.zip").

## 3. Spawn and keep the pipeline full

```bash
BATCH=.local/bug-batch-<date> bash .claude/skills/fixing-bug-backlog/scripts/spawn-fix-child.sh \
  <n> "<one-line task>" "<what the issue shows, where the code likely is>" "<red/green test to add, which tests to run>"
```

- Keep `max_running_children` children busy and **1–2 spawns queued**, no more; spawn the next
  one whenever a child is merged.
- Default runner: `claude-jb` codex profile, tier opus. For a harder bug (unclear cause, layout or
  device behaviour, several modules) use Anthropic Opus: `RUNNER="--agent claude --model opus"
  MODEL_NAME="Claude Opus"`.
- Waiting, reading the inbox, answering questions, statuses and cleanup: follow
  **jailbee-orchestration**; this skill adds nothing to that protocol.

## 4. Review, fix and merge each `done`

Import as **jailbee-orchestration** describes ("When a child says done"). The child's own review
does not count: children may not dispatch subagents and ask the orchestrator to review.

1. **Review with a subagent** (superpowers:requesting-code-review; reviewer model sonnet or opus,
   never fable). Give it the issue file, the child's report and `git diff HEAD...<imported_ref>`,
   not your conclusion. Ask: is the fix at the root cause, are other entry points (a gesture
   path, another caller, other platforms) covered, does a test go red without the fix, any
   regression risk. For a larger or riskier diff, also ask the container's other Claude process
   (different model family) for a read-only review.
2. **Read the production diff yourself** too, and verify every finding against the code
   (superpowers:receiving-code-review) before acting on it.
3. **Fix what the review found:**
   - Small or Minor findings: merge the child's branch, then fix on the orchestrator branch with
     a coder subagent (haiku or sonnet; opus only for hard ones), TDD, its own commit
     (`Refs #<n> (...)`), and run that fix's focused tests yourself.
   - The fix is wrong or misses the root cause: do not merge; spawn a new child for the issue
     (fresh fork, new branch name such as `fix/issue-<n>-2`) with the review findings in its
     context.
4. **Merge** with `--no-ff` and a message naming the issue:
   `Merge fix/issue-<n> (child fix-issue-<n>): <what> (#<n>)`.
5. A child that found "already fixed" may still bring a regression test: merge that, and name the
   earlier fix commit in the issue comment. A child that could not fix it safely: merge nothing,
   report it to the user, no comment.

## 5. Stage a comment per fixed issue

One manifest `~/.jailbee/issue-outbox/NNN-fix-comments.json` with a `comment` action per issue
(regenerate it as fixes land; start a new NNN once the user has applied it). Comment shape:
"Fixed: <what the user sees now>. <one sentence why, if useful>. The fix will be in an upcoming
version." Read the issue title first so the comment matches what was reported. Do not close
issues: `Fixes #<n>` commits close them when they reach the default branch. Tell the user to run
`jb issue apply <container>` on the host.

## 6. Final gate and report

- Once at HEAD after the last merge, chosen from the diff per `docs/superpowers/CLAUDE.md` "Gate
  selection": Vue `test:ci`/`lint`/`type-check` for bibleview-js; shared `jvmTest` + iOS
  compiles; `:app:cleanTestStandardGoogleplayDebugUnitTest :app:verifyRoborazziStandardGoogleplayDebug`
  for anything in `app/` or rendered; `assembleStandardGithubDebug` for Kotlin changes.
- A golden that moves because of a fix: inspect it and re-record in the same batch.
- Report per issue: root cause, fix, confidence (high / medium + why), and what was skipped.
- Clean up: no emulator, no own idle Gradle daemons; remind `jailbee unmount osticket <container>`
  if osTicket was mounted.

## Common mistakes

| Mistake | Fix |
|---|---|
| Spawning a bug that is already fixed on the branch | grep the log for `#<n>` first |
| Queueing many spawns at once | 1–2 queued; later picks benefit from merged fixes |
| Merging on the child's word | subagent review + your own read of the diff, every time |
| Child cannot reach a download/osTicket | put the file in the shared batch folder |
