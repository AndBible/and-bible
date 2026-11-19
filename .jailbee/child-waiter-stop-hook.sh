#!/usr/bin/env bash
# Claude Code Stop hook (wired in .claude/settings.json). Temporary stop-gap until JailBee
# ships VRTFinland/jailbee#48 (waiter kept armed by JailBee); then delete this file, its test,
# the Stop entry in .claude/settings.json and the .gitignore exception (memory
# jailbee-child-waiter-hook-temporary). Until then JailBee does not
# re-arm a child's inbox waiter itself.
#
# In a JailBee child (JAILBEE_ROLE=child) the agent must keep `jailbee inbox wait` running in
# the background, or orchestrator messages never reach it. Agents forget to re-arm it after a
# wake or a restart, so this hook refuses to end the turn while no waiter process exists.
# Outside a child it does nothing.
#
# Loop guard: when the hook already blocked once in this stop chain (stop_hook_active), it lets
# the turn end, so a broker that cannot be reached never traps the agent.
set -u

[ "${JAILBEE_ROLE:-}" = "child" ] || exit 0

input=$(cat)
if [ "$(printf '%s' "$input" | jq -r '.stop_hook_active // false' 2>/dev/null)" = "true" ]; then
    exit 0
fi

# A real waiter is the jailbee process itself; shells whose command text merely mentions
# "inbox wait" (the Bash tool wrapper, grep) do not count.
if ps -eo comm=,args= | awk '$1 !~ /^(bash|sh|zsh|dash|grep|awk|pgrep)$/ && /inbox wait/ {found=1} END {exit !found}'; then
    exit 0
fi

jq -n '{decision: "block", reason: "JailBee child: no `jailbee inbox wait` process is running, so orchestrator messages cannot reach you. Re-arm it now as a background command (the waiter command from your jailbee-orchestration-child skill), then end your turn. If you just sent `done` and the broker is destroying this container, say so in one line and stop."}'
