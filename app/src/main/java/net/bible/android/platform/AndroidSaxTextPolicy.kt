package net.bible.android.platform

import android.os.Build
import net.bible.service.format.SaxTextPolicy

fun androidSaxTextPolicy(): SaxTextPolicy =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) SaxTextPolicy.MODERN else SaxTextPolicy.LEGACY
