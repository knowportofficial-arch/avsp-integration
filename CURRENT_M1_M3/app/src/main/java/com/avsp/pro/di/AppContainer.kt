package com.avsp.pro.di

import android.content.Context
import com.avsp.pro.audio.engine.DefaultTtsEngineRegistry
import com.avsp.pro.audio.engine.LocalFileVoiceCloneProvider
import com.avsp.pro.audio.engine.VoiceCloneTtsEngine
import com.avsp.pro.audio.repository.AudioRepository
import com.avsp.pro.audio.repository.AudioRepositoryImpl
import com.avsp.pro.capture.data.MediaRepository
import com.avsp.pro.capture.data.ProjectRepository as CaptureProjectRepository
import com.avsp.pro.capture.database.CaptureDatabase
import com.avsp.pro.database.AvspDatabase
import com.avsp.pro.database.DatabaseProvider
import com.avsp.pro.dataset.api.DatasetAutomationContract
import com.avsp.pro.dataset.api.DefaultEditorSelectionProvider
import com.avsp.pro.dataset.api.EditorSelectionProvider
import com.avsp.pro.dataset.api.MediaSelectionApi
import com.avsp.pro.dataset.repository.DatasetRepository
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
import com.avsp.pro.script.generator.DefaultScriptGeneratorRegistry
import com.avsp.pro.script.repository.ScriptRepository
import com.avsp.pro.script.repository.ScriptRepositoryImpl
import com.avsp.pro.settings.EncryptedSecureConfigStore
import com.avsp.pro.settings.SecureConfigStore
import com.avsp.pro.storage.AvspStorage
import com.avsp.pro.storage.FileAvspStorage

/**
 * Service locator for M1 + M2 + M3 + M7 capture/vision wiring.
 * Keeps Compose free of Room/DAO access.
 */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val database: AvspDatabase = DatabaseProvider.get(appContext)
    val captureDatabase: CaptureDatabase = CaptureDatabase.get(appContext)
    val storage: AvspStorage = FileAvspStorage(appContext)
    val secureConfigStore: SecureConfigStore = EncryptedSecureConfigStore(appContext)

    val logRepository: LogRepository = LogRepositoryImpl(database.logDao())
    val logger: AvspLogger = AvspLoggerImpl(logRepository)

    val projectRepository: ProjectRepository = ProjectRepositoryImpl(
        projectDao = database.projectDao(),
        mediaAssetDao = database.mediaAssetDao(),
        storage = storage,
        logger = logger,
        onProjectDeleted = { projectId ->
            captureDatabase.mediaDao().deleteAllForProject(projectId)
        }
    )

    val settingsRepository: SettingsRepository = SettingsRepositoryImpl(
        settingsDao = database.settingsDao(),
        secureConfigStore = secureConfigStore
    )

    val moduleStatusRepository: ModuleStatusRepository = ModuleStatusRepositoryImpl(
        dao = database.moduleStatusDao()
    )

    val voiceCloneProvider = LocalFileVoiceCloneProvider(storage)

    private val scriptGeneratorRegistry = DefaultScriptGeneratorRegistry(secureConfigStore)

    val scriptRepository: ScriptRepository = ScriptRepositoryImpl(
        storage = storage,
        generatorRegistry = scriptGeneratorRegistry,
        logger = logger
    )

    private val ttsEngineRegistry = DefaultTtsEngineRegistry(
        context = appContext,
        secureConfigStore = secureConfigStore,
        voiceCloneEngine = VoiceCloneTtsEngine(voiceCloneProvider)
    )

    val audioRepository: AudioRepository = AudioRepositoryImpl(
        storage = storage,
        scriptRepository = scriptRepository,
        ttsRegistry = ttsEngineRegistry,
        logger = logger,
        voiceCloneProvider = voiceCloneProvider
    )

    /** M6/M7 capture media + quality metadata (keyed by Pro projectId). */
    val mediaRepository: MediaRepository = MediaRepository(
        context = appContext,
        mediaDao = captureDatabase.mediaDao(),
        proProjectRepository = projectRepository
    )

    val captureProjectRepository: CaptureProjectRepository = CaptureProjectRepository(projectRepository)

    val datasetRepository: DatasetRepository = DatasetRepository(
        context = appContext,
        mediaDao = captureDatabase.mediaDao()
    )

    val mediaSelectionApi: MediaSelectionApi = MediaSelectionApi(datasetRepository)

    val datasetAutomationContract: DatasetAutomationContract =
        DatasetAutomationContract(datasetRepository, mediaSelectionApi)

    val editorSelectionProvider: EditorSelectionProvider =
        DefaultEditorSelectionProvider(captureDatabase.mediaDao())
}
