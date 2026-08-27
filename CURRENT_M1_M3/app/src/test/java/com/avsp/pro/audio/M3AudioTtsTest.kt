package com.avsp.pro.audio

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.avsp.pro.audio.contract.AssignmentScope
import com.avsp.pro.audio.contract.AudioGenerationRequest
import com.avsp.pro.audio.contract.AudioSegmentStatus
import com.avsp.pro.audio.contract.VoiceConfiguration
import com.avsp.pro.audio.contract.VoiceMode
import com.avsp.pro.audio.contract.VoiceSettings
import com.avsp.pro.audio.engine.DefaultTtsEngineRegistry
import com.avsp.pro.audio.engine.MockTtsEngine
import com.avsp.pro.audio.engine.TtsSynthesisRequest
import com.avsp.pro.audio.engine.VoiceCloneTtsEngine
import com.avsp.pro.audio.error.AudioErrorCode
import com.avsp.pro.audio.error.AudioException
import com.avsp.pro.audio.integration.AudioToVideoContract
import com.avsp.pro.audio.integration.M3ScriptAudioPlan
import com.avsp.pro.audio.language.AudioLanguageRegistry
import com.avsp.pro.audio.repository.AudioRepositoryImpl
import com.avsp.pro.audio.validation.AudioValidator
import com.avsp.pro.audio.voice.LocalVoiceCatalog
import com.avsp.pro.audio.voice.VoiceCloneProfileStore
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
    private lateinit var voiceCloneStore: VoiceCloneProfileStore

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
        voiceCloneStore = VoiceCloneProfileStore(storage)
        val ttsRegistry = DefaultTtsEngineRegistry(
            context = context,
            secureConfigStore = secure,
            storage = storage,
            voiceCloneProfileStore = voiceCloneStore,
            mock = mockTts,
            androidEngineFactory = { null }
        )
        audioRepository = AudioRepositoryImpl(
            storage = storage,
            scriptRepository = scriptRepository,
            ttsRegistry = ttsRegistry,
            voiceCatalog = LocalVoiceCatalog(context),
            voiceCloneProfileStore = voiceCloneStore,
            logger = logger
        )
        moduleStatusRepository = ModuleStatusRepositoryImpl(database.moduleStatusDao())
    }

    @After
    fun tearDown() {
        database.close()
    }

    private suspend fun seedScript(
        projectId: String = "prj_m3",
        language: String = "en"
    ) = scriptRepository.generate(
        ScriptGenerationRequest(
            projectId = projectId,
            topic = "Kharagpur update",
            languageCode = language,
            duration = DurationRequest.ShortForm
        )
    )

    private suspend fun generateWithMock(projectId: String) =
        audioRepository.generate(
            AudioGenerationRequest(
                projectId = projectId,
                preferredProviderId = MockTtsEngine.PROVIDER_ID,
                voiceConfiguration = VoiceConfiguration(
                    projectId = projectId,
                    voiceMode = VoiceMode.SIMPLE_LOCAL,
                    assignmentScope = AssignmentScope.ENTIRE_PROJECT,
                    defaultLanguage = "en",
                    defaultProviderId = MockTtsEngine.PROVIDER_ID
                )
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
        val plan = M3ScriptAudioPlan.fromScript(script)
        val audio = generateWithMock("prj_map")
        assertThat(audio.validation.isValid).isTrue()
        assertThat(audio.provider).isEqualTo(MockTtsEngine.PROVIDER_ID)
        assertThat(audio.segments).hasSize(plan.items.size)
        assertThat(audio.segments.all { it.status == AudioSegmentStatus.READY }).isTrue()
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
        val audio = generateWithMock("prj_time")
        val validation = AudioValidator.validatePackage(audio)
        assertThat(validation.isValid).isTrue()
        assertThat(audio.totalDurationMs).isEqualTo(audio.segments.last().endMs)
        var cursor = 0L
        audio.segments.forEach { seg ->
            assertThat(seg.startMs).isEqualTo(cursor)
            assertThat(seg.endMs).isEqualTo(seg.startMs + seg.durationMs)
            cursor = seg.endMs
        }
    }

    @Test
    fun persistenceAndFileReferences() = runBlocking {
        seedScript("prj_persist_a")
        val generated = generateWithMock("prj_persist_a")
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
        val first = generateWithMock("prj_regen")
        val second = audioRepository.regenerate("prj_regen", MockTtsEngine.PROVIDER_ID)
        assertThat(second.audioPackageId).isNotEqualTo(first.audioPackageId)
        assertThat(second.validation.isValid).isTrue()
    }

    @Test
    fun singleSegmentRegenerationKeepsOthers() = runBlocking {
        seedScript("prj_seg_regen")
        val first = generateWithMock("prj_seg_regen")
        val target = first.segments.first { it.role.name == "SCENE" || it.role.name == "BODY" }
        val otherPaths = first.segments
            .filter { it.segmentId != target.segmentId }
            .associate { it.segmentId to storage.readBytes(StorageArea.PROJECT_DATA, it.relativeAudioPath, "prj_seg_regen") }
        val updated = audioRepository.regenerateSegment("prj_seg_regen", target.segmentId)
        otherPaths.forEach { (id, bytes) ->
            val seg = updated.segments.first { it.segmentId == id }
            assertThat(storage.readBytes(StorageArea.PROJECT_DATA, seg.relativeAudioPath, "prj_seg_regen"))
                .isEqualTo(bytes)
        }
        val regenerated = updated.segments.first { it.segmentId == target.segmentId }
        assertThat(regenerated.status).isEqualTo(AudioSegmentStatus.READY)
    }

    @Test
    fun narrationChangeMarksStale() = runBlocking {
        val script = seedScript("prj_stale")
        val audio = generateWithMock("prj_stale")
        val editedScene = script.scenes[1]
        val edited = script.copy(
            scenes = script.scenes.map {
                if (it.sceneId == editedScene.sceneId) {
                    it.copy(narration = it.narration + " UPDATED")
                } else it
            }
        )
        scriptRepository.updateEdited(edited)
        val refreshed = audioRepository.refreshStaleState("prj_stale")
        assertThat(refreshed).isNotNull()
        val staleSeg = refreshed!!.segments.first { it.sceneId == editedScene.sceneId }
        assertThat(staleSeg.status).isEqualTo(AudioSegmentStatus.STALE)
    }

    @Test
    fun voiceCloneNotConfigured() = runBlocking {
        seedScript("prj_clone")
        try {
            audioRepository.generate(
                AudioGenerationRequest(
                    projectId = "prj_clone",
                    voiceConfiguration = VoiceConfiguration(
                        projectId = "prj_clone",
                        voiceMode = VoiceMode.MY_VOICE_CLONE,
                        defaultProviderId = VoiceCloneTtsEngine.PROVIDER_ID
                    )
                )
            )
            throw AssertionError("Expected VOICE_CLONE_NOT_CONFIGURED")
        } catch (e: AudioException) {
            assertThat(e.errorCode).isEqualTo(AudioErrorCode.VOICE_CLONE_NOT_CONFIGURED)
            assertThat(e.message).contains("not configured")
        }
    }

    @Test
    fun providerAbstractionDefaultsToMock() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val secure = EncryptedSecureConfigStore(context)
        val registry = DefaultTtsEngineRegistry(
            context,
            secure,
            FileAvspStorage(context),
            VoiceCloneProfileStore(FileAvspStorage(context)),
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
        val secure = EncryptedSecureConfigStore(context)
        val registry = DefaultTtsEngineRegistry(
            context,
            secure,
            FileAvspStorage(context),
            VoiceCloneProfileStore(FileAvspStorage(context)),
            mock = unavailable,
            androidEngineFactory = { null }
        )
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
        val audio = generateWithMock("prj_play")
        val abs = audioRepository.resolveAbsolutePath("prj_play", audio.segments.first().relativeAudioPath)
        assertThat(abs).isNotEmpty()
        assertThat(java.io.File(abs).exists()).isTrue()
    }

    @Test
    fun m3ToM4Handoff() = runBlocking {
        seedScript("prj_m4")
        val audio = generateWithMock("prj_m4")
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
    }

    @Test
    fun bengaliMockAudio() = runBlocking {
        scriptRepository.generate(
            ScriptGenerationRequest("prj_bn", "আবহাওয়া", "bn", DurationRequest.ShortForm)
        )
        val audio = audioRepository.generate(
            AudioGenerationRequest(
                projectId = "prj_bn",
                preferredProviderId = MockTtsEngine.PROVIDER_ID,
                voiceConfiguration = VoiceConfiguration(
                    projectId = "prj_bn",
                    defaultLanguage = "bn",
                    defaultProviderId = MockTtsEngine.PROVIDER_ID
                )
            )
        )
        assertThat(audio.language).isEqualTo("bn")
        assertThat(audio.validation.isValid).isTrue()
    }

    @Test
    fun hindiMockAudio() = runBlocking {
        scriptRepository.generate(
            ScriptGenerationRequest("prj_hi", "मौसम", "hi", DurationRequest.ShortForm)
        )
        val audio = audioRepository.generate(
            AudioGenerationRequest(
                projectId = "prj_hi",
                preferredProviderId = MockTtsEngine.PROVIDER_ID,
                voiceConfiguration = VoiceConfiguration(
                    projectId = "prj_hi",
                    defaultLanguage = "hi",
                    defaultProviderId = MockTtsEngine.PROVIDER_ID
                )
            )
        )
        assertThat(audio.language).isEqualTo("hi")
        assertThat(audio.segments.all { it.durationMs > 0 }).isTrue()
    }
}
