#!/usr/bin/env bash
set -euo pipefail
root=$(cd "$(dirname "$0")/../.." && pwd)
tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT
mkdir -p "$tmp/input/jbr/lib"
printf 'fixture cef\n' > "$tmp/input/jbr/lib/libcef.so"
tar -czf "$tmp/fixture.tar.gz" -C "$tmp/input" jbr
digest=$(sha512sum "$tmp/fixture.tar.gz" | cut -d' ' -f1)
export KCEF_TEST_TMP="$tmp" KCEF_TEST_DIGEST="$digest"
cat > "$tmp/test.init.gradle" <<'GRADLE'
gradle.beforeProject { project ->
    if (project.path == ':desktopApp') {
        project.layout.buildDirectory.set(new File(System.getenv('KCEF_TEST_TMP'), 'build'))
        project.afterEvaluate {
            project.tasks.named('prepareKcefNatives').configure {
                packageUrl.set('http://127.0.0.1:1/must-not-download.tar.gz')
                expectedDigest.set(System.getenv('KCEF_TEST_DIGEST'))
            }
        }
    }
}
GRADLE
cd "$root"
./gradlew --offline -I "$tmp/test.init.gradle" :desktopApp:prepareKcefNatives \
    "-Pandbible.kcefArchive=$tmp/fixture.tar.gz"
test "$(cat "$tmp/build/kcef-natives/lib/libcef.so")" = 'fixture cef'
printf 'corrupt archive\n' >> "$tmp/fixture.tar.gz"
if ./gradlew --offline -I "$tmp/test.init.gradle" :desktopApp:prepareKcefNatives \
    "-Pandbible.kcefArchive=$tmp/fixture.tar.gz" > "$tmp/corrupt.log" 2>&1; then
    printf 'Corrupt local archive unexpectedly accepted\n' >&2
    exit 1
fi
rg -q 'Checksum mismatch for local KCEF archive' "$tmp/corrupt.log"
printf 'Valid archive accepted; changed corrupt archive rejected without downloading\n'
