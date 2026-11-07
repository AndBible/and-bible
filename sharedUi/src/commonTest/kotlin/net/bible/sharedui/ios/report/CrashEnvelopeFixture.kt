package net.bible.sharedui.ios.report

/**
 * Copy of `iosApp/test-fixtures/crash-envelope.json`, the envelope `RecentCrashDiagnosticStore.swift` writes with the
 * real `JSONEncoder` (a Swift unit test asserts byte equality; `CrashEnvelopeFixtureSyncTest` on the JVM asserts that
 * this copy equals the file). The payload's base64 contains `/` (escaped `\/` in the JSON), `+` and `=`.
 */
internal const val CRASH_ENVELOPE_FIXTURE = """{"occurrence":1700000000.25,"payload":"W3siayI6Ij8\/Pz4+Pn4ifV0="}"""

/** The decoded payload of [CRASH_ENVELOPE_FIXTURE]. */
internal const val CRASH_ENVELOPE_FIXTURE_PAYLOAD = """[{"k":"???>>>~"}]"""

internal const val CRASH_ENVELOPE_FIXTURE_OCCURRENCE = 1700000000.25
