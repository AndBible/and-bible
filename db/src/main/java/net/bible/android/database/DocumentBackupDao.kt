package net.bible.android.database

import androidx.room.*

@Entity
data class DocumentBackup(
    // TODO osisId -> initials in DB
    @PrimaryKey @ColumnInfo(name = "osisId") var initials: String,
    var name: String,
    var abbreviation: String,
    var language: String,
    var repository: String,
    var cipherKey: String? = null,
)

@Dao
interface DocumentBackupDao {
    @Insert
    fun insert(documents: List<DocumentBackup>)

    @Insert
    fun insert(documents: DocumentBackup)

    @Update
    fun update(doc: DocumentBackup)

    @Query("""SELECT * FROM DocumentBackup WHERE osisId = :initials""")
    fun getBook(initials: String): DocumentBackup?

    @Query("""SELECT * from DocumentBackup""")
    fun getKnownInstalled(): List<DocumentBackup>

    @Query("""SELECT * from DocumentBackup WHERE cipherKey IS NOT NULL""")
    fun getUnlocked(): List<DocumentBackup>

    @Query("""DELETE FROM DocumentBackup WHERE osisId = :initials""")
    fun deleteByOsisId(initials: String)

}
