package net.bible.sharedcore.platform

fun interface NotificationPermission {
    suspend fun ensureAsked()
}

object NoNotificationPermission : NotificationPermission {
    override suspend fun ensureAsked() = Unit
}
