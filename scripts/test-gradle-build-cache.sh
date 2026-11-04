#!/usr/bin/env bash
# Exercise the real build policy, including the separate kdom included build.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
LOGS="$(mktemp -d)"
trap 'result=$?; if (( result == 0 )); then rm -rf "$LOGS"; else printf "Logs retained: %s\n" "$LOGS"; fi' EXIT
cd "$ROOT"

./gradlew -I scripts/tests/gradle-cache-policy.init.gradle checkBuildCachePolicy \
    :kdom:checkBuildCachePolicy --console=plain

# The first run populates compilation outputs. The second removes only local outputs,
# not shared cache entries: compilation must restore, but tests must execute again.
./gradlew :strings-gen:clean :strings-gen:test :kdom:cleanJvmTest :kdom:jvmTest \
    --build-cache --console=plain >"$LOGS/first.log" 2>&1 || { printf '%s\n' "First run failed: $LOGS/first.log"; exit 1; }
./gradlew :strings-gen:clean :strings-gen:test :kdom:cleanJvmTest :kdom:jvmTest \
    --build-cache --console=plain >"$LOGS/second.log" 2>&1 || { printf '%s\n' "Second run failed: $LOGS/second.log"; exit 1; }
./gradlew :strings-gen:test :kdom:jvmTest --build-cache --console=plain \
    >"$LOGS/repeat.log" 2>&1 || { printf '%s\n' "Repeat failed: $LOGS/repeat.log"; exit 1; }
python3 -I - "$LOGS/second.log" "$LOGS/repeat.log" <<'PY'
import pathlib
import re
import sys

log = pathlib.Path(sys.argv[1]).read_text()
assert re.search(r"^> Task :strings-gen:compileKotlin FROM-CACHE$", log, re.M), log
for path in sys.argv[1:]:
    run = pathlib.Path(path).read_text()
    for task in (":strings-gen:test", ":kdom:jvmTest"):
        assert re.search(r"^> Task " + re.escape(task) + r"$", run, re.M), run
        assert not re.search(r"^> Task " + re.escape(task) + r" (FROM-CACHE|UP-TO-DATE)$", run, re.M), run
print("PASS: compilation restored FROM-CACHE; tests executed after clean and again without clean")
PY
