#!/usr/bin/env bash
set -euo pipefail

repo=$(cd "$(dirname "$0")/.." && pwd)
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
mkdir -p "$work/project/classes" "$work/cache"
printf 'fixture' > "$work/project/classes/Fixture.class"
project="$work/project"
export FIXTURE_CACHE="$work/cache"
init_script="$repo/scripts/mac-build-cache.init.gradle"

cat > "$project/settings.gradle" <<'GRADLE'
rootProject.name = 'cache-fixture'
buildCache { local { directory = file(System.getenv('FIXTURE_CACHE')) } }
GRADLE
cat > "$project/build.gradle" <<'GRADLE'
import org.gradle.api.tasks.testing.Test
plugins { id 'java' }

@CacheableTask
abstract class CompileFixture extends DefaultTask {
    @OutputFile abstract RegularFileProperty getResultFile()
    @TaskAction void compile() { resultFile.get().asFile.text = 'compiled' }
}
tasks.register('compileFixture', CompileFixture) {
    resultFile = layout.buildDirectory.file('compiled.txt')
}
tasks.register('testFixture', Test) {
    dependsOn 'compileFixture'
    testClassesDirs = files('classes')
    // Keep the real Test type and caching rules, but avoid a JUnit dependency.
    setActions([])
    outputs.file(layout.buildDirectory.file('test-result.txt'))
    doLast { layout.buildDirectory.file('test-result.txt').get().asFile.text = 'tested' }
}
GRADLE
run_gradle() {
    "$repo/gradlew" -p "$project" --offline --console plain -I "$init_script" "$@" > "$work/output" 2>&1 || { printf 'Gradle failed:\n'; command cat "$work/output"; exit 1; }
}
expect() {
    if ! rg -q -- "$1" "$work/output"; then
        printf 'Expected %s in Gradle output:\n' "$1"
        command cat "$work/output"
        exit 1
    fi
}

# With the guard absent the init script must not enable caching or disable test caching.
unset MAC_REMOTE_BUILD_CACHE || true
run_gradle testFixture
rm -rf "$project/build"
run_gradle testFixture
expect '^> Task :compileFixture$'
expect '^> Task :testFixture$'
run_gradle testFixture --build-cache --rerun-tasks
rm -rf "$project/build"
run_gradle testFixture --build-cache
expect '^> Task :compileFixture FROM-CACHE$'
expect '^> Task :testFixture FROM-CACHE$'

# Guarded runs use the existing compilation cache but never restore test output.
export MAC_REMOTE_BUILD_CACHE=1
rm -rf "$project/build"
run_gradle testFixture
expect '^> Task :compileFixture FROM-CACHE$'
expect '^> Task :testFixture$'
run_gradle testFixture
expect '^> Task :compileFixture UP-TO-DATE$'
expect '^> Task :testFixture UP-TO-DATE$'
run_gradle testFixture --rerun
expect '^> Task :compileFixture UP-TO-DATE$'
expect '^> Task :testFixture$'
# A guarded execution must not populate the test cache.
rm -rf "$work/cache" "$project/build"
run_gradle testFixture
rm -rf "$project/build"
unset MAC_REMOTE_BUILD_CACHE
run_gradle testFixture --build-cache
expect '^> Task :compileFixture FROM-CACHE$'
expect '^> Task :testFixture$'
printf 'Mac build cache fixture passed.\n'
