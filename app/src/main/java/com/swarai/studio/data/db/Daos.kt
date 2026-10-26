package com.swarai.studio.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ProjectDao {
    @Query("SELECT * FROM projects ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<ProjectEntity>>

    @Query("SELECT * FROM projects WHERE id = :id")
    suspend fun get(id: Long): ProjectEntity?

    @Query("SELECT * FROM projects WHERE id = :id")
    fun observe(id: Long): Flow<ProjectEntity?>

    @Insert
    suspend fun insert(project: ProjectEntity): Long

    @Update
    suspend fun update(project: ProjectEntity)

    @Delete
    suspend fun delete(project: ProjectEntity)
}

@Dao
interface SongDao {
    @Query("SELECT * FROM songs WHERE voicePreviewId IS NULL ORDER BY createdAt DESC")
    fun observeLibrary(): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE voicePreviewId IS NOT NULL ORDER BY createdAt DESC")
    fun observeVoicePreviews(): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE projectId = :projectId ORDER BY createdAt DESC")
    fun observeForProject(projectId: Long): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE id = :id")
    suspend fun get(id: Long): SongEntity?

    @Query("SELECT * FROM songs WHERE id = :id")
    fun observe(id: Long): Flow<SongEntity?>

    @Query("SELECT * FROM songs ORDER BY createdAt DESC")
    suspend fun all(): List<SongEntity>

    @Insert
    suspend fun insert(song: SongEntity): Long

    @Update
    suspend fun update(song: SongEntity)

    @Delete
    suspend fun delete(song: SongEntity)
}

@Dao
interface PresetDao {
    @Query("SELECT * FROM presets ORDER BY favorite DESC, createdAt DESC")
    fun observeAll(): Flow<List<PresetEntity>>

    @Insert
    suspend fun insert(preset: PresetEntity): Long

    @Update
    suspend fun update(preset: PresetEntity)

    @Delete
    suspend fun delete(preset: PresetEntity)
}

@Dao
interface JobDao {
    @Query("SELECT * FROM generation_jobs ORDER BY createdAt DESC LIMIT 50")
    fun observeRecent(): Flow<List<GenerationJobEntity>>

    @Query("SELECT * FROM generation_jobs WHERE id = :id")
    suspend fun get(id: Long): GenerationJobEntity?

    @Query("SELECT * FROM generation_jobs WHERE id = :id")
    fun observe(id: Long): Flow<GenerationJobEntity?>

    @Insert
    suspend fun insert(job: GenerationJobEntity): Long

    @Update
    suspend fun update(job: GenerationJobEntity)
}
