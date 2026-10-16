package net.bible.shareddata.spike

import androidx.room3.ConstructedBy
import androidx.room3.Dao
import androidx.room3.Database
import androidx.room3.Entity
import androidx.room3.Insert
import androidx.room3.PrimaryKey
import androidx.room3.Query
import androidx.room3.RoomDatabase
import androidx.room3.RoomDatabaseConstructor

@Entity
data class SpikeEntity(@PrimaryKey val id: Int, val text: String)

@Dao
interface SpikeDao {
    @Insert
    suspend fun insert(entity: SpikeEntity)

    @Query("SELECT * FROM SpikeEntity WHERE id = :id")
    suspend fun find(id: Int): SpikeEntity?
}

@Database(entities = [SpikeEntity::class], version = 1)
@ConstructedBy(SpikeDatabaseConstructor::class)
abstract class SpikeDatabase : RoomDatabase() {
    abstract fun spikeDao(): SpikeDao
}

@Suppress("KotlinNoActualForExpect")
expect object SpikeDatabaseConstructor : RoomDatabaseConstructor<SpikeDatabase> {
    override fun initialize(): SpikeDatabase
}
