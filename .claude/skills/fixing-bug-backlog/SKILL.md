---
name: fixing-bug-backlog
description: Use when the maintainer wants a batch of AndBible GitHub bugs fixed in parallel by JailBee child containers from an orchestrator container — "käy backlogia läpi ja korjaa selkeät bugit", "jaa tiketit konteille", "fix the clear bugs in the backlog", "work through the bug backlog with child containers". Needs an orchestration grant (`jailbee inbox grant`).
---

# Fixing the bug backlog with child containers

The orchestrator picks clear bugs, gives each one to its own child container, reviews and merges
what comes back, keeps the containers busy, and stages a comment for every fixed issue. One child =
one issue. Children do the debugging and TDD; the orchestrator owns review, merge, gates and GitHub.

**REQUIRED SUB-SKILLS:** jailbee-orchestration for everything about children (grant, spawn,
inbox, done, import, destroy); jailbee-issue-management for staging issue actions (`jailbee issue ...`);
issue-management for tracker rules. This skill only adds the AndBible-specific choices.

## 0. Orchestrator role

Follow **jailbee-orchestration** ("Containers are not subagents"): investigation, reviews and larger
reading/writing go to subagents or a fork; debugging that runs programs goes to a child.

## 1. Pick candidates

1. Open bugs with board fields: `docs/superpowers/triage/snapshot/index.tsv` + `board.json`
   (`Type: Bug`, not Done). Re-check live state with `gh issue view <n>`.
2. Drop issues an earlier round already handled. Fixed issues stay open until they reach the
   default branch, so check all three: `git log --all --oneline --grep="<n>"` (a fix commit
   may not name the issue with `#`), the `docs/superpowers` log, and the issue comments
   (`gh issue view <n> --json comments`: a "Fixed: ..." / "already fixed" comment or a
   `_— bug-fix round_` signature means it is done).
3. Keep a bug only when it is **self-contained**: concrete repro or stack trace, an identifiable
   code area, and a test that can show it red. Skip design debates (e.g. "Psalm vs Psalms"),
   feature requests disguised as bugs, and "maybe just my phone" reports without repro steps.
   A device/layout bug with concrete repro steps is in: give it to Opus (step 3), which can
   reproduce it on the emulator.
4. A bug whose evidence nobody here can reach (a video or attachment egress blocks, a device-only
   state, an answer only the reporter has) is out of the round: leave it waiting. If a child hits
   such a blocker mid-work, destroy it unchanged and take the next bug.
5. Stop when no clear bug is left; say which ones were skipped and why.

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
- Runner: the one the maintainer names at the start of the session (it changes with quota); runner notes from earlier
  sessions do not carry over. Check `jailbee inbox runners` for what is available. The script's default is the
  `claude-jb` codex profile, tier opus; override with e.g. `RUNNER="--agent claude --model opus" MODEL_NAME="Claude Opus"`.
- Waiting, reading the inbox, watching quiet or dead children, answering questions, statuses and cleanup:
  follow **jailbee-orchestration** as is.
- The spawn prompt tells each child to commit early (WIP commits are fine), so a dead agent loses little.

## Decisions: park them, never block the run on the user

The run is autonomous and may go on overnight. **jailbee-orchestration's "Never block on the user"
applies as is:** no question dialogs while orchestrating. For a decision a child or a review raises (a
design choice, a product question, "merge or rework?"):

- **Park it.** Write the question, the options and your recommendation into `$BATCH/decisions.md`. Take
  the recommended default when there is a safe one ("default taken, pending maintainer"); otherwise keep
  the work safe (leave the import unmerged, or preserve it as a local branch, e.g. `wip/issue-<n>-v1`).
  Then carry on with everything else.
- **Remind, don't ask.** In your normal replies, say how many decisions are parked (one line). Present
  them as a numbered plain-text list when the user asks for them or the round is done.
- **Brainstorming with the user has priority:** while it is under way, do not start new work (spawns,
  merges, gates) in between; children keep running, and their reports wait for the next stopping point.
- A blocker that makes the whole run pointless (no egress, the grant revoked, the disk full): report it
  plainly and stop cleanly.
- A child's own `question` is answered by you from the issue, the code and the spec; forward it to the
  decision list only if it truly needs the user, and give the child a safe interim instruction (skip that
  part, take the conservative option) so it is not left waiting.

Memory `orchestrator-defer-decisions-to-end` has the history.

## 4. Review, fix and merge each `done`

Import as **jailbee-orchestration** describes ("When a child says done"). Merge only after the
orchestrator's own independent review below; a review the child ran itself does not replace it.

1. **Review with a subagent** (superpowers:requesting-code-review; reviewer model sonnet or opus,
   never fable). Give it the issue file, the child's report and `git diff HEAD...<imported_ref>`,
   not your conclusion. Ask: is the fix at the root cause and in the layer that owns the
   behaviour (bibleview-js vs Kotlin `:app` vs the shared modules), or is it a narrow ad hoc
   patch for the reported symptom; are other entry points (a gesture path, another caller, other
   platforms) covered; does a test go red without the fix; any regression risk.
   Reviews are read-only; several at once may each read the import in their own git worktree
   (`isolation: "worktree"` on the Agent call), as jailbee-orchestration allows. A finding that needs
   a test run to confirm goes to a child, not to a reviewer here. For a larger or riskier diff, also
   ask the container's other Claude process (different model family) for a read-only review.
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

## 5. Stage a comment and a close per fixed issue

Stage with the `jailbee issue` commands into one draft batch per round (never write
`~/.jailbee/issue-outbox/` files by hand; the commands capture the expected state themselves):

```bash
jailbee issue comment <n> --body-file /tmp/<n>-comment.md --batch fix-comments-<date>
jailbee issue state <n> closed --reason completed --batch fix-comments-<date>
```

Add each fixed issue to the batch as its fix lands; `jailbee outbox show issue/fix-comments-<date>`
shows the draft. Comment shape:
"Fixed: <what the user sees now>. <one sentence why, if useful>. The fix will be in an upcoming
version." End every comment with a signature as its final line, separated by a blank line:
`_— bug-fix round, <Mon YYYY>_` (e.g. `_— bug-fix round, Oct 2026_`), so the maintainer's own
comments are never confused with session-written ones (same idea as the triage marker in
issue-management). Read the issue title first so the comment matches what was reported. Close each fixed
issue in the same batch, right after its comment (`state <n> closed --reason completed`): the
maintainer wants fixed issues closed when the fix is merged here, not only when the `Fixes #<n>`
commit reaches the default branch. An issue found already fixed is closed too; one the round did not
fix (nothing merged, or not reproducible) is not. Keep body files outside the work tree (`/tmp`), so
the tree stays clean for fork spawns.

When the round is done, `jailbee outbox submit fix-comments-<date> --kind issue`. If `jailbee inbox grant`
shows `publish issues` covering the actions, publish with `jailbee inbox send publish --issue <file> --wait`
and read the final status; otherwise tell the user to run `jb issue apply <container>` (or
`jb outbox <container>` to inspect) on the host. Relay a child's GitHub requests as **jailbee-orchestration** says.

## 6. Final gate and report

- Once at HEAD after the last merge, chosen from the diff per `docs/superpowers/CLAUDE.md` "Gate
  selection": Vue `test:ci`/`lint`/`type-check` for bibleview-js; shared `jvmTest` + iOS
  compiles; `:app:cleanTestStandardGoogleplayDebugUnitTest :app:verifyRoborazziStandardGoogleplayDebug`
  for anything in `app/` or rendered; `assembleStandardGithubDebug` for Kotlin changes.
- **Run the gate in parallel, not as one sequential script.** Verify (45-80 min) is the long pole;
  nothing should queue behind it:
  - Start verify first, in this container.
  - Run the light, non-Gradle steps (Vue gate, `make site site-check`) here at the same time; they
    do not compete with Gradle.
  - Never run a second Gradle build in this checkout while verify runs: it contends for the same
    build directories and locks. Give the other Gradle steps (`assembleStandardGithubDebug`, and
    the shared `jvmTest` + iOS compiles or desktop tests when they are not done yet) to a light
    child: a `fork` of HEAD (warm build outputs; the APK took about 2 min on 2026-11-06), spawned
    with `--autostart-profile none`, with a bounded command and "report the result, change nothing".
  - The host's cores are shared by all containers: parallel Gradle runs slow each other, so do
    this when the load allows; the light steps are always worth it.
- A golden that moves because of a fix: inspect it and re-record in the same batch.
- Report per issue: root cause, fix, confidence (high / medium + why), and what was skipped.
- Clean up: no emulator, no own idle Gradle daemons; remind `jailbee unmount osticket <container>`
  if osTicket was mounted.

## Common mistakes

| Mistake | Fix |
|---|---|
| Spawning a bug that is already fixed on the branch | grep the log for `#<n>` first |
| Queueing many spawns at once | 1–2 queued; later picks benefit from merged fixes |
| A question dialog mid-run (`AskUserQuestion` at night) | park it in `decisions.md`, remind, list it when asked |
| Merging on the child's word | subagent review + your own read of the diff, every time |
| Child cannot reach a download/osTicket | put the file in the shared batch folder |
