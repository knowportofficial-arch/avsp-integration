package com.avsp.pro.avsp20.integration

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.avsp.pro.audio.contract.AudioGenerationRequest
import com.avsp.pro.audio.engine.DefaultTtsEngineRegistry
import com.avsp.pro.audio.engine.MockTtsEngine
import com.avsp.pro.audio.repository.AudioRepositoryImpl
import com.avsp.pro.avsp20.adapters.M2ScriptContractAdapter
import com.avsp.pro.avsp20.adapters.M3AudioContractAdapter
import com.avsp.pro.database.AvspDatabase
import com.avsp.pro.logs.AvspLoggerImpl
import com.avsp.pro.repository.LogRepositoryImpl
import com.avsp.pro.script.contract.DurationRequest
import com.avsp.pro.script.contract.ScriptGenerationRequest
import com.avsp.pro.script.generator.DefaultScriptGeneratorRegistry
import com.avsp.pro.script.generator.MockScriptGenerator
import com.avsp.pro.script.repository.ScriptRepositoryImpl
import com.avsp.pro.settings.EncryptedSecureConfigStore
import com.avsp.pro.storage.FileAvspStorage
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class S18RealM2M3AdapterChainTest {

    private lateinit var database: AvspDatabase
    private lateinit var scriptRepository: ScriptRepositoryImpl
    private lateinit var audioRepository: AudioRepositoryImpl

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        database = Room.inMemoryDatabaseBuilder(
            context,
            AvspDatabase::class.java
        ).allowMainThreadQueries().build()

        val storage = FileAvspStorage(context)
        val logger = AvspLoggerImpl(LogRepositoryImpl(database.logDao()))
        val secure = EncryptedSecureConfigStore(context)

        scriptRepository = ScriptRepositoryImpl(
            storage,
            DefaultScriptGeneratorRegistry(secure, MockScriptGenerator()),
            logger
        )

        val mockTts = MockTtsEngine()
        val ttsRegistry = DefaultTtsEngineRegistry(
            context = context,
            secureConfigStore = secure,
            mock = mockTts,
            androidEngineFactory = { null }
        )

        audioRepository = AudioRepositoryImpl(
            storage,
            scriptRepository,
            ttsRegistry,
            logger
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun realM2ToM3AdaptersProduceValidCanonicalContracts() = runBlocking {
        val projectId = "s18-real-chain"

        val script = scriptRepository.generate(
            ScriptGenerationRequest(
                projectId = projectId,
                topic = "Kharagpur update",
                languageCode = "en",
                duration = DurationRequest.ShortForm
            )
        )

        assertThat(script.validation.isValid).isTrue()
        assertThat(script.projectId).isEqualTo(projectId)

        val canonicalScript = M2ScriptContractAdapter.toAvsp20(script)
        val scriptValidation =
            Avsp20IntegrationValidator.validateScript(canonicalScript)

        assertThat(scriptValidation.valid).isTrue()

        val audio = audioRepository.generate(
            AudioGenerationRequest(
                projectId = projectId,
                preferredProviderId = MockTtsEngine.PROVIDER_ID
            )
        )

        assertThat(audio.validation.isValid).isTrue()
        assertThat(audio.scriptId).isEqualTo(script.scriptId)

        val canonicalAudio = M3AudioContractAdapter.toAvsp20(audio)
        val audioValidation =
            Avsp20IntegrationValidator.validateAudio(canonicalAudio)

        assertThat(audioValidation.valid).isTrue()
        assertThat(canonicalAudio.sourceScriptId).isEqualTo(canonicalScript.header.id)
    }
}


