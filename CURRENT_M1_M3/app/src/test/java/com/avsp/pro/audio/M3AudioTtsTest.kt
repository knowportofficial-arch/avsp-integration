package com.avsp.pro.audio

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.avsp.pro.audio.contract.AudioGenerationRequest
import com.avsp.pro.audio.contract.VoiceSettings
import com.avsp.pro.audio.engine.DefaultTtsEngineRegistry
import com.avsp.pro.audio.engine.MockTtsEngine
import com.avsp.pro.audio.engine.TtsSynthesisRequest
import com.avsp.pro.audio.error.AudioErrorCode
import com.avsp.pro.audio.error.AudioException
import com.avsp.pro.audio.integration.AudioToVideoContract
import com.avsp.pro.audio.language.AudioLanguageRegistry
import com.avsp.pro.audio.repository.AudioRepositoryImpl
import com.avsp.pro.audio.validation.AudioValidator
import com.avsp.pro.audio.wav.WavEncoder
import com.avsp.pro.core.module.AvspModules
import com.avsp.pro.core.module.ModuleRunStatus
import com.avsp.pro.database.AvspDatabase
import com.avsp.pro.logs.AvspLoggerImpl
import com.avsp.pro.repository.LogRepositoryImpl
import com.avsp.pro.repository.ModuleStatusRepositoryImpl
import com.avsp.pro.script.contract.DurationRequest
import com.avsp.pro.script.contract.ScriptGenerationRequest
import com.avsp.pro.script.generator.DefaultScriptGeneratorRegistry
import com.avsp.pro.script.generator.MockScriptGenerator
import com.avsp.pro.script.integration.ScriptToTtsContract
import com.avsp.pro.script.repository.ScriptRepositoryImpl
import com.avsp.pro.settings.EncryptedSecureConfigStore
import com.avsp.pro.storage.FileAvspStorage
import com.avsp.pro.storage.StorageArea
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class M3AudioTtsTest {

    private lateinit var database: AvspDatabase
    private lateinit var storage: FileAvspStorage
    private lateinit var scriptRepository: ScriptRepositoryImpl
    private lateinit var audioRepository: AudioRepositoryImpl
    private lateinit var moduleStatusRepository: ModuleStatusRepositoryImpl
    private lateinit var mockTts: MockTtsEngine

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        database = Room.inMemoryDatabaseBuilder(context, AvspDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        storage = FileAvspStorage(context)
        val logger = AvspLoggerImpl(LogRepositoryImpl(database.logDao()))
        val secure = EncryptedSecureConfigStore(context)
        scriptRepository = ScriptRepositoryImpl(
            storage,
            DefaultScriptGeneratorRegistry(secure, MockScriptGenerator()),
            logger
        )
        mockTts = MockTtsEngine()
        val ttsRegistry = DefaultTtsEngineRegistry(
            context = context,
            secureConfigStore = secure,
            mock = mockTts,
            androidEngineFactory = { null } // keep tests deterministic without Android TTS
        )
        audioRepository = AudioRepositoryImpl(storage, scriptRepository, ttsRegistry, logger)
        moduleStatusRepository = ModuleStatusRepositoryImpl(database.moduleStatusDao())
    }

    @After
    fun tearDown() {
        database.close()
    }

    private suspend fun seedScript(projectId: String = "prj_m3") =
        scriptRepository.generate(
            ScriptGenerationRequest(
                projectId = projectId,
                topic = "Kharagpur update",
                languageCode = "en",
                duration = DurationRequest.ShortForm
            )
        )

    @Test
    fun scriptToTtsContractConsumption() = runBlocking {
        val script = seedScript()
        val handoff = ScriptToTtsContract.fromPackage(script)
        assertThat(handoff.segments).isNotEmpty()
        val loaded = audioRepository.loadScriptHandoff(script.projectId)
        assertThat(loaded).isNotNull()
        assertThat(loaded!!.scriptId).isEqualTo(script.scriptId)
    }

    @Test
    fun emptyScriptRejection() = runBlocking {
        try {
            audioRepository.generate(AudioGenerationRequest("prj_missing"))
            throw AssertionError("Expected SCRIPT_REQUIRED")
        } catch (e: AudioException) {
            assertThat(e.errorCode).isEqualTo(AudioErrorCode.SCRIPT_REQUIRED)
        }
    }

    @Test
    fun validScriptConversionAndSceneMapping() = runBlocking {
        val script = seedScript("prj_map")
        val audio = audioRepository.generate(
            AudioGenerationRequest("prj_map", preferredProviderId = MockTtsEngine.PROVIDER_ID)
        )
        assertThat(audio.validation.isValid).isTrue()
        assertThat(audio.provider).isEqualTo(MockTtsEngine.PROVIDER_ID)
        assertThat(audio.segments).hasSize(script.scenes.size)
        assertThat(audio.segments.map { it.sceneId }).isEqualTo(script.scenes.sortedBy { it.order }.map { it.sceneId })
        assertThat(audio.segments.map { it.order }).isEqualTo(audio.segments.map { it.order }.sorted())
    }

    @Test
    fun languageHandlingAndUnsupportedLanguage() = runBlocking {
        assertThat(AudioLanguageRegistry.isSupported("en")).isTrue()
        assertThat(AudioLanguageRegistry.isSupported("bn")).isTrue()
        assertThat(AudioLanguageRegistry.isSupported("hi")).isTrue()
        try {
            mockTts.synthesize(
                TtsSynthesisRequest("hello", "zz", VoiceSettings(language = "zz"), 1000L)
            )
            throw AssertionError("Expected TTS_LANGUAGE_UNAVAILABLE")
        } catch (e: AudioException) {
            assertThat(e.errorCode).isEqualTo(AudioErrorCode.TTS_LANGUAGE_UNAVAILABLE)
        }
    }

    @Test
    fun mockTtsGenerationProducesWav() = runBlocking {
        val result = mockTts.synthesize(
            TtsSynthesisRequest("hello world", "en", VoiceSettings(language = "en"), 1000L)
        )
        assertThat(result.providerId).isEqualTo("mock")
        assertThat(result.mimeType).isEqualTo("audio/wav")
        assertThat(result.audioBytes.copyOfRange(0, 4).toString(Charsets.US_ASCII)).isEqualTo("RIFF")
        assertThat(result.durationMs).isAtLeast(250L)
    }

    @Test
    fun audioPackageValidationAndTiming() = runBlocking {
        seedScript("prj_time")
        val audio = audioRepository.generate(AudioGenerationRequest("prj_time", "mock"))
        val validation = AudioValidator.validatePackage(audio)
        assertThat(validation.isValid).isTrue()
        assertThat(audio.totalDurationMs).isEqualTo(audio.segments.last().endMs)
        var cursor = 0L
        audio.segments.forEach { seg ->
            assertThat(seg.startMs).isEqualTo(cursor)
            assertThat(seg.endMs).isEqualTo(seg.startMs + seg.durationMs)
            cursor = seg.endMs
            // pauseAfter is applied between segments in generator by advancing cursor;
            // package total uses last endMs.
        }
    }

    @Test
    fun persistenceAndFileReferences() = runBlocking {
        seedScript("prj_persist_a")
        val generated = audioRepository.generate(AudioGenerationRequest("prj_persist_a", "mock"))
        val loaded = audioRepository.load("prj_persist_a")
        assertThat(loaded).isNotNull()
        assertThat(loaded!!.audioPackageId).isEqualTo(generated.audioPackageId)
        generated.segments.forEach { seg ->
            assertThat(
                storage.exists(StorageArea.PROJECT_DATA, seg.relativeAudioPath, "prj_persist_a")
            ).isTrue()
        }
    }

    @Test
    fun regenerationCreatesNewPackage() = runBlocking {
        seedScript("prj_regen")
        val first = audioRepository.generate(AudioGenerationRequest("prj_regen", "mock"))
        val second = audioRepository.regenerate("prj_regen", "mock")
        assertThat(second.audioPackageId).isNotEqualTo(first.audioPackageId)
        assertThat(second.validation.isValid).isTrue()
    }

    @Test
    fun providerAbstractionDefaultsToMock() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val registry = DefaultTtsEngineRegistry(
            context,
            EncryptedSecureConfigStore(context),
            mockTts,
            androidEngineFactory = { null }
        )
        assertThat(registry.resolve().providerId).isEqualTo(MockTtsEngine.PROVIDER_ID)
        assertThat(registry.available().map { it.providerId }).contains(MockTtsEngine.PROVIDER_ID)
    }

    @Test
    fun ttsUnavailableHandling() {
        val unavailable = object : com.avsp.pro.audio.engine.TtsEngine by mockTts {
            override val providerId = "broken"
            override fun isAvailable() = false
        }
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val registry = DefaultTtsEngineRegistry(
            context,
            EncryptedSecureConfigStore(context),
            mock = unavailable,
            androidEngineFactory = { null }
        )
        // resolve falls back — but available list may be empty of usable engines except we used broken as mock.
        // requireProvider should fail
        try {
            registry.requireProvider("broken")
            throw AssertionError("Expected unavailable")
        } catch (e: AudioException) {
            assertThat(e.errorCode).isEqualTo(AudioErrorCode.TTS_PROVIDER_UNAVAILABLE)
        }
    }

    @Test
    fun playbackPathResolves() = runBlocking {
        seedScript("prj_play")
        val audio = audioRepository.generate(AudioGenerationRequest("prj_play", "mock"))
        val abs = audioRepository.resolveAbsolutePath("prj_play", audio.segments.first().relativeAudioPath)
        assertThat(abs).isNotEmpty()
        assertThat(java.io.File(abs).exists()).isTrue()
    }

    @Test
    fun m3ToM4Handoff() = runBlocking {
        seedScript("prj_m4")
        val audio = audioRepository.generate(AudioGenerationRequest("prj_m4", "mock"))
        val handoff = AudioToVideoContract.fromPackage(audio)
        assertThat(handoff.segments).hasSize(audio.segments.size)
        assertThat(handoff.totalDurationMs).isEqualTo(audio.totalDurationMs)
    }

    @Test
    fun wavFormatConstants() {
        assertThat(WavEncoder.SAMPLE_RATE).isEqualTo(16_000)
        assertThat(WavEncoder.CHANNELS).isEqualTo(1)
        assertThat(WavEncoder.BITS_PER_SAMPLE).isEqualTo(16)
    }

    @Test
    fun m3ModuleStatusReadyAndBaselineFrozen() = runBlocking {
        moduleStatusRepository.ensureDefaults()
        assertThat(moduleStatusRepository.get(AvspModules.M1_CORE_UI)!!.status)
            .isEqualTo(ModuleRunStatus.FROZEN)
        assertThat(moduleStatusRepository.get(AvspModules.M2_SCRIPT_AI)!!.status)
            .isEqualTo(ModuleRunStatus.FROZEN)
        assertThat(moduleStatusRepository.get(AvspModules.M3_AUDIO_TTS)!!.status)
            .isEqualTo(ModuleRunStatus.READY)
        AvspModules.FROZEN_MODULE_IDS.filter { it.startsWith("M") && it != "M1" && it != "M2" }.forEach { id ->
            // M4/M5/M8/M9 still frozen (M6/M7 are live after Pro integration)
            if (id in listOf("M4", "M5", "M8", "M9")) {
                assertThat(moduleStatusRepository.get(id)!!.status).isEqualTo(ModuleRunStatus.FROZEN)
            }
        }
        assertThat(moduleStatusRepository.get(AvspModules.M6_CAMERA)!!.status)
            .isEqualTo(ModuleRunStatus.READY)
        assertThat(moduleStatusRepository.get(AvspModules.M7_DATASET_VISION)!!.status)
            .isEqualTo(ModuleRunStatus.READY)
    }

    @Test
    fun bengaliMockAudio() = runBlocking {
        scriptRepository.generate(
            ScriptGenerationRequest("prj_bn", "আবহাওয়া", "bn", DurationRequest.ShortForm)
        )
        val audio = audioRepository.generate(AudioGenerationRequest("prj_bn", "mock"))
        assertThat(audio.language).isEqualTo("bn")
        assertThat(audio.validation.isValid).isTrue()
    }
}
