package net.bible.android.database

import androidx.room.*

@Entity
data class AppPreferences(
    @PrimaryKey var key: String,
    @ColumnInfo(defaultValue = "NULL") var data: String? = null,
)

@Dao
interface AppPreferencesDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun update(pref: AppPreferences)

    @Query("""SELECT * FROM AppPreferences WHERE `key` = :key""")
    fun getPreference(key: String): AppPreferences?
}
