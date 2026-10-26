package com.swarai.studio

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.swarai.studio.data.db.AppDatabase
import com.swarai.studio.data.db.ProjectEntity
import com.swarai.studio.data.db.SongEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DatabaseTest {
    private lateinit var db: AppDatabase

    @Before fun open() {
        db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, AppDatabase::class.java).build()
    }

    @After fun close() = db.close()

    @Test fun storesUnicodeLyricsExactly() = runBlocking {
        val samples = mapOf(
            "hi" to "मेरे रश्क-ए-क़मर, तू ने पहली नज़र",
            "bho" to "पिया के गउँवा में, लागेला हमरो जियरा",
            "mai" to "जय जय भैरवि असुर भयाउनि",
            "en" to "Golden fields under monsoon rain",
        )
        samples.forEach { (lang, text) -> db.projects().insert(ProjectEntity(title = lang, lyrics = text, originalLyrics = text, languageId = lang)) }
        val back = db.projects().observeAll().first().associate { it.languageId to it.lyrics }
        samples.forEach { (lang, text) -> assertEquals(text, back[lang]) }
    }

    @Test fun deletingProjectKeepsSongs() = runBlocking {
        val pid = db.projects().insert(ProjectEntity(title = "p"))
        val sid = db.songs().insert(SongEntity(projectId = pid, title = "s", filePath = "/x.mp3", format = "mp3"))
        db.projects().delete(db.projects().get(pid)!!)
        assertNull(db.songs().get(sid)!!.projectId)
    }
}
