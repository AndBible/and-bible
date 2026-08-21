---
name: osticket
description: >
  Fetch and act on AndBible osTicket staff tickets via ostickethelper
  (list, read, resolve). Use when the user runs /osticket, asks to list
  open/closed tickets, read ticket id=N, fetch an osTicket, or post/close
  a ticket in osTicket. Not for Play Store, email, or pasted reports —
  those use the support skill. Pasted tickets still use /support.
---

Staff osTicket I/O for AndBible. Drafting and triage live in the **support** skill — do not copy those rules here.

## Gate

Before any osTicket command, run the wrapper (it exits 2 if local env is missing):

`.agents/skills/osticket/scripts/osticket.sh`

Env file (gitignored, not in this skill): `<repo>/ai-local/osticket.env`

If the wrapper reports a missing env file, missing `OSTICKETHELPER_DIR` / `OSTICKET_CONFIG`, or a bad path: **stop**. Tell the operator to copy `.agents/skills/osticket/env.example` to `ai-local/osticket.env` and point those two variables at their [ostickethelper](https://github.com/Sykero-Software/ostickethelper) checkout and helper YAML. Use `/support` with a paste until that file exists. Do not invent credentials, ticket bodies, or staff ids.

Playwright is slow. Use a long command timeout (at least 2 minutes per ticket).

## Staff id

CLI arguments are the staff URL id (`id=3321` in `tickets.php?id=3321`), never the public `Ticket #…`. If only a Ticket # is given, stop and ask for `id=`. Full two-number rule: support skill.

## Commands

Always go through the wrapper. Prefer `--no-pdf` on read (agent uses `ticket.json` + attachments, not receipts).

```bash
.agents/skills/osticket/scripts/osticket.sh list
.agents/skills/osticket/scripts/osticket.sh list --status closed
.agents/skills/osticket/scripts/osticket.sh read 3321 --no-pdf
.agents/skills/osticket/scripts/osticket.sh resolve 3321 --message "…"
```

- `/osticket` with no id → `list` (open).
- `/osticket 3321` or “read id=3321” → `read <id> --no-pdf`.
- List output uses `[id=NNNN]` — that NNNN is the CLI id. List is grouped by sender.

After `read`, open `ticket.json` and downloaded files from the CLI output (inbox is whatever the helper YAML set). Then apply the **support** skill to that content as if it were pasted. Pass `id=<staff id>` through so support URLs are correct.

## Same user — read the set before deciding

Do **not** draft, recommend send/close, or treat a ticket as standalone while that sender still has other **open** tickets on the list.

1. From the list group (or `list --user "Name"`), collect **every open id** for that person.
2. `read` them **all** (`--no-pdf`) before any recommendation. Closed tickets from the same user only if the open set is still ambiguous.
3. Then decide **one plan for the person**, not per id in isolation:
   - Same issue → **one reply**. Post on the newest id that has the best user text; close the rest as the same (same message or a one-liner pointing at that reply).
   - Different issues → separate replies, **or** one combined reply if that is kinder (one inbox, one person).
4. Show the operator the set (ids, dates, one-line each) and the proposed split/combine **before** they send.

## Resolve (destructive)

`resolve` posts the message **and** sets status to Resolved. There is no reply-without-close.

Run `resolve` only when the operator explicitly says to send/post/close (e.g. “send it”, “close with that”). Never resolve in the same turn as the first draft unless they asked to send. Confirm the staff id and the exact message.

If they want a reply left open: say the CLI cannot do that; they must paste in SCP.

## Do not

- Call the CLI when the user pasted a ticket, Play review, or email — that is `/support` only.
- Pass a public Ticket # to `read` / `resolve`.
- Store or print SCP passwords. They belong in the helper YAML / secrets file / `OSTICKET_PASSWORD` in `ai-local/osticket.env`.
