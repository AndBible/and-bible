package net.bible.sharedui.icons

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.painterResource
import androidx.compose.ui.graphics.toPixelMap
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class AbIconsRenderTest {
    @Test
    fun everyIconResourceRendersToANonEmptyBitmap() = runComposeUiTest {
        val resources = AbIcons.all.values.toList()
        assertTrue(resources.isNotEmpty(), "AbIcons must expose its complete resource inventory")

        for ((index, resource) in resources.withIndex()) {
            setContent {
                Image(
                    painter = painterResource(resource),
                    contentDescription = null,
                    modifier = Modifier.size(48.dp).testTag("icon-$index"),
                )
            }
            val image = onNodeWithTag("icon-$index").captureToImage()
            assertTrue(image.width > 0 && image.height > 0, "resource $resource rendered an empty bitmap")
            assertTrue(
                image.toPixelMap().buffer.any { it != 0 },
                "resource $resource rendered a fully transparent bitmap",
            )
        }
    }

    @Test
    fun darkerGrayAndroidColorIsConvertedToItsOriginalArgbValue() {
        val resourcePath = File("src/commonMain/composeResources/drawable")
        val darkestGrayResources = setOf("ic_save_24dp.xml", "ic_syncdb_24dp.xml", "icon_circle_question.xml", "icon_robot.xml")
        for (name in darkestGrayResources) {
            val color = Regex("android:fillColor=\"(#[0-9a-fA-F]{6})\"")
                .find(File(resourcePath, name).readText())?.groupValues?.get(1)
            assertEquals("#aaaaaa", color, "$name must preserve @android:color/darker_gray")
        }
    }

    @Test
    fun labelKeysMatchAndroid() {
        val source = sequenceOf(
            File("../app/src/main/java/net/bible/android/view/activity/bookmark/LabelIconMap.kt"),
            File("app/src/main/java/net/bible/android/view/activity/bookmark/LabelIconMap.kt"),
        ).firstOrNull(File::isFile)
        assertTrue(source != null, "Could not find Android LabelIconMap.kt from ${File(".").absolutePath}")
        val androidKeys = Regex("\\\"([^\\\"]+)\\\"\\s+to\\s+R\\.drawable\\.")
            .findAll(source!!.readText())
            .map { it.groupValues[1] }
            .toSet()
        assertEquals(androidKeys, labelIconResources.keys)
    }
}
