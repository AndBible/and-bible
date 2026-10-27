#!/bin/bash
# Spawn one JailBee child container that fixes one GitHub issue.
# usage: bash spawn-fix-child.sh <issue> "<one-line task>" "<issue-specific context>" "<acceptance tests>"
# env:   BATCH   shared folder under .local/ (default .local/bug-batch), must hold issues/<n>.md
#        RUNNER  runner flags (default: --agent claude-jb --profile codex --model opus)
#        BRANCH  branch name (default fix/issue-<n>; use fix/issue-<n>-2 for a rework)
#        MODEL_NAME  model named in the commit trailer (default: jb.codex.capable via claude-jb)
set -euo pipefail
n=$1; task=$2; extra=$3; tests=$4
BATCH=${BATCH:-.local/bug-batch}
test -f "$BATCH/issues/$n.md" || { echo "missing $BATCH/issues/$n.md" >&2; exit 2; }
# shellcheck disable=SC2086
jailbee-inbox send spawn --branch "${BRANCH:-fix/issue-$n}" --note "GitHub #$n" ${RUNNER:---agent claude-jb --profile codex --model opus} --wait - <<EOF
Task: $task (GitHub issue #$n, AndBible/and-bible).

Context:
- The full issue text with comments is in the shared folder: $BATCH/issues/$n.md (read it first; .local/ is shared with the host and gitignored).
- Shared helper files (SWORD module zips, osTicket attachments, logs) are under $BATCH/.
- $extra
- Read CLAUDE.md and .jailbee/AGENT-GUIDE.md before builds/tests. Judge against this branch, not current-stable.
- Before any :app test run: scripts/check-test-sword-modules.sh; if modules are missing, extract .local/testmods.zip into ~/.sword. Never add other modules to ~/.sword.
- Work: superpowers systematic-debugging -> test-driven-development. Find the root cause first, verify it in the code at HEAD. Write a test that fails before the fix (red), then fix (green). If the bug is already fixed at HEAD or cannot be fixed safely, say so with evidence instead of forcing a change.
- Keep the change minimal and focused on this issue; no unrelated refactoring. English strings only. If user-visible behaviour changes, update the matching website/content/en/docs page.
- Do not re-record Roborazzi goldens; if your change could move a golden, say so in the report (the orchestrator runs the full verify gate).
- Do not run the full :app verify gate; run only the tests relevant to your change.

Acceptance: $tests

Commit: small commits on your branch. The fix commit message starts with "Fixes #$n (short description)", then details, and a line "Implemented by: ${MODEL_NAME:-jb.codex.capable via claude-jb}". No Claude session info in commit messages. Do not push.

Report: send \`done\` with a short summary: root cause, fix, tests added and their red/green evidence, tests run and results, your confidence (high/medium/low) that this fixes the reported bug, and any risk or follow-up. Send \`question\` if you are stuck.
Commit everything before you send \`done\`.
EOF
