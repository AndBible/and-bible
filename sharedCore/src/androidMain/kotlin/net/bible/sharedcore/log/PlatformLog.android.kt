package net.bible.sharedcore.log

internal actual fun platformLog(level: LogLevel, tag: String, msg: String, tr: Throwable?) {
    when (level) {
        LogLevel.VERBOSE -> android.util.Log.v(tag, msg, tr)
        LogLevel.DEBUG -> android.util.Log.d(tag, msg, tr)
        LogLevel.INFO -> android.util.Log.i(tag, msg, tr)
        LogLevel.WARN -> android.util.Log.w(tag, msg, tr)
        LogLevel.ERROR -> android.util.Log.e(tag, msg, tr)
    }
}
