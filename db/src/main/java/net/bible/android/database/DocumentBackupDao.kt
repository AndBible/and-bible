package net.bible.android.database

import androidx.room.*

@Entity
data class DocumentBackup(
    @PrimaryKey
    var osisId: String,
    var name: String,
    var abbreviation: String,
    var language: String,
    var repository: String
)

@Dao
interface DocumentBackupDao {
    @Insert
    fun insertDocuments(documents: List<DocumentBackup>)

    @Query("""SELECT * from DocumentBackup""")
    fun getKnownInstalled(): List<DocumentBackup>

    @Query("""DELETE FROM DocumentBackup WHERE osisId = :osisId""")
    fun deleteByOsisId(osisId: String)

}
