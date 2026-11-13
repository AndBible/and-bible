package net.bible.service.cloudsync.drivebrowser

/** Which Google sign-in a non-F-Droid build uses for Drive. */
enum class DriveAuthChoice { PLAY, BROWSER }

/**
 * The sign-in rule of the Play/GitHub builds:
 * 1. a stored browser refresh token keeps the browser path (sticky: the app never flips between sign-ins);
 * 2. otherwise Play services, when available;
 * 3. otherwise the browser, when this build has the `read.andbible.org` App Link (standard appearance);
 * 4. otherwise Play (discrete without Play services: fails as before, the redirect could never come back).
 */
fun chooseDriveAuth(playServicesAvailable: Boolean, browserTokenStored: Boolean, appLinkAvailable: Boolean): DriveAuthChoice = when {
    browserTokenStored -> DriveAuthChoice.BROWSER
    playServicesAvailable -> DriveAuthChoice.PLAY
    appLinkAvailable -> DriveAuthChoice.BROWSER
    else -> DriveAuthChoice.PLAY
}
