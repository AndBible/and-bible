package net.bible.sharedui.ios.ports

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import net.bible.sharedui.assembly.iosStringTable
import kotlin.test.Test
import kotlin.test.assertEquals
import net.bible.sharedui.host.ports.IosInstallZipHostPlatform

class IosInstallZipHostPlatformTest {
    @Test fun formatsTextFollowsLanguageSwitch() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        var table = iosStringTable("en")
        try {
            val platform = IosInstallZipHostPlatform(table = { table }, scope = scope, notificationPermission = IosNotificationPermission())
            assertEquals(
                "You have to choose the file that you want to load to AndBible.  \n\n" +
                    "Supported formats for loading documents from a file: " +
                    "Zip file containing Sword module(s), or documents backup file (*.abmd) created by AndBible, " +
                    "MyBible documents (*.SQLite3), MySword documents (*.mybible), " +
                    "e-Sword documents (*.bblx, *.bbli), EPUB documents (*.epub), TTF fonts (*.ttf), " +
                    "AI prompt add-ons (*.csv), StudyPads from AndBible databases (*.abdb)",
                platform.formatsText(),
            )
            table = iosStringTable("fi")
            assertEquals(
                "Sinun pitää valita tiedosto jonka haluat ladata AndBibleen. \n\n" +
                    "Voit ladata tiedostosta näitä tiedostoformaatteja: " +
                    "Zip tiedosto joka sisältää Sword-moduulin / moduuleja tai dokumenttien " +
                    "varmuuskopioiitiedosto (*.abmd), joka on tehty AndBiblella., " +
                    "MyBible-dokumentti (*.SQLite3), MySword-dokumentti (*.mybible), " +
                    "e-Sword-teokset (*.bblx, *.bbli), EPUB-dokumentit (*.epub), TTF-fontit (*.ttf), " +
                    "Tekoälykehotteiden lisäosat (*.csv), Opintoalustat AndBible tietokannasta (*.abdb)",
                platform.formatsText(),
            )
        } finally {
            scope.cancel()
        }
    }
}
