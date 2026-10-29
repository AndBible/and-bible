package net.bible.sharedui.host.ports

import net.bible.sharedui.strings.keyed.StringTable

internal object HostTellAFriend {
    fun message(table: StringTable): String {
        val introduction = table.fmt("tell_friend_message1", table.text("app_name_long"))
        val description = table.text("tell_friend_message2_device")
        val homepage = table.fmt("tell_friend_message4", "https://andbible.org")
        return "$introduction $description\n\n$homepage"
    }
}
