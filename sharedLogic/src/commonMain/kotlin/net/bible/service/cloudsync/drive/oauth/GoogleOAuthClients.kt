package net.bible.service.cloudsync.drive.oauth

/**
 * The OAuth clients of the iOS and desktop Drive sign-in, registered in the same Google Cloud project as the Android
 * client (`533479479097-…`), so all platforms share one `appDataFolder`. A `null` client means Google Drive is not
 * offered on that platform; the desktop also honours the JVM properties read by `desktopClient()` (jvmMain) for testing.
 */
object GoogleOAuthClients {
    /** The iOS client (bundle id `org.andbible.ios`): no secret, redirect is the reversed client id scheme. */
    val ios: GoogleOAuthClient? = GoogleOAuthClient(
        clientId = "533479479097-jrffr8s761jig9mspkplomaecqgume4h.apps.googleusercontent.com",
    )

    /**
     * The Desktop ("installed app") client. Google treats an installed app's secret as non-confidential: it is
     * meant to ship in the build, and PKCE protects the code exchange.
     */
    val desktop: GoogleOAuthClient? = GoogleOAuthClient(
        clientId = "533479479097-sp8bhqtuubpjqpk8mqs6sr084cige6a4.apps.googleusercontent.com",
        clientSecret = "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",
    )
}
