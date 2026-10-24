package net.bible.service.db

import net.bible.android.database.SettingsDatabase
import net.bible.sharedcore.settings.store.SettingWrite
import net.bible.sharedcore.settings.store.SettingsBackend
import net.bible.sharedcore.settings.store.SettingsSnapshot

/** [SettingsBackend] over the four setting tables of [SettingsDatabase]. */
class RoomSettingsBackend(private val db: SettingsDatabase) : SettingsBackend {
    override suspend fun loadAll() = SettingsSnapshot(
        db.booleanSettingDao().all().associate { it.key to it.value },
        db.longSettingDao().all().associate { it.key to it.value },
        db.stringSettingDao().all().associate { it.key to it.value },
        db.doubleSettingDao().all().associate { it.key to it.value },
    )

    override suspend fun write(write: SettingWrite) = when (write) {
        is SettingWrite.Bool -> db.booleanSettingDao().set(write.key, write.value)
        is SettingWrite.Lng -> db.longSettingDao().set(write.key, write.value)
        is SettingWrite.Str -> db.stringSettingDao().set(write.key, write.value)
        is SettingWrite.Dbl -> db.doubleSettingDao().set(write.key, write.value)
    }
}
