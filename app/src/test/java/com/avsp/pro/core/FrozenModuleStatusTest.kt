package com.avsp.pro.core

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.avsp.pro.core.module.AvspModules
import com.avsp.pro.core.module.ModuleRunStatus
import com.avsp.pro.database.AvspDatabase
import com.avsp.pro.repository.ModuleStatusRepositoryImpl
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Frozen baseline status tests. M4 is intentionally in development while the Android renderer is being accepted.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class FrozenModuleStatusTest {

    private lateinit var database: AvspDatabase
    private lateinit var repo: ModuleStatusRepositoryImpl

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        database = Room.inMemoryDatabaseBuilder(context, AvspDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repo = ModuleStatusRepositoryImpl(database.moduleStatusDao())
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun ensureDefaults_setsRequiredInitialStatuses() = runBlocking {
        repo.ensureDefaults()
        assertThat(repo.get(AvspModules.M1_CORE_UI)!!.status).isEqualTo(ModuleRunStatus.FROZEN)
        assertThat(repo.get(AvspModules.M2_SCRIPT_AI)!!.status).isEqualTo(ModuleRunStatus.FROZEN)
        assertThat(repo.get(AvspModules.M3_AUDIO_TTS)!!.status).isEqualTo(ModuleRunStatus.READY)
        assertThat(repo.get(AvspModules.M4_VIDEO_ENGINE)!!.status).isEqualTo(ModuleRunStatus.RUNNING)
        assertThat(repo.get(AvspModules.M5_YOUTUBE_SCREEN)!!.status).isEqualTo(ModuleRunStatus.FROZEN)
        listOf("M5", "M6", "M7", "M8", "M9").forEach { id ->
            assertThat(repo.get(id)!!.status).isEqualTo(ModuleRunStatus.FROZEN)
        }
    }

    @Test
    fun frozenEnum_isParseableAndDistinct() {
        assertThat(ModuleRunStatus.fromRaw("FROZEN")).isEqualTo(ModuleRunStatus.FROZEN)
        assertThat(ModuleRunStatus.FROZEN).isNotEqualTo(ModuleRunStatus.NOT_STARTED)
        assertThat(ModuleRunStatus.FROZEN).isNotEqualTo(ModuleRunStatus.DISABLED)
    }

    @Test
    fun registerOrUpdate_keepsM5toM9Frozen() = runBlocking {
        repo.ensureDefaults()
        repo.registerOrUpdate(
            com.avsp.pro.core.module.ModuleStatus(
                moduleId = AvspModules.M5_YOUTUBE_SCREEN,
                displayName = "M5 YouTube/Screen",
                version = "0.0.0",
                status = ModuleRunStatus.READY,
                lastUpdated = System.currentTimeMillis()
            )
        )
        assertThat(repo.get(AvspModules.M5_YOUTUBE_SCREEN)!!.status)
            .isEqualTo(ModuleRunStatus.FROZEN)
    }
}
