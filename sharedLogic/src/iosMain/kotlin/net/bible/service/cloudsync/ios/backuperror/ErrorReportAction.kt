package net.bible.service.cloudsync.ios

internal suspend fun errorReportAction(value: String?, cause: Throwable?, report: suspend (Throwable?) -> Unit, backup: () -> Unit) {
    when (value) {
        "report" -> report(cause)
        "backup" -> backup()
    }
}
