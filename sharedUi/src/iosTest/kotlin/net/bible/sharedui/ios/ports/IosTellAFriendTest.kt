package net.bible.sharedui.ios.ports

import net.bible.sharedui.assembly.iosStringTable
import kotlin.test.*
import net.bible.sharedui.host.ports.IosTellAFriend

class IosTellAFriendTest {
    @Test fun messageHasHomepageAndNoStoreLink() {
        val message = IosTellAFriend.message(task4Table())
        assertContains(message, "en:tell_friend_message1(en:app_name_long)")
        assertContains(message, "en:tell_friend_message2_device")
        assertContains(message, "en:tell_friend_message4(https://andbible.org)")
        assertFalse(message.contains("en:tell_friend_message2 "))
        listOf("play.google.com", "apps.apple.com", "itms-apps", "tell_friend_message3").forEach {
            assertFalse(message.contains(it), "Unexpected store target: $it")
        }
    }

    @Test fun englishRecommendationDoesNotClaimToBeAnAndroidApplication() {
        val message = IosTellAFriend.message(iosStringTable("en"))
        assertContains(message, "https://andbible.org")
        assertContains(message, "offline Bible study application")
        assertFalse(message.contains("Android", ignoreCase = true))
        assertFalse(message.contains("tell_friend_message2_device"), "Missing keyed English resource")
    }
}
