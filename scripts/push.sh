#!/usr/bin/env bash
# make push: push the current branch, publishing our own submodules first so no pushed gitlink points at
# an unpublished commit.
#
#   scripts/push.sh [--dry-run] <submodule>...
#
# For each submodule:
#  1. The commit HEAD's gitlink names (not the checkout, which can lag behind) goes to the submodule
#     branch named after the superproject branch, fast-forward only.
#  2. `git push --recurse-submodules=check` inspects the gitlink of EVERY commit being pushed, not just
#     HEAD's. An intermediate gitlink can name a commit that later submodule history left out (a child
#     container's submodule commit that was merged around). Each such commit that no origin branch
#     contains goes to its own branch `gitlink/<sha>`, so the history stays fetchable.
# Finally the superproject branch is pushed with --recurse-submodules=check as the safety net.
# --dry-run prints the pushes instead of running them (needs no network and no key touch).
set -euo pipefail

dry=0
if [ "${1:-}" = --dry-run ]; then dry=1; shift; fi
run() { if [ "$dry" = 1 ]; then echo "[dry-run] $*"; else "$@"; fi; }
die() { echo "push: $*" >&2; exit 1; }

branch=$(git symbolic-ref --short HEAD 2>/dev/null) || die "detached HEAD"
# The superproject commits this push publishes: the ones no origin branch has yet.
mapfile -t new_commits < <(git rev-list HEAD --not --remotes=origin)

for sm in "$@"; do
    if [ ! -e "$sm/.git" ]; then echo "push: $sm not checked out, skipping"; continue; fi
    head_sha=$(git rev-parse "HEAD:$sm")
    [ "$(git -C "$sm" rev-parse HEAD)" = "$head_sha" ] ||
        echo "push: note: $sm checkout differs from the committed gitlink; pushing the gitlink"
    echo "push: $sm ($(git -C "$sm" rev-parse --short "$head_sha") -> $branch)"
    run git -C "$sm" push origin "$head_sha:refs/heads/$branch"

    declare -A seen=()
    for c in "${new_commits[@]}"; do
        sha=$(git rev-parse -q --verify "$c:$sm" 2>/dev/null) || continue
        [ -z "${seen[$sha]:-}" ] || continue
        seen[$sha]=1
        git -C "$sm" cat-file -e "$sha^{commit}" 2>/dev/null ||
            die "$sm: gitlink $sha (superproject $(git rev-parse --short "$c")) is not in the local clone; fetch it first"
        # published by step 1, or already on an origin branch
        git -C "$sm" merge-base --is-ancestor "$sha" "$head_sha" && continue
        [ -z "$(git -C "$sm" for-each-ref --count=1 --contains "$sha" refs/remotes/origin)" ] || continue
        echo "push: $sm: gitlink $(git -C "$sm" rev-parse --short "$sha") of $(git log -1 --format='%h %s' "$c")"
        echo "push:   is on no origin branch; publishing it as gitlink/$sha"
        run git -C "$sm" push origin "$sha:refs/heads/gitlink/$sha"
    done
    unset seen
done

echo "push: and-bible ($branch)"
run git push --recurse-submodules=check -u origin "$branch"
