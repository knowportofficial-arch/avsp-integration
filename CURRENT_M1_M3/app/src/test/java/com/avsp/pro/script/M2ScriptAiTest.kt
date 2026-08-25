package com.avsp.pro.script

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.avsp.pro.core.error.InvalidInputException
import com.avsp.pro.core.module.AvspModules
import com.avsp.pro.core.module.ModuleRunStatus
import com.avsp.pro.database.AvspDatabase
import com.avsp.pro.logs.AvspLoggerImpl
import com.avsp.pro.repository.LogRepositoryImpl
import com.avsp.pro.repository.ModuleStatusRepositoryImpl
import com.avsp.pro.script.contract.DurationRequest
import com.avsp.pro.script.contract.ScriptGenerationRequest
import com.avsp.pro.script.contract.ValidationStatus
import com.avsp.pro.script.generator.DefaultScriptGeneratorRegistry
import com.avsp.pro.script.generator.MockScriptGenerator
import com.avsp.pro.script.integration.ScriptToTtsContract
import com.avsp.pro.script.language.ScriptLanguageRegistry
import com.avsp.pro.script.repository.ScriptEditHelpers
import com.avsp.pro.script.repository.ScriptRepositoryImpl
import com.avsp.pro.script.validation.ScriptValidator
import com.avsp.pro.settings.EncryptedSecureConfigStore
import com.avsp.pro.storage.FileAvspStorage
import com.google.common.truth.Truth.assertThat
import com.google.gson.Gson
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.math.abs

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class M2ScriptAiTest {

    private lateinit var database: AvspDatabase
    private lateinit var storage: FileAvspStorage
    private lateinit var scriptRepository: ScriptRepositoryImpl
    private lateinit var moduleStatusRepository: ModuleStatusRepositoryImpl
    private lateinit var mockGenerator: MockScriptGenerator

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        database = Room.inMemoryDatabaseBuilder(context, AvspDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        storage = FileAvspStorage(context)
        val logger = AvspLoggerImpl(LogRepositoryImpl(database.logDao()))
        val secure = EncryptedSecureConfigStore(context)
        mockGenerator = MockScriptGenerator()
        val registry = DefaultScriptGeneratorRegistry(secure, mockGenerator)
        scriptRepository = ScriptRepositoryImpl(storage, registry, logger)
        moduleStatusRepository = ModuleStatusRepositoryImpl(database.moduleStatusDao())
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun validScriptGeneration() = runBlocking {
        val script = scriptRepository.generate(
            ScriptGenerationRequest(
                projectId = "prj_m2_1",
                topic = "Kharagpur weather",
                languageCode = "en",
                duration = DurationRequest.MediumForm
            )
        )
        assertThat(script.validation.isValid).isTrue()
        assertThat(script.scenes).isNotEmpty()
        assertThat(script.title).isNotEmpty()
        assertThat(script.hook).isNotEmpty()
        assertThat(script.metadata.generatorMode).isEqualTo("MOCK")
    }

    @Test
    fun emptyTopicRejection() = runBlocking {
        try {
            scriptRepository.generate(
                ScriptGenerationRequest(
                    projectId = "prj_m2_2",
                    topic = "   ",
                    languageCode = "en",
                    duration = DurationRequest.ShortForm
                )
            )
            throw AssertionError("Expected InvalidInputException")
        } catch (e: InvalidInputException) {
            assertThat(e.errorInfo.message).contains("topic")
        }
    }

    @Test
    fun invalidDurationRejection() {
        val result = ScriptValidator.validateRequest(
            ScriptGenerationRequest(
                projectId = "prj_m2_3",
                topic = "Gold price",
                languageCode = "en",
                duration = DurationRequest.Explicit(500L)
            )
        )
        assertThat(result.isValid).isFalse()
        assertThat(result.errors.joinToString()).contains("duration")
    }

    @Test
    fun languageValidation() {
        assertThat(ScriptLanguageRegistry.isSupported("en")).isTrue()
        assertThat(ScriptLanguageRegistry.isSupported("bn")).isTrue()
        assertThat(ScriptLanguageRegistry.isSupported("hi")).isTrue()
        assertThat(ScriptLanguageRegistry.isSupported("xx")).isFalse()
        val bad = ScriptValidator.validateRequest(
            ScriptGenerationRequest(
                projectId = "prj",
                topic = "Test",
                languageCode = "zz",
                duration = DurationRequest.ShortForm
            )
        )
        assertThat(bad.isValid).isFalse()
    }

    @Test
    fun sceneOrdering() = runBlocking {
        val script = mockGenerator.generate(
            ScriptGenerationRequest(
                projectId = "prj_ord",
                topic = "Ordering",
                languageCode = "en",
                duration = DurationRequest.LongForm
            )
        )
        val orders = script.scenes.map { it.order }
        assertThat(orders).isEqualTo(orders.sorted())
        assertThat(orders.distinct()).hasSize(orders.size)
    }

    @Test
    fun durationCalculationWithinTolerance() = runBlocking {
        val target = 60_000L
        val script = mockGenerator.generate(
            ScriptGenerationRequest(
                projectId = "prj_dur",
                topic = "Duration check",
                languageCode = "bn",
                duration = DurationRequest.Explicit(target),
                durationToleranceRatio = 0.15
            )
        )
        val sum = script.scenes.sumOf { it.durationMs }
        assertThat(script.estimatedDurationMs).isEqualTo(sum)
        val delta = abs(sum - target).toDouble() / target
        assertThat(delta).isAtMost(0.15)
        assertThat(script.validation.isValid).isTrue()
    }

    @Test
    fun targetDurationToleranceFailure() {
        // Build a package that intentionally misses tolerance.
        val base = runBlocking {
            mockGenerator.generate(
                ScriptGenerationRequest(
                    projectId = "prj_tol",
                    topic = "Tolerance",
                    languageCode = "en",
                    duration = DurationRequest.ShortForm
                )
            )
        }
        val bad = base.copy(
            targetDurationMs = 60_000L,
            estimatedDurationMs = base.scenes.sumOf { it.durationMs }
        )
        val validation = ScriptValidator.validatePackage(bad, toleranceRatio = 0.10)
        assertThat(validation.isValid).isFalse()
        assertThat(validation.status).isEqualTo(ValidationStatus.INVALID)
    }

    @Test
    fun persistenceRoundTrip() = runBlocking {
        val generated = scriptRepository.generate(
            ScriptGenerationRequest(
                projectId = "prj_persist",
                topic = "Persist me",
                languageCode = "en",
                duration = DurationRequest.MediumForm
            )
        )
        val loaded = scriptRepository.load("prj_persist")
        assertThat(loaded).isNotNull()
        assertThat(loaded!!.scriptId).isEqualTo(generated.scriptId)
        assertThat(loaded.topic).isEqualTo("Persist me")
        val refs = scriptRepository.listReferences("prj_persist")
        assertThat(refs).isNotEmpty()
    }

    @Test
    fun scriptEditing() = runBlocking {
        val generated = scriptRepository.generate(
            ScriptGenerationRequest(
                projectId = "prj_edit",
                topic = "Editable",
                languageCode = "en",
                duration = DurationRequest.ShortForm
            )
        )
        val sceneId = generated.scenes.first().sceneId
        val edited = ScriptEditHelpers.updateSceneNarration(
            ScriptEditHelpers.updateTitle(generated, "New Title"),
            sceneId,
            "Edited narration line."
        )
        val saved = scriptRepository.updateEdited(edited)
        assertThat(saved.title).isEqualTo("New Title")
        assertThat(saved.scenes.first { it.sceneId == sceneId }.narration)
            .isEqualTo("Edited narration line.")
    }

    @Test
    fun serializationDeserialization() = runBlocking {
        val generated = mockGenerator.generate(
            ScriptGenerationRequest(
                projectId = "prj_ser",
                topic = "Serialize",
                languageCode = "hi",
                duration = DurationRequest.MediumForm
            )
        )
        val gson = Gson()
        val json = gson.toJson(generated)
        val restored = gson.fromJson(json, com.avsp.pro.script.contract.ScriptPackage::class.java)
        assertThat(restored.scriptId).isEqualTo(generated.scriptId)
        assertThat(restored.scenes).hasSize(generated.scenes.size)
        assertThat(restored.language).isEqualTo("hi")
    }

    @Test
    fun mockGeneratorIsDeterministicStructure() = runBlocking {
        val a = mockGenerator.generate(
            ScriptGenerationRequest("p", "Topic A", "en", DurationRequest.MediumForm)
        )
        val b = mockGenerator.generate(
            ScriptGenerationRequest("p", "Topic A", "en", DurationRequest.MediumForm)
        )
        assertThat(a.scenes.size).isEqualTo(b.scenes.size)
        assertThat(a.metadata.generatorId).isEqualTo(MockScriptGenerator.PROVIDER_ID)
        assertThat(a.metadata.generatorMode).isEqualTo("MOCK")
    }

    @Test
    fun providerAbstractionResolvesMockWhenNotConfigured() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val registry = DefaultScriptGeneratorRegistry(EncryptedSecureConfigStore(context))
        assertThat(registry.isRemoteConfigured()).isFalse()
        assertThat(registry.resolve().providerId).isEqualTo(MockScriptGenerator.PROVIDER_ID)
        assertThat(registry.available()).isNotEmpty()
    }

    @Test
    fun m2ToM3NarrationHandoff() = runBlocking {
        val script = mockGenerator.generate(
            ScriptGenerationRequest("p", "Handoff", "en", DurationRequest.ShortForm)
        )
        val handoff = ScriptToTtsContract.fromPackage(script)
        assertThat(handoff.segments).hasSize(script.scenes.size)
        assertThat(handoff.language).isEqualTo("en")
        assertThat(handoff.segments.first().narration).isNotEmpty()
    }

    @Test
    fun m2ModuleStatusFrozen() = runBlocking {
        moduleStatusRepository.ensureDefaults()
        val m2 = moduleStatusRepository.get(AvspModules.M2_SCRIPT_AI)
        assertThat(m2).isNotNull()
        assertThat(m2!!.status).isEqualTo(ModuleRunStatus.FROZEN)
        assertThat(m2.version).isEqualTo("1.0.0")
        assertThat(moduleStatusRepository.get(AvspModules.M3_AUDIO_TTS)!!.status)
            .isEqualTo(ModuleRunStatus.READY)
    }

    @Test
    fun bengaliGeneration() = runBlocking {
        val script = mockGenerator.generate(
            ScriptGenerationRequest("p", "আবহাওয়া", "bn", DurationRequest.ShortForm)
        )
        assertThat(script.language).isEqualTo("bn")
        assertThat(script.validation.isValid).isTrue()
        assertThat(script.hook).isNotEmpty()
    }
}
