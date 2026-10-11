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

/** An engine using this test's application paths rather than a previous Robolectric application's context. */
fun testBackupControl(): net.bible.android.control.backup.BackupControl {
    val koin = org.koin.core.context.GlobalContext.get()
    val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    return net.bible.android.control.backup.BackupControl(
        koin.get(), AndroidCoreStrings(context), net.bible.android.platform.AndroidAppEnvironment(context),
        testAppSettings(), koin.get(), koin.get(), koin.get(),
    )
}

fun testSwordContentFacade(): net.bible.service.sword.SwordContentFacade {
    val koin = org.koin.core.context.GlobalContext.get()
    val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    return net.bible.service.sword.SwordContentFacade(
        testCoreStrings(), net.bible.android.platform.AndroidAppEnvironment(context),
        net.bible.service.sword.HtmlToPlainText { net.bible.service.common.htmlToSpan(it).toString() },
        koin.get(), koin.get(),
    )
}
