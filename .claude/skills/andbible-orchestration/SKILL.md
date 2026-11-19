---
name: andbible-orchestration
description: >
  Use when orchestrating AndBible work with JailBee child containers from an orchestrator container:
  spawning, briefing, reviewing, merging and cleaning up children, running a round's final gate.
  Extends jailbee-orchestration with AndBible's own rules; load both. Triggers: "orkestroi",
  "orkestroi kontteja", "jaa kontteihin", "jaa tehtävät kontteihin", "lapsikontti", "käynnistä
  lapsikontti", "mergeä lapset", "orchestrate child containers", "spawn a child", "dispatch to
  containers", "merge the children".
---

# Orchestrating AndBible with JailBee children

**REQUIRED BASE SKILL:** jailbee-orchestration. It owns everything generic (grant, spawn, prompts,
runners, inbox loop, watching, done/import, rework mechanics, cleanup). This skill only adds AndBible
rules; never restate the base here. Bug rounds additionally use `fixing-bug-backlog`.

## Role and delegation

- **Reviews run outside your own context:** review every child's `done` before merging with the
  `superpowers:requesting-code-review` skill, dispatching its reviewer subagent with the issue/spec and the diff
  range, not your conclusion. You read the findings and spot-check one.
  **Reviewer model by difficulty** (always pass `model`, never fable): **opus** for code changes with logic,
  concurrency, data/migrations, cross-platform or security impact, large or many-file diffs, and anything where
  a missed defect is costly; **sonnet** for routine, well-specified diffs (docs, text/narration edits,
  mechanical renames, small scoped fixes). When unsure, opus.
  Why: 2026-11-18, the orchestrator reviewed and fixed #4146 inline and spent its context, so reviews moved to
  subagents (opus for every child). 2026-11-19 the maintainer made the model a judgement call: video round 3 ran
  ~35 opus reviews of mostly narration edits.
- **Review fixes never run in your tree**, not even ones found while resolving a conflicted merge: resolve
  only the conflict in the merge commit, then fix with a fork / worktree subagent (`isolation: "worktree"`,
  no runs needed) or a `fork` child (tests or builds needed). Only tiny one-line edits stay inline.
  Why: 2026-11-13, #4070: a coder subagent kept the tree dirty for its whole run, so the maintainer could
  not merge another branch into it.
- **Keep the orchestrator tree clean** at all times: the maintainer merges other containers' branches into
  it whenever they like.
- **Parallel work next to a running batch:** when asked what can run alongside, offer items whose
  *implementation* can start now (no spec-only items). File overlap is a managed risk, not a veto: name the
  overlapping files and mitigations (move-only commits, smallest unit, second merger resolves) and run it.
  Why: 2026-10-15/16, "maximise work in progress".

## Spawning

- **Briefs are short:** every implementation brief starts with "Load the `andbible-child` skill." and keeps to
  Task / Context / Acceptance (+ runner/egress specifics, the `.local/<topic>/` folder). The skill carries process,
  the child gate tier, commit, evidence, cleanup and `done`-report rules; do not repeat them in briefs.
- **Start from your HEAD:** a `fork` (default), or `--ref <HEAD sha>` (or the commit to rework). Never a
  bare `--new`: it branches from origin's default `current-stable` (classic tree, no shared modules).
  Why: 2026-11-14, three children started on `current-stable`. A child reporting a classic tree gets
  `update --ref <sha>` (have it `git -C docs/superpowers clean -fd` first if a stale untracked tree blocks).
- **Commits outside HEAD** (local branches, other imports): send with spawn/update `--ref`; submodule
  commits do not travel that way, so put a `git bundle` in `.local/` for the child to fetch.
- **Runner:** what the maintainer says at the start of the session (it changes with quota); otherwise omit
  it and let the grant default decide. No standing per-difficulty table; runner notes from earlier sessions
  do not carry over. If `jailbee inbox runners` shows the default as "unknown", ask. Never fable.
- **Egress:** spawn strict; list the hosts the task needs and grant the least (`--egress HOST --for` before
  `--loose`). Never add one-off hosts to `.jailbee/config.yaml` (it holds only what the project always
  needs, and editing it here does not reach children). A step you cannot run for a blocked host goes to a
  short child with the right, not to the maintainer.
- **Evidence folder:** `.local/` is one host directory shared by all containers and survives a child's
  destroy. Name the exact `.local/<topic>/` folder for screenshots, logs and fixtures and ask for the paths
  in `done`; never the child's `/tmp` or scratchpad. `ls` before calling a destroyed child's files lost.
- **Holds on shared resources** (Windows VM token: `windows-vm`; Mac lock: `ios-mac`): keep a list; when the
  holder is merged, done or destroyed, message every waiting child "hold released".

## Watching

- **Quiet notes:** read child notes and routine statuses silently; tell the maintainer only questions,
  blockers, scope changes, parked decisions and `done` results.
- **A dead agent is dead by default** (`blocked`, `inbox status` shows `dead`): handle it as the base skill
  says. Only when the maintainer has said they resumed the child by hand is it alive but unsupervised:
  then do not `restart --agent-only` (a second agent would start); judge it by messages and commits.
  A pane tail with a fresh prompt is a reason to ask, not proof. Why: 2026-11-03, #3932/#3933.

## Decisions

- A product decision a child or review raises never stops the run. Take the recommended default, record it
  in the round's decisions file (`docs/superpowers/status/...-decisions.md` or the batch `decisions.md`)
  marked "default taken, pending maintainer", let the child build it so it is easy to change (no safe
  default: keep the import unmerged or preserved as a local branch), carry on.
  Why: 2026-10-28..11-07, questions asked while the maintainer was away stalled children.
- Mention the number of parked decisions in progress replies (one line); present them as a numbered
  plain-text list with options and a recommendation when asked or when the work is done. No forms unless
  the maintainer explicitly asks for one.
- Only a blocker that makes the whole run pointless justifies an immediate message.
- A live brainstorm with the maintainer has priority: no new spawns, merges or gates in between; children
  keep running and their reports wait for the next stopping point.

## Notifications

- Send `PushNotification` (reaches the maintainer's phone when Remote Control is on) whenever something
  waits for them: a question only they can answer, a Google challenge, a blocker, a `done` to merge or
  review, a newly parked product decision. Always notify when the session's work is finished (round merged,
  final gate run, publish and host reminders ready). One short line naming what is waiting; no routine
  progress.
- Notifying does not make orchestration blocking: questions stay plain text, the inbox waiter stays armed
  and other children keep moving.
- **Never use `AskUserQuestion` while orchestrating** (2026-11-18, maintainer): the dialog blocks the turn, so child
  messages, merges and spawns wait behind it. This overrides the "ask with an AskUserQuestion form" rule in
  `docs/superpowers/CLAUDE.md` and the ostickethelper confirmation rule's form: ask in plain text (numbered, each
  question self-contained) and send a `PushNotification`; act on the maintainer's explicit plain-text answer. A
  confirmation that a rule requires (e.g. every osTicket write) is still required: show the exact actions and texts
  in plain text, then wait for that specific yes before running it, while everything else keeps moving.

## Rework

- Rework after a kept child's `done` on a long task goes to a **fresh session**: `restart --agent-only`
  **without** `--resume`, with a self-contained brief (what was built, where the plan/spec/notes are, what
  to change, acceptance). `--resume last` only for short follow-ups where the conversation is the context
  (an answer to its open question, a mid-task continuation). Why: 2026-11-13, a resumed long context cost
  more per turn and got compacted.
- A fix that is wrong or misses the root cause: do not merge; spawn a new child (fresh fork, branch such as
  `fix/issue-<n>-2`) with the review findings in its brief.
- **Every message you send to a child ends with** "Re-arm your inbox waiter after reading this." A child's waiter
  exits on each message; children (sonnet especially) often answer and go back to work without re-arming it,
  and are unreachable until someone notices. Why: 2026-11-19, video round 3, ep24 twice.
- **A hung child:** first try `restart --agent-only --resume last` (keeps its slot, no queue). Only when the
  broker refuses (non-waiter background processes) `stop` and do an ordinary `restart`; that queues for a slot.
- **Every restart prompt after a `stop` (or any container restart) says so explicitly:** "The container was
  restarted: every background process is gone, including your inbox waiter and any render/build. First re-arm
  your background inbox waiter, then check with `pgrep -fa` and your logs which runs are unfinished, kill
  leftovers, and re-run what is missing as background commands that exit with the work." A resumed session
  remembers its old waiter and jobs as running, so without this it neither listens nor notices dead or
  duplicate runs. Why: 2026-11-19, video round 3: ep20/ep24 restarted with `--resume` never re-armed their
  waiter, and ep20 had two stale `render.py` processes hanging at 0 % CPU.

## Merging

- Merge only after your independent review (`superpowers:requesting-code-review`, reviewer model by difficulty, see Role and delegation); a review the
  child ran itself does not replace it.
- `git merge --no-ff` with a message naming the child and the issue:
  `Merge <branch> (child <short>): <what> (#<n>)`.
- **Submodule gitlink trap:** after a conflicted merge stage only the conflicted paths. `git add <submodule>`
  records the submodule worktree's *current* (old) HEAD and silently reverts the child's bump. Compare
  `git ls-tree HEAD <sub>` with `git ls-tree MERGE_HEAD <sub>` before committing, then
  `git submodule update --checkout <sub>`. Why: 2026-10-30, desktop D2b dropped a jsword bump.
- **Submodule conflicts** (`docs/superpowers`, goldens): resolve inside the submodule (merge or cherry-pick
  the child's submodule commits there, commit), then stage the resulting gitlink explicitly and confirm it
  with `git ls-tree`.
- A green merge can still break tests (removed APIs, unrun test classes): memory
  `parallel-stream-merge-breaks-tests-without-conflict`.

## After each merge: disk hygiene

1. `du -sh .local/*` and `df -h .local`.
2. Delete the merged child's leftovers in `.local/`.
3. Over 85 % full: ask the maintainer, naming candidates and sizes, before deleting anything another live
   container may use. Never delete another live container's files (`.local/mac/<c>`,
   `.local/windows-vm/runs/<c>`) without asking.
4. Other containers' trees on the remote Mac go only via `mac-remote.sh prune ... --yes` with names the
   maintainer gave (dry run first).

## Final gate

Gates are tiered (`docs/superpowers/CLAUDE.md`, "Gate selection"): children run only the short child gate, so the
**batch gate is yours** and is the only place the full `:app` suite runs. Brief children with the child tier, not
"full verify". Run the batch gate once at HEAD after the last merge, **in parallel**:

- Start `:app:verifyRoborazziStandardGoogleplayDebug` (30-50 min, the long pole) first, in this container.
- Run the light non-Gradle steps (Vue `test:ci`/`lint`/`type-check`, `make site site-check`) here at the
  same time.
- Never run a second Gradle build in this checkout while verify runs (same build dirs and locks). Give the
  other Gradle steps (`assembleStandardGithubDebug`, `compileStandardGoogleplayDebugAndroidTestKotlin`, the changed
  shared `jvmTest`s + iOS compiles, desktop tests) to a light child: a `fork` of HEAD with `--autostart-profile none`,
  a bounded command and "report the result, change nothing". The host's cores are shared, so do this when the load
  allows.
- A red only the full run shows: bisect it to the merged child (revert-test or `--tests` the suspects), fix in a new
  child, rerun the batch gate.

## Finishing

- The maintainer usually merges their own branches without a PR: report the branch, HEAD and what to push
  (including `docs/superpowers` / goldens submodule commits) instead of staging `jailbee pr source`, unless
  asked (memory `maintainer-own-work-no-pr-by-default`).
- Notify (see above), then clean up: no emulator, no idle own Gradle daemons, own `.local/` scratch removed.
