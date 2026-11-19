---
name: andbible-child
description: >
  Use when you are an AndBible JailBee child container (`JAILBEE_ROLE=child`, or your first prompt says
  you are a child or tells you to load this skill) and the task is implementation, a bug fix or other
  committed work in the and-bible repository. Triggers: "Load the `andbible-child` skill", "olet
  lapsikontti", "you are a child container", "report done to the orchestrator".
---

# Working as an AndBible child

**REQUIRED BASE SKILL:** jailbee-orchestration-child (inbox, `question`/`blocked`/`done`, waiter, clean tree).
This skill adds only AndBible's rules for an implementing child. The brief gives Task / Context / Acceptance
(plus runner or egress specifics); everything below applies unless the brief says otherwise. The standing rules
in `CLAUDE.md`, `docs/superpowers/CLAUDE.md` and `.jailbee/AGENT-GUIDE.md` still apply; sections are named here,
not repeated.

## Process

- Superpowers chain as in `docs/superpowers/CLAUDE.md`, "Process". A brief that names an approved spec or plan
  means: implement it; no new brainstorm/spec/plan cycle.
- Bug: `superpowers:systematic-debugging`, then a test that reproduces it, seen red before the fix.
- Subagents: coders `sonnet` or `haiku`, reviewers `sonnet` or `opus`, never `fable`; always pass `model`.
  Tell them to report to you, not to the orchestrator.
- Before `done`: one whole-branch review (`superpowers:requesting-code-review`, diff `<BASE>..HEAD`). Fix
  Critical/Important findings; resolve Minor findings yourself without asking.

## Gate

- Run the **child `done` gate** tier of `docs/superpowers/CLAUDE.md`, "Gate selection": focused tests, touched
  `:app` classes via `--tests`, changed shared module `jvmTest` + one iOS target, scoped Roborazzi when an image can
  move. No full `:app` suite and no full verify unless the brief asks; the orchestrator runs the batch gate.
- Run it once, at HEAD, after the last code change. Docs/status commits after it do not invalidate it.
- Before any `:app` test run: `.jailbee/AGENT-GUIDE.md`, "Test Sword modules".

## After a restart or resume

- A `Stop` hook (`.jailbee/child-waiter-stop-hook.sh`, temporary until JailBee does it) refuses to end your turn
  while no `jailbee inbox wait` runs. When it fires, re-arm the waiter as a background command; do not argue with it.
  Right after an accepted `done` in a container being destroyed, one line saying so and stopping is fine.
- If your prompt says the container was restarted, or you resume a session and find background tasks missing,
  assume **no background process survived**: your inbox waiter and every render, build or capture are gone.
- First re-arm the background inbox waiter (as the child preamble describes). Then `pgrep -fa` and read your
  logs: kill leftovers and duplicates from before the restart, and re-run unfinished work as background commands
  that exit with the work. Never assume a run you started before the restart is still going.
  Why: 2026-11-19, two restarted video children never re-armed their waiter, and one had two stale render
  processes hanging.

## Waiting on long runs

- Run a long command (render, Gradle, capture, TTS) as a **background command that ends when the work ends**
  (`run_in_background`), and wait for its completion notification. Wrap it so it always exits, also on failure
  (`cmd > log 2>&1; echo "exit=$?" >> log`).
- Never wait with `tail -f`, an `until [ -s file ]` / `sleep` poll loop or a `Monitor` on a log pattern. If the run
  dies, writes elsewhere or never prints the pattern, the wait never ends and the agent hangs for hours: it cannot
  read orchestrator messages either.
  Why: 2026-11-19, video round 3: three children (ep14, ep20, ep24) hung 1.5 h each this way and had to be stopped
  and restarted.

## Decisions and questions

- A product or UX choice the brief leaves open: take the sensible default, build it so it is easy to change, list
  it under "Decisions" in `done`. Send `question` only when you truly cannot continue: the maintainer may be away
  for hours and a question stalls you.

## Commits

- Small commits; each message names the model, e.g. `(Claude Sonnet)`. GitHub issue fix:
  `Fixes #NNN (short description)` as the first line. Never amend, force or push.
- Submodules (`app/src/test/roborazzi` goldens, `docs/superpowers`): commit inside the submodule first, then the
  gitlink bump in and-bible right after. An uncommitted pointer does not reach the host.
- Goldens: a moved golden is re-recorded in the same branch, inspected zoomed, and explained in `done`; a new
  screen or view gets new goldens (`docs/superpowers/CLAUDE.md`, "Goldens").
- New user-facing strings in English only: `strings.xml` + `Strings.kt` + `AndroidStrings.kt` (public `CLAUDE.md`,
  "Kotlin Multiplatform / Compose structure"), Vue strings in `default.yaml`. Translations only when the brief
  asks (`update-translations`).
- A user-visible change updates `website/content/en/docs/` in the same branch (`update-user-docs`).

## Evidence and cleanup

- Evidence that matters (screenshots, logs a reviewer needs): a committed status note under
  `docs/superpowers/status/<topic>/` with its images. Scratch goes in the `.local/<topic>/` folder the brief names,
  never in `/tmp` or the scratchpad when the orchestrator needs to see it.
- Before `done` (`docs/superpowers/CLAUDE.md`, "Disk hygiene in `.local/`"): delete your own `.local` scratch,
  run `scripts/mac-remote.sh cleanup` if you used the Mac, stop your emulator, Windows VM, Linux desktop session
  and Gradle daemons (`./gradlew --stop`), release held tokens. Name what you kept and why.
- GitHub writes (issue comments, labels, PR text): never stage them yourself; send the exact content to the
  orchestrator in a message or in `done`.

## `done` report

Send with `--wait` (base skill). Use these headings; write "none" rather than dropping one.

```text
Branch / HEAD: <branch> <sha>
Commits: <sha> <subject>   (one line each)
Root cause and fix: <bugs only; what was wrong, where, how fixed>
Tests added: <test names; red-first evidence: the failing assertion before the fix>
Goldens: <moved (why, inspected) / added / none>
Gate: <set + one-line reason from the diff paths>; <tests / failures / skips per task>; <durations>
Decisions (default taken, pending maintainer): <numbered, with the alternative>
Evidence: <status note and .local paths>
Cleanup: <what was stopped and deleted; what was kept and why>
Maintainer action: <pushes of submodule commits, host commands, GitHub writes to stage; or none>
```

## Common mistakes

| Mistake | Instead |
|---|---|
| Running the full `:app` verify "to be safe" | Child tier; say in `done` if you suspect the full suite or a golden |
| `question` for a UX choice | Default + "Decisions" in `done` |
| Gitlink bump without the submodule commit (or the reverse) | Submodule commit, then bump, back to back |
| Evidence only in `/tmp` | Status note in `docs/superpowers/status/` or the named `.local/<topic>/` |
| Waiting on a render/build with `tail -f`, a poll loop or `Monitor` on a log line | Background command that exits with the work; wait for its notification |
| Gate numbers from a scoped rerun | `rm -rf build/test-results/<task>/` after scoped runs; report the final run |
