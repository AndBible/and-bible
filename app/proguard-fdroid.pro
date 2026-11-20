# F-Droid flavour only (app/build.gradle.kts): F-Droid builds from source itself, so the mapping of the APK users run
# is not ours to keep. Shrinking and optimisation stay on; names stay readable in stack traces. #4160,
# docs/r8-obfuscation.md.
-dontobfuscate
