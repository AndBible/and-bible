package net.bible.test

import androidx.test.core.app.ApplicationProvider
import net.bible.android.platform.AndroidCoreStrings
import net.bible.service.common.CommonUtils
import net.bible.sharedcore.platform.AppSettings
import net.bible.sharedcore.platform.CoreStrings

/** The app's own settings, as the Koin `coreModule` binds them. */
fun testAppSettings(): AppSettings = CommonUtils.settings

/** The real resource-backed strings (Robolectric tests). */
fun testCoreStrings(): CoreStrings = AndroidCoreStrings(ApplicationProvider.getApplicationContext())
