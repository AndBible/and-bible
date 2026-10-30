/*
 * Copyright (c) 2026 Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
 *
 * This file is part of AndBible: Bible Study (http://github.com/AndBible/and-bible).
 *
 * AndBible is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software Foundation,
 * either version 3 of the License, or (at your option) any later version.
 *
 * AndBible is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with AndBible.
 * If not, see http://www.gnu.org/licenses/.
 */
package net.bible.sharedui.desktop.assembly

import net.bible.sharedcore.settings.DeviceLocalPrefs
import net.bible.sharedui.assembly.HostUiLanguage
import net.bible.sharedui.assembly.resolveDeviceUiLanguage
import java.util.Locale

/**
 * The desktop UI language: the hoisted iOS resolution (`locale_pref`, else the first preferred language the string
 * tables have, else English) fed with the desktop's preferred languages.
 */
object DesktopUiLanguage {
    /**
     * The preferred languages, best first, as BCP 47 tags: the gettext order `LANGUAGE` (colon separated), `LC_ALL`,
     * `LC_MESSAGES`, `LANG`, then the JVM default locale. Encoding and modifier suffixes (`.UTF-8`, `@euro`) are dropped;
     * `C` and `POSIX` mean "no preference" and are skipped.
     */
    fun preferredLanguages(env: (String) -> String?, locale: Locale = Locale.getDefault()): List<String> {
        val fromEnv = buildList {
            env("LANGUAGE")?.split(':')?.let(::addAll)
            listOf("LC_ALL", "LC_MESSAGES", "LANG").forEach { name -> env(name)?.let(::add) }
        }
        val tags = fromEnv.map { it.substringBefore('.').substringBefore('@').trim() }
            .filter { it.isNotEmpty() && it != "C" && it != "POSIX" }
        val jvm = locale.toLanguageTag().takeIf { it != "und" }
        return (tags.map { it.replace('_', '-') } + listOfNotNull(jvm)).distinct()
    }

    /** The language to use now: `locale_pref` of [settings] first, then [preferredLanguages]. */
    fun resolve(
        settings: DeviceLocalPrefs,
        env: (String) -> String? = System::getenv,
        locale: Locale = Locale.getDefault(),
    ): String = resolveDeviceUiLanguage(settings) { preferredLanguages(env, locale) }

    /** Whether [language] is written right to left. */
    fun isRtl(language: String): Boolean = HostUiLanguage.isRtl(language)
}
