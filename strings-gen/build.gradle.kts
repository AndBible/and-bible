plugins { kotlin("jvm") }
dependencies { testImplementation(kotlin("test")) }
tasks.test {
    useJUnitPlatform()
    // CoverageTest runs the parsers against the REAL repo files; resolve them from the repo root.
    systemProperty("repoRoot", rootDir.absolutePath)
}
kotlin { jvmToolchain(17) }
