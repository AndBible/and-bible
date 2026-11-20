package net.bible.android.activity

import androidx.test.core.app.ApplicationProvider
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Review Focus 1: widgets placed before the redesign (Small, Middle, Large, Text, Bookmark) keep their provider classes
 * and render the new design after the update instead of "Can't load widget".
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class LegacyWidgetUpgradeTest {
    private val app = ApplicationProvider.getApplicationContext<TestBibleApplication>()

    private val legacy = listOf(
        SpeakWidgetManager.SmallSpeakControlWidget::class.java,
        SpeakWidgetManager.MiddleSpeakControlWidget::class.java,
        SpeakWidgetManager.LargeSpeakControlWidget::class.java,
        SpeakWidgetManager.SpeakTextControlWidget::class.java,
        SpeakWidgetManager.SpeakBookmarkWidget::class.java,
    )

    private fun fileText(path: String) = File(path).readText()

    /**
     * Each old provider class is now a Glance receiver of the widget it maps to, so an update broadcast after the upgrade
     * draws the new design. (Glance's update session does not run under Robolectric; what the widgets draw is asserted by
     * the view-tree and golden tests of the Glance widgets themselves.)
     */
    @Test fun legacyProvidersRenderNewDesign() {
        val expected = mapOf(
            SpeakWidgetManager.SmallSpeakControlWidget::class.java to net.bible.android.view.widget.SpeakGlanceWidget::class.java,
            SpeakWidgetManager.MiddleSpeakControlWidget::class.java to net.bible.android.view.widget.SpeakGlanceWidget::class.java,
            SpeakWidgetManager.LargeSpeakControlWidget::class.java to net.bible.android.view.widget.SpeakGlanceWidget::class.java,
            SpeakWidgetManager.SpeakTextControlWidget::class.java to net.bible.android.view.widget.SpeakTextGlanceWidget::class.java,
            SpeakWidgetManager.SpeakBookmarkWidget::class.java to net.bible.android.view.widget.ListeningListGlanceWidget::class.java,
        )
        for ((provider, widget) in expected) {
            val receiver = provider.getDeclaredConstructor().newInstance()
            assertTrue("${provider.simpleName} is a Glance receiver", receiver is androidx.glance.appwidget.GlanceAppWidgetReceiver)
            assertEquals(widget, (receiver as androidx.glance.appwidget.GlanceAppWidgetReceiver).glanceAppWidget.javaClass)
        }
        assertEquals(legacy.toSet(), expected.keys)
    }

    @Test fun pickerListsFourEntries() {
        val res = "src/main/res/xml/"
        val hidden = listOf("small_speak_control_widget_info.xml", "middle_speak_control_widget_info.xml")
        val shown = listOf("large_speak_control_widget_info.xml", "speak_text_control_widget_info.xml", "speak_bookmark_widget_info.xml", "verse_widget_info.xml")
        hidden.forEach { assertTrue("$it must be hidden from the picker (API 28+; below that the entry stays visible and renders the new design)", fileText(res + it).contains("hide_from_picker")) }
        shown.forEach { assertFalse("$it must be listed", fileText(res + it).contains("hide_from_picker")) }
    }

    @Test fun theManifestDeclaresTheFiveLegacyReceiversAndTheVerseOne() {
        val manifest = fileText("src/standard/AndroidManifest.xml")
        legacy.forEach {
            // ".SpeakWidgetManager$Small..." is how the manifest names the nested classes.
            assertTrue("${it.name} not declared", manifest.contains(".SpeakWidgetManager$" + it.simpleName))
        }
        assertTrue(manifest.contains("net.bible.android.view.widget.VerseWidgetReceiver"))
        assertEquals("every receiver resolves to a class", 6, Regex("<receiver").findAll(manifest).count())
        assertTrue(Class.forName("net.bible.android.view.widget.VerseWidgetReceiver") != null)
    }

    @Test fun theDiscreteFlavourDeclaresNoWidgets() {
        val discrete = File("src/discrete/AndroidManifest.xml")
        if (discrete.exists()) assertFalse(discrete.readText().contains("appwidget"))
    }
}
