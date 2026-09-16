package com.avsp.pro.di

import android.content.Context
import com.avsp.pro.database.AvspDatabase
import com.avsp.pro.database.DatabaseProvider
import com.avsp.pro.logs.AvspLogger
import com.avsp.pro.logs.AvspLoggerImpl
import com.avsp.pro.repository.LogRepository
import com.avsp.pro.repository.LogRepositoryImpl
import com.avsp.pro.repository.ModuleStatusRepository
import com.avsp.pro.repository.ModuleStatusRepositoryImpl
import com.avsp.pro.repository.ProjectRepository
import com.avsp.pro.repository.ProjectRepositoryImpl
import com.avsp.pro.repository.SettingsRepository
import com.avsp.pro.repository.SettingsRepositoryImpl
import com.avsp.pro.settings.EncryptedSecureConfigStore
import com.avsp.pro.settings.SecureConfigStore
import com.avsp.pro.script.generator.DefaultScriptGeneratorRegistry
import com.avsp.pro.script.repository.ScriptRepository
import com.avsp.pro.script.repository.ScriptRepositoryImpl
import com.avsp.pro.audio.engine.DefaultTtsEngineRegistry
import com.avsp.pro.audio.repository.AudioRepository
import com.avsp.pro.audio.repository.AudioRepositoryImpl
import com.avsp.pro.video.engine.Media3VideoEngine
import com.avsp.pro.video.repository.VideoRepository
import com.avsp.pro.video.repository.VideoRepositoryImpl
import com.avsp.pro.storage.AvspStorage
import com.avsp.pro.storage.FileAvspStorage
import com.avsp.pro.media.BundledMediaSeeder
import com.avsp.pro.m7.M7Services

/**
 * Service locator for M1 + M2 + M3 wiring. Keeps Compose free of Room/DAO access.
 */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val m7Services: M7Services = M7Services(appContext)

    val database: AvspDatabase = DatabaseProvider.get(appContext)
    val storage: AvspStorage = FileAvspStorage(appContext)
    val secureConfigStore: SecureConfigStore = EncryptedSecureConfigStore(appContext)

    val logRepository: LogRepository = LogRepositoryImpl(database.logDao())
    val logger: AvspLogger = AvspLoggerImpl(logRepository)

    val bundledMediaSeeder: BundledMediaSeeder = BundledMediaSeeder(
        context = appContext,
        storage = storage,
        mediaAssetDao = database.mediaAssetDao(),
        logger = logger
    )

    val projectRepository: ProjectRepository = ProjectRepositoryImpl(
        projectDao = database.projectDao(),
        mediaAssetDao = database.mediaAssetDao(),
        storage = storage,
        logger = logger
    )

    val settingsRepository: SettingsRepository = SettingsRepositoryImpl(
        settingsDao = database.settingsDao(),
        secureConfigStore = secureConfigStore
    )

    val moduleStatusRepository: ModuleStatusRepository = ModuleStatusRepositoryImpl(
        dao = database.moduleStatusDao()
    )

    private val scriptGeneratorRegistry = DefaultScriptGeneratorRegistry(secureConfigStore)

    val scriptRepository: ScriptRepository = ScriptRepositoryImpl(
        storage = storage,
        generatorRegistry = scriptGeneratorRegistry,
        logger = logger
    )

    private val ttsEngineRegistry = DefaultTtsEngineRegistry(appContext, secureConfigStore)

    val audioRepository: AudioRepository = AudioRepositoryImpl(
        storage = storage,
        scriptRepository = scriptRepository,
        ttsRegistry = ttsEngineRegistry,
        logger = logger
    )

    private val videoEngine = Media3VideoEngine(appContext, storage)

    val videoRepository: VideoRepository = VideoRepositoryImpl(
        projectRepository = projectRepository,
        audioRepository = audioRepository,
        scriptRepository = scriptRepository,
        storage = storage,
        engine = videoEngine,
        logger = logger,
        bundledMediaSeeder = bundledMediaSeeder
    )
}

