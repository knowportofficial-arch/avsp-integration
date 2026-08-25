package com.avsp.pro

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.avsp.pro.core.error.ErrorCode
import com.avsp.pro.core.error.ErrorInfo
import com.avsp.pro.core.error.ErrorSeverity
import com.avsp.pro.core.error.InvalidInputException
import com.avsp.pro.core.error.ProjectNotFoundException
import com.avsp.pro.core.error.StorageException
import com.avsp.pro.core.model.AspectRatio
import com.avsp.pro.core.model.ProjectLanguage
import com.avsp.pro.core.model.ProjectStatus
import com.avsp.pro.core.module.AvspModules
import com.avsp.pro.core.module.ModuleRunStatus
import com.avsp.pro.core.module.ModuleStatus
import com.avsp.pro.core.ui.UiState
import com.avsp.pro.database.AvspDatabase
import com.avsp.pro.database.DatabaseProvider
import com.avsp.pro.logs.AvspLoggerImpl
import com.avsp.pro.logs.LogLevel
import com.avsp.pro.logs.SecretRedactor
import com.avsp.pro.repository.LogRepositoryImpl
import com.avsp.pro.repository.ModuleStatusRepositoryImpl
import com.avsp.pro.repository.ProjectRepositoryImpl
import com.avsp.pro.repository.SettingsRepositoryImpl
import com.avsp.pro.settings.AppSettings
import com.avsp.pro.settings.EncryptedSecureConfigStore
import com.avsp.pro.settings.SecureConfigKeys
import com.avsp.pro.settings.ThemePreference
import com.avsp.pro.storage.FileAvspStorage
import com.avsp.pro.storage.StorageArea
import com.avsp.pro.ui.navigation.AvspDestination
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File

/**
 * Comprehensive M1 test suite covering mandatory cases A–X.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class M1CoreSuiteTest {

    private lateinit var context: Context
    private lateinit var database: AvspDatabase
    private lateinit var storage: FileAvspStorage
    private lateinit var logRepository: LogRepositoryImpl
    private lateinit var logger: AvspLoggerImpl
    private lateinit var projectRepository: ProjectRepositoryImpl
    private lateinit var settingsRepository: SettingsRepositoryImpl
    private lateinit var moduleStatusRepository: ModuleStatusRepositoryImpl
    private lateinit var secureStore: EncryptedSecureConfigStore

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        DatabaseProvider.clearForTests()
        database = Room.inMemoryDatabaseBuilder(context, AvspDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        storage = FileAvspStorage(context)
        logRepository = LogRepositoryImpl(database.logDao())
        logger = AvspLoggerImpl(logRepository)
        projectRepository = ProjectRepositoryImpl(
            projectDao = database.projectDao(),
            mediaAssetDao = database.mediaAssetDao(),
            storage = storage,
            logger = logger
        )
        secureStore = EncryptedSecureConfigStore(context)
        settingsRepository = SettingsRepositoryImpl(database.settingsDao(), secureStore)
        moduleStatusRepository = ModuleStatusRepositoryImpl(database.moduleStatusDao())
    }

    @After
    fun tearDown() {
        database.close()
        DatabaseProvider.clearForTests()
    }

    // A — Application initialization
    @Test
    fun a_applicationInitialization() {
        val appName = context.packageName
        assertThat(appName).isEqualTo("com.avsp.pro")
        assertThat(context.filesDir.exists() || context.filesDir.mkdirs()).isTrue()
    }

    // B — Database initialization
    @Test
    fun b_databaseInitialization() {
        runBlocking {
        // Accessing a DAO opens the database; Room reports isOpen only after first open.
        assertThat(database.projectDao().count()).isEqualTo(0)
        assertThat(database.isOpen).isTrue()
        moduleStatusRepository.ensureDefaults()
        assertThat(moduleStatusRepository.getAll()).isNotEmpty()
        }
    }

    // C — Project creation
    @Test
    fun c_projectCreation() {
        runBlocking {
        val project = projectRepository.createProject("KnowPort Daily", "Weather gold petrol")
        assertThat(project.projectId).startsWith("prj_")
        assertThat(project.name).isEqualTo("KnowPort Daily")
        assertThat(project.status).isEqualTo(ProjectStatus.DRAFT)
        assertThat(project.aspectRatio).isEqualTo(AspectRatio.RATIO_9_16)
        assertThat(storage.exists(StorageArea.PROJECT_DATA, "generated", project.projectId)).isTrue()
        }
    }

    // D — Project retrieval
    @Test
    fun d_projectRetrieval() {
        runBlocking {
        val created = projectRepository.createProject("Retrieve Me")
        val opened = projectRepository.openProject(created.projectId)
        assertThat(opened.projectId).isEqualTo(created.projectId)
        assertThat(opened.name).isEqualTo("Retrieve Me")
        }
    }

    // E — Project update
    @Test
    fun e_projectUpdate() {
        runBlocking {
        val created = projectRepository.createProject("Before")
        val updated = projectRepository.updateProject(
            created.copy(
                description = "After update",
                status = ProjectStatus.READY,
                language = ProjectLanguage.BENGALI
            )
        )
        assertThat(updated.description).isEqualTo("After update")
        assertThat(updated.status).isEqualTo(ProjectStatus.READY)
        assertThat(updated.language).isEqualTo(ProjectLanguage.BENGALI)
        assertThat(updated.updatedAt).isAtLeast(created.updatedAt)
        }
    }

    // F — Project deletion
    @Test
    fun f_projectDeletion() {
        runBlocking {
        val created = projectRepository.createProject("Delete Me")
        projectRepository.deleteProject(created.projectId)
        try {
            projectRepository.openProject(created.projectId)
            throw AssertionError("Expected ProjectNotFoundException")
        } catch (e: ProjectNotFoundException) {
            assertThat(e.errorInfo.code).isEqualTo(ErrorCode.PROJECT_NOT_FOUND)
        }
        }
    }

    // G — Project listing
    @Test
    fun g_projectListing() {
        runBlocking {
        projectRepository.createProject("A")
        projectRepository.createProject("B")
        val list = projectRepository.listProjects()
        assertThat(list).hasSize(2)
        assertThat(list.map { it.name }).containsAtLeast("A", "B")
        }
    }

    // H — Persistence across repository recreation
    @Test
    fun h_persistenceAcrossRestart() {
        runBlocking {
        val dbFile = File(context.getDatabasePath("avsp_persist_test.db").absolutePath)
        if (dbFile.exists()) dbFile.delete()
        val db1 = Room.databaseBuilder(context, AvspDatabase::class.java, "avsp_persist_test.db")
            .allowMainThreadQueries()
            .addMigrations(AvspDatabase.MIGRATION_1_2)
            .build()
        val repo1 = ProjectRepositoryImpl(db1.projectDao(), db1.mediaAssetDao(), storage, logger)
        val created = repo1.createProject("Persisted")
        db1.close()

        val db2 = Room.databaseBuilder(context, AvspDatabase::class.java, "avsp_persist_test.db")
            .allowMainThreadQueries()
            .addMigrations(AvspDatabase.MIGRATION_1_2)
            .build()
        val repo2 = ProjectRepositoryImpl(db2.projectDao(), db2.mediaAssetDao(), storage, logger)
        val opened = repo2.openProject(created.projectId)
        assertThat(opened.name).isEqualTo("Persisted")
        db2.close()
        }
    }

    // I — Invalid project input
    @Test
    fun i_invalidProjectInput() {
        runBlocking {
        try {
            projectRepository.createProject("   ")
            throw AssertionError("Expected InvalidInputException")
        } catch (e: InvalidInputException) {
            assertThat(e.errorInfo.code).isEqualTo(ErrorCode.INVALID_INPUT)
        }
        try {
            ProjectStatus.fromRaw("NOT_A_REAL_STATUS")
            throw AssertionError("Expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
            // expected
        }
        }
    }

    // J — Storage save
    @Test
    fun j_storageSave() {
        storage.save(StorageArea.APP_DATA, "notes/hello.txt", "hello avsp")
        assertThat(storage.exists(StorageArea.APP_DATA, "notes/hello.txt")).isTrue()
    }

    // K — Storage read
    @Test
    fun k_storageRead() {
        storage.save(StorageArea.TEMP, "tmp.bin", byteArrayOf(1, 2, 3, 4))
        val bytes = storage.readBytes(StorageArea.TEMP, "tmp.bin")
        assertThat(bytes.toList()).containsExactly(1.toByte(), 2.toByte(), 3.toByte(), 4.toByte()).inOrder()
        storage.save(StorageArea.LOGS, "boot.txt", "boot ok")
        assertThat(storage.readText(StorageArea.LOGS, "boot.txt")).isEqualTo("boot ok")
    }

    // L — Storage delete
    @Test
    fun l_storageDelete() {
        storage.save(StorageArea.GENERATED_MEDIA, "clip.txt", "x", projectId = "p1")
        assertThat(storage.delete(StorageArea.GENERATED_MEDIA, "clip.txt", "p1")).isTrue()
        assertThat(storage.exists(StorageArea.GENERATED_MEDIA, "clip.txt", "p1")).isFalse()
    }

    // M — Storage missing-file handling
    @Test
    fun m_storageMissingFileHandling() {
        try {
            storage.readText(StorageArea.APP_DATA, "missing/nope.txt")
            throw AssertionError("Expected StorageException")
        } catch (e: StorageException) {
            assertThat(e.errorInfo.code).isEqualTo(ErrorCode.STORAGE_ERROR)
            assertThat(e.errorInfo.message).contains("not found")
        }
        assertThat(storage.delete(StorageArea.APP_DATA, "missing/nope.txt")).isFalse()
    }

    // N — Settings save/read
    @Test
    fun n_settingsSaveRead() {
        runBlocking {
        val settings = AppSettings(
            defaultLanguage = ProjectLanguage.HINDI,
            defaultAspectRatio = AspectRatio.RATIO_16_9,
            defaultOutputDirectoryRef = "generated/video",
            themePreference = ThemePreference.DARK,
            loggingLevel = LogLevel.DEBUG
        )
        settingsRepository.saveSettings(settings)
        val loaded = settingsRepository.getSettings()
        assertThat(loaded.defaultLanguage).isEqualTo(ProjectLanguage.HINDI)
        assertThat(loaded.defaultAspectRatio).isEqualTo(AspectRatio.RATIO_16_9)
        assertThat(loaded.themePreference).isEqualTo(ThemePreference.DARK)
        assertThat(loaded.loggingLevel).isEqualTo(LogLevel.DEBUG)
        }
    }

    // O — Module status registration (M1 is FROZEN in accepted baseline)
    @Test
    fun o_moduleStatusRegistration() {
        runBlocking {
        moduleStatusRepository.ensureDefaults()
        moduleStatusRepository.registerOrUpdate(
            ModuleStatus(
                moduleId = AvspModules.M1_CORE_UI,
                displayName = "M1 Core/UI",
                version = "1.0.0",
                status = ModuleRunStatus.READY,
                lastUpdated = System.currentTimeMillis()
            )
        )
        val m1 = moduleStatusRepository.get(AvspModules.M1_CORE_UI)
        assertThat(m1).isNotNull()
        // Frozen protection keeps accepted M1 as FROZEN
        assertThat(m1!!.status).isEqualTo(ModuleRunStatus.FROZEN)
        }
    }

    // P — M2 initial status = FROZEN (accepted baseline)
    @Test
    fun p_m2InitialStatusFrozen() {
        runBlocking {
        moduleStatusRepository.ensureDefaults()
        val m2 = moduleStatusRepository.get(AvspModules.M2_SCRIPT_AI)
        assertThat(m2).isNotNull()
        assertThat(m2!!.status).isEqualTo(ModuleRunStatus.FROZEN)
        assertThat(m2.version).isEqualTo("1.0.0")
        }
    }

    // Q — M3 initial status = READY (M3 implemented)
    @Test
    fun q_m3InitialStatusReady() {
        runBlocking {
        moduleStatusRepository.ensureDefaults()
        val m3 = moduleStatusRepository.get(AvspModules.M3_AUDIO_TTS)
        assertThat(m3).isNotNull()
        assertThat(m3!!.status).isEqualTo(ModuleRunStatus.READY)
        assertThat(m3.version).isEqualTo("1.0.0")
        }
    }

    // R — Error model
    @Test
    fun r_errorModel() {
        val info = ErrorInfo(
            code = ErrorCode.CONFIG_ERROR,
            message = "Missing configuration reference",
            module = "settings",
            severity = ErrorSeverity.ERROR,
            details = "publishingConfigReference",
            recoverable = true
        )
        assertThat(info.code.code).isEqualTo("CONFIG_ERROR")
        assertThat(info.toLogLine()).contains("CONFIG_ERROR")
        assertThat(ErrorCode.fromRaw("PROJECT_NOT_FOUND")).isEqualTo(ErrorCode.PROJECT_NOT_FOUND)
        assertThat(ErrorCode.fromRaw("nope")).isEqualTo(ErrorCode.UNKNOWN_ERROR)
    }

    // S — Logging
    @Test
    fun s_logging() {
        runBlocking {
        logger.info("M1", "unit test info", details = "detail")
        // give coroutine append a moment
        kotlinx.coroutines.delay(200)
        val recent = logRepository.recent(10)
        assertThat(recent.any { it.message.contains("unit test info") }).isTrue()
        val redacted = SecretRedactor.redact("api_key=sk-SECRETVALUE123")
        assertThat(redacted).doesNotContain("sk-SECRETVALUE123")
        assertThat(redacted).contains("REDACTED")
        }
    }

    // T — Repository layer
    @Test
    fun t_repositoryLayer() {
        runBlocking {
        val p = projectRepository.createProject("Repo Layer")
        projectRepository.renameProject(p.projectId, "Renamed")
        assertThat(projectRepository.openProject(p.projectId).name).isEqualTo("Renamed")
        settingsRepository.updateTheme(ThemePreference.LIGHT)
        assertThat(settingsRepository.getSettings().themePreference).isEqualTo(ThemePreference.LIGHT)
        moduleStatusRepository.ensureDefaults()
        assertThat(moduleStatusRepository.getAll().size).isAtLeast(9)
        logRepository.append(LogLevel.WARNING, "M1", "repo check")
        assertThat(logRepository.recent(5)).isNotEmpty()
        }
    }

    // U — UI state model
    @Test
    fun u_uiStateModel() {
        val idle: UiState<String> = UiState.Idle
        val loading: UiState<String> = UiState.Loading
        val success = UiState.Success("ok")
        val error = UiState.Error("boom", code = "X")
        assertThat(idle.isIdle).isTrue()
        assertThat(loading.isLoading).isTrue()
        assertThat(success.isSuccess).isTrue()
        assertThat(success.getOrNull()).isEqualTo("ok")
        assertThat(error.isError).isTrue()
    }

    // V — Migration test (non-destructive 1→2 adding media_assets)
    @Test
    fun v_migrationTest() {
        val dbName = "migration_test.db"
        context.deleteDatabase(dbName)
        val config = androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(dbName)
            .callback(object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(1) {
                override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS projects (
                            projectId TEXT NOT NULL PRIMARY KEY,
                            name TEXT NOT NULL,
                            description TEXT NOT NULL,
                            createdAt INTEGER NOT NULL,
                            updatedAt INTEGER NOT NULL,
                            status TEXT NOT NULL,
                            duration INTEGER,
                            aspectRatio TEXT NOT NULL,
                            language TEXT NOT NULL,
                            outputPath TEXT,
                            metadataJson TEXT NOT NULL
                        )
                        """.trimIndent()
                    )
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS module_status (
                            moduleId TEXT NOT NULL PRIMARY KEY,
                            displayName TEXT NOT NULL,
                            version TEXT NOT NULL,
                            status TEXT NOT NULL,
                            lastUpdated INTEGER NOT NULL,
                            error TEXT
                        )
                        """.trimIndent()
                    )
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS settings (
                            `key` TEXT NOT NULL PRIMARY KEY,
                            value TEXT NOT NULL
                        )
                        """.trimIndent()
                    )
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS logs (
                            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            timestamp INTEGER NOT NULL,
                            level TEXT NOT NULL,
                            module TEXT NOT NULL,
                            message TEXT NOT NULL,
                            details TEXT,
                            projectId TEXT
                        )
                        """.trimIndent()
                    )
                    db.execSQL(
                        "INSERT INTO projects VALUES('prj_keep','Keep','',1,1,'DRAFT',NULL,'9:16','en',NULL,'{}')"
                    )
                }

                override fun onUpgrade(
                    db: androidx.sqlite.db.SupportSQLiteDatabase,
                    oldVersion: Int,
                    newVersion: Int
                ) = Unit
            })
            .build()
        val openHelper = FrameworkSQLiteOpenHelperFactory().create(config)
        val db = openHelper.writableDatabase
        try {
            AvspDatabase.MIGRATION_1_2.migrate(db)
            db.query(
                "SELECT name FROM sqlite_master WHERE type='table' AND name='media_assets'"
            ).use { cursor ->
                assertThat(cursor.moveToFirst()).isTrue()
            }
            db.query("SELECT name FROM projects WHERE projectId='prj_keep'").use { cursor ->
                assertThat(cursor.moveToFirst()).isTrue()
                assertThat(cursor.getString(0)).isEqualTo("Keep")
            }
        } finally {
            openHelper.close()
        }
    }

    // W — No hard-coded secrets
    @Test
    fun w_noHardCodedSecrets() {
        // Settings UI / secure store must not expose secrets; verify config state abstraction.
        assertThat(secureStore.hasSecret(SecureConfigKeys.AI_API)).isFalse()
        secureStore.putSecret(SecureConfigKeys.AI_API, "test-secret-value")
        assertThat(secureStore.hasSecret(SecureConfigKeys.AI_API)).isTrue()
        assertThat(secureStore.configState(SecureConfigKeys.AI_API).name).isEqualTo("CONFIGURED")
        // Ensure source contracts do not embed common secret literals in AppSettings defaults.
        val defaults = AppSettings()
        assertThat(defaults.aiConfigReference).isNull()
        assertThat(defaults.publishingConfigReference).isNull()
        secureStore.clearSecret(SecureConfigKeys.AI_API)
    }

    // X — Navigation/state smoke test
    @Test
    fun x_navigationStateSmokeTest() {
        val routes = AvspDestination.bottomBar.map { it.route }
        assertThat(routes).containsExactly(
            "home", "projects", "media", "modules", "settings", "logs"
        ).inOrder()
        assertThat(AvspDestination.ProjectDetail.createRoute("prj_abc")).isEqualTo("project/prj_abc")
        assertThat(AvspDestination.Home.label).isEqualTo("Home")
        // Controlled status enums remain closed sets
        assertThat(ProjectStatus.entries.map { it.name })
            .containsExactly("DRAFT", "PROCESSING", "READY", "FAILED", "COMPLETED")
            .inOrder()
        assertThat(ModuleRunStatus.entries).hasSize(7)
        assertThat(ModuleRunStatus.entries.map { it.name }).contains("FROZEN")
    }
}
