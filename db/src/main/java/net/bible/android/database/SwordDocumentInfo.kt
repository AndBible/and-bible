package net.bible.android.database

import androidx.room.*

@Entity(tableName = "DocumentBackup")
data class SwordDocumentInfo(
    @PrimaryKey @ColumnInfo(name = "osisId") var initials: String,
    var name: String,
    var abbreviation: String,
    var language: String,
    var repository: String,
    var cipherKey: String? = null,
)

@Dao
interface SwordDocumentInfoDao {
    @Insert
    fun insert(documents: List<SwordDocumentInfo>)

    @Insert
    fun insert(documents: SwordDocumentInfo)

    @Update
    fun update(doc: SwordDocumentInfo)

    @Query("""SELECT * FROM DocumentBackup WHERE osisId = :initials""")
    fun getBook(initials: String): SwordDocumentInfo?

    @Query("""SELECT * from DocumentBackup""")
    fun getKnownInstalled(): List<SwordDocumentInfo>

    @Query("""SELECT * from DocumentBackup WHERE cipherKey IS NOT NULL""")
    fun getUnlocked(): List<SwordDocumentInfo>

    @Query("""DELETE FROM DocumentBackup WHERE osisId = :initials""")
    fun deleteByOsisId(initials: String)

}
