package net.bible.service.cloudsync.drive.oauth

/**
 * An OAuth client registered in the AndBible Google Cloud project.
 *
 * @property clientSecret Desktop clients send it in the token exchange (Google treats it as non-confidential);
 *   iOS clients have none.
 */
data class GoogleOAuthClient(val clientId: String, val clientSecret: String? = null)
