#!/usr/bin/env bash
# Tests scripts/push.sh (make push) against throwaway local bare repositories: a submodule commit that
# an intermediate superproject commit names, but that later submodule history no longer contains, must
# be published so `git push --recurse-submodules=check` accepts the superproject push.
set -euo pipefail
push=$(cd "$(dirname "$0")" && pwd)/push.sh
t=$(mktemp -d); trap 'rm -rf "$t"' EXIT
fail() { echo "FAIL: $*"; exit 1; }
g() { git -c protocol.file.allow=always -c user.name=t -c user.email=t@t -c init.defaultBranch=main "$@"; }
export GIT_CONFIG_GLOBAL=/dev/null GIT_CONFIG_NOSYSTEM=1

setup() {
    rm -rf "$t"/*
    g init -q --bare "$t/sub.git"; g init -q --bare "$t/super.git"
    g clone -q "$t/sub.git" "$t/sub" 2>/dev/null
    g -C "$t/sub" commit -q --allow-empty -m A; g -C "$t/sub" push -q origin main
    g clone -q "$t/super.git" "$t/super" 2>/dev/null
    g -C "$t/super" commit -q --allow-empty -m root
    g -C "$t/super" submodule add -q "$t/sub.git" sub
    g -C "$t/super" commit -q -m "add sub"; g -C "$t/super" push -q origin main
    s=$t/super/sub
    # orphan: B on a side line, named by one superproject commit, then abandoned
    g -C "$s" checkout -q -b side; g -C "$s" commit -q --allow-empty -m B; B=$(g -C "$s" rev-parse HEAD)
    g -C "$t/super" commit -q -am "bump sub to B"
    g -C "$s" checkout -q main; g -C "$s" commit -q --allow-empty -m C; C=$(g -C "$s" rev-parse HEAD)
    g -C "$t/super" commit -q -am "bump sub to C"
}

# 1. Without the fix, the plain check refuses: the scenario reproduces the bug.
setup
g -C "$s" push -q origin "$C:refs/heads/main"
g -C "$t/super" push -q --recurse-submodules=check origin main 2>/dev/null && fail "scenario does not reproduce: check accepted"

# 2. --dry-run pushes nothing and names exactly the HEAD gitlink and the orphan.
setup
out=$(cd "$t/super" && "$push" --dry-run sub 2>&1) || fail "dry run failed: $out"
grep -q "\[dry-run\] git -C sub push origin $C:refs/heads/main" <<<"$out" || fail "dry run: no HEAD gitlink push: $out"
grep -q "\[dry-run\] git -C sub push origin $B:refs/heads/gitlink/$B" <<<"$out" || fail "dry run: no orphan push: $out"
[ "$(grep -c '\[dry-run\] git -C sub push' <<<"$out")" = 2 ] || fail "dry run: unexpected submodule pushes: $out"
[ "$(g -C "$t/sub.git" for-each-ref --format='%(refname)')" = refs/heads/main ] || fail "dry run created submodule branches"
[ "$(g -C "$t/sub.git" rev-parse main)" != "$C" ] || fail "dry run pushed the submodule"
[ "$(g -C "$t/super.git" rev-parse main)" != "$(g -C "$t/super" rev-parse HEAD)" ] || fail "dry run pushed the superproject"

# 3. The real run publishes the orphan and the superproject push goes through.
setup
out=$(cd "$t/super" && "$push" sub 2>&1) || fail "push failed: $out"
[ "$(g -C "$t/sub.git" rev-parse main)" = "$C" ] || fail "sub main not at C"
[ "$(g -C "$t/sub.git" rev-parse "gitlink/$B")" = "$B" ] || fail "orphan B not published"
[ "$(g -C "$t/super.git" rev-parse main)" = "$(g -C "$t/super" rev-parse HEAD)" ] || fail "superproject not pushed"

# 4. Gitlinks already reachable from the HEAD gitlink get no extra branch.
setup
g -C "$s" checkout -q main; g -C "$s" merge -q --no-edit side; g -C "$t/super" commit -q -am "bump sub to merge"
out=$(cd "$t/super" && "$push" sub 2>&1) || fail "push failed: $out"
[ -z "$(g -C "$t/sub.git" for-each-ref refs/heads/gitlink)" ] || fail "ancestor got a gitlink branch"

echo "PUSH TESTS OK"
