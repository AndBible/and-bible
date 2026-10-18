package net.bible.service.legacymigration

import net.bible.sharedcore.platform.secrets.SecretStore
import okio.FileSystem
import okio.Path

/**
 * What the legacy migration needs from the platform it runs on: the file locations of the old Swift app, its
 * UserDefaults and Keychain, and the new app's [secrets] store. The common code only reads through this interface;
 * the iOS implementation is `Task 11`, tests use a fake built from a fixture directory.
 *
 * All paths are absolute. [applicationSupport] is the platform's `Application Support` directory itself (the Swift app
 * keeps its files under `Application Support/AndBible/...`).
 */
interface LegacyPlatform {
    val fileSystem: FileSystem

    /** The `Application Support` directory. */
    val applicationSupport: Path

    /** The app's `Documents` directory (SWORD root, `sword_install`, `jsword/readingplan`, ...). */
    val documents: Path

    /** The directory the new app's Room databases live in. */
    val databasesDir: Path

    /** A snapshot of the standard UserDefaults domain (`Boolean`, `Int`/`Long`, `Double`, `String`, lists, ...). */
    fun userDefaults(): Map<String, Any?>

    /** A generic-password Keychain item of the old app, or null when it is absent or unreadable. */
    fun legacyKeychain(service: String, account: String): String?

    /** The new app's own secret store (API keys are written here by the adopter, not by the mappers). */
    val secrets: SecretStore
}
