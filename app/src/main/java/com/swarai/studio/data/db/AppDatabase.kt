package com.swarai.studio.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [ProjectEntity::class, SongEntity::class, PresetEntity::class, GenerationJobEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun projects(): ProjectDao
    abstract fun songs(): SongDao
    abstract fun presets(): PresetDao
    abstract fun jobs(): JobDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "swarai.db").build()
    }
}
