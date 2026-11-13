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

    /**
     * The "Web application" client of the Android browser sign-in (F-Droid builds, and Play builds on devices without
     * Play services). Its redirect is the verified App Link [ANDROID_WEB_REDIRECT_URI]. Google's token endpoint wants
     * the secret even with PKCE; in an installed app it is not confidential, for the same reason as [desktop]'s.
     */
    val androidWeb: GoogleOAuthClient = GoogleOAuthClient(
        clientId = "533479479097-d64q085rb43su0dido7uso490o2m397l.apps.googleusercontent.com",
        clientSecret = "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",
    )

    /** The only redirect URI registered on [androidWeb]; Android routes it to AndBible as an App Link. */
    const val ANDROID_WEB_REDIRECT_URI = "https://read.andbible.org/oauth2/drive"
}
