package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "python_scripts")
data class PythonScript(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val content: String,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "pip_packages")
data class PipPackage(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val version: String,
    val summary: String,
    val installTime: Long = System.currentTimeMillis(),
    val isPrebuilt: Boolean = true
)

@Dao
interface PythonDao {
    // Scripts
    @Query("SELECT * FROM python_scripts ORDER BY updatedAt DESC")
    fun getAllScripts(): Flow<List<PythonScript>>

    @Query("SELECT * FROM python_scripts WHERE id = :id")
    suspend fun getScriptById(id: Int): PythonScript?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertScript(script: PythonScript): Long

    @Delete
    suspend fun deleteScript(script: PythonScript)

    @Query("DELETE FROM python_scripts WHERE id = :id")
    suspend fun deleteScriptById(id: Int)

    // Pip Packages
    @Query("SELECT * FROM pip_packages ORDER BY installTime DESC")
    fun getAllPackages(): Flow<List<PipPackage>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPackage(pkg: PipPackage): Long

    @Query("DELETE FROM pip_packages WHERE name = :name")
    suspend fun deletePackageByName(name: String)

    @Query("SELECT COUNT(*) FROM pip_packages WHERE name = :name")
    suspend fun hasPackage(name: String): Int
}
