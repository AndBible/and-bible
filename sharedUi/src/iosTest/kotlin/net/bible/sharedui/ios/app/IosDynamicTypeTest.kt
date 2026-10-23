package net.bible.sharedui.ios.app

import platform.Foundation.NSNotificationCenter
import platform.UIKit.UIContentSizeCategoryDidChangeNotification
import platform.UIKit.UIApplicationDidBecomeActiveNotification
import kotlin.test.Test
import kotlin.test.assertEquals

class IosDynamicTypeTest {
    @Test
    fun mapsEveryCategory() {
        val bodyPoints = listOf(
            "UICTContentSizeCategoryXS" to 14f,
            "UICTContentSizeCategoryS" to 15f,
            "UICTContentSizeCategoryM" to 16f,
            "UICTContentSizeCategoryL" to 17f,
            "UICTContentSizeCategoryXL" to 19f,
            "UICTContentSizeCategoryXXL" to 21f,
            "UICTContentSizeCategoryXXXL" to 23f,
            "UICTContentSizeCategoryAccessibilityM" to 28f,
            "UICTContentSizeCategoryAccessibilityL" to 33f,
            "UICTContentSizeCategoryAccessibilityXL" to 40f,
            "UICTContentSizeCategoryAccessibilityXXL" to 47f,
            "UICTContentSizeCategoryAccessibilityXXXL" to 53f,
        )
        for ((category, points) in bodyPoints) {
            assertEquals(points / 17f, iosFontScale(category), 0.0001f, category)
        }
        assertEquals(1f, iosFontScale("unknown-category"))
    }

    @Test
    fun fontScaleRefreshesWhenReturningFromSystemSettings() {
        var category = "UICTContentSizeCategoryL"
        val center = NSNotificationCenter()
        val observer = IosDynamicTypeObserver(categorySource = { category }, notificationCenter = center)
        try {
            observer.start()
            category = "UICTContentSizeCategoryAccessibilityXXXL"
            center.postNotificationName(UIApplicationDidBecomeActiveNotification, null)
            assertEquals(53f / 17f, observer.fontScale.value, 0.0001f)
        } finally {
            observer.stop()
        }
        category = "UICTContentSizeCategoryS"
        center.postNotificationName(UIApplicationDidBecomeActiveNotification, null)
        assertEquals(53f / 17f, observer.fontScale.value, 0.0001f, "stopped observer must ignore foreground changes")
    }

    @Test
    fun fontScaleFollowsContentSizeChangeNotification() {
        var category = "UICTContentSizeCategoryL"
        // A private center avoids global observers; the observer must use the real notification path.
        val center = NSNotificationCenter()
        val observer = IosDynamicTypeObserver(categorySource = { category }, notificationCenter = center)
        try {
            observer.start()
            assertEquals(1f, observer.fontScale.value)
            category = "UICTContentSizeCategoryAccessibilityXXXL"
            assertEquals(1f, observer.fontScale.value, "category source alone must not announce an update")
            center.postNotificationName(UIContentSizeCategoryDidChangeNotification, null)
            assertEquals(53f / 17f, observer.fontScale.value, 0.0001f)
            category = "UICTContentSizeCategoryS"
            center.postNotificationName(UIContentSizeCategoryDidChangeNotification, null)
            assertEquals(15f / 17f, observer.fontScale.value, 0.0001f)
        } finally {
            observer.stop()
        }
        category = "UICTContentSizeCategoryXXXL"
        center.postNotificationName(UIContentSizeCategoryDidChangeNotification, null)
        assertEquals(15f / 17f, observer.fontScale.value, 0.0001f, "disposed observer must stop receiving notifications")
    }
}
