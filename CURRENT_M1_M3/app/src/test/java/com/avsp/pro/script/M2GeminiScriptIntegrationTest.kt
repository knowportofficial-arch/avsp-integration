package com.avsp.pro.script

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.avsp.pro.core.error.ErrorCode
import com.avsp.pro.script.contract.DurationRequest
import com.avsp.pro.script.contract.ScriptGenerationRequest
import com.avsp.pro.script.generator.DefaultScriptGeneratorRegistry
import com.avsp.pro.script.generator.MockScriptGenerator
import com.avsp.pro.script.generator.gemini.GeminiConfig
import com.avsp.pro.script.generator.gemini.GeminiHttpResponse
import com.avsp.pro.script.generator.gemini.GeminiScriptException
import com.avsp.pro.script.generator.gemini.GeminiScriptGenerator
import com.avsp.pro.script.generator.gemini.GeminiScriptResponseParser
import com.avsp.pro.settings.EncryptedSecureConfigStore
import com.avsp.pro.settings.SecureConfigKeys
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class M2GeminiScriptIntegrationTest {

    private lateinit var secure: EncryptedSecureConfigStore

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        secure = EncryptedSecureConfigStore(context)
        secure.clearSecret(SecureConfigKeys.AI_API)
    }

    @Test
    fun parseStructuredResponsePreservesProjectIdentityAndScenes() {
        val request = ScriptGenerationRequest(
            projectId = "prj_gemini_id",
            topic = "Monsoon in Kharagpur",
            languageCode = "en",
            duration = DurationRequest.Explicit(30_000L)
        )
        val modelJson = """
            {
              "title":"Monsoon Brief",
              "hook":"Rain is coming.",
              "introduction":"A short look at local weather.",
              "cta":"Stay dry and prepared.",
              "ending":"That's the forecast.",
              "scenes":[
                {
                  "order":1,
                  "durationMs":10000,
                  "narration":"Clouds gather over the campus.",
                  "onScreenText":"Clouds",
                  "visualDescription":"Wide sky shot",
                  "shotType":"WIDE",
                  "cameraDirection":"Pan left slowly",
                  "bRollSuggestion":"Umbrellas",
                  "transition":"CUT",
                  "notes":""
                },
                {
                  "order":0,
                  "durationMs":10000,
                  "narration":"Start with a calm street intro.",
                  "onScreenText":"Intro",
                  "visualDescription":"Street medium",
                  "shotType":"MEDIUM",
                  "cameraDirection":"Hold steady",
                  "bRollSuggestion":"",
                  "transition":"CUT",
                  "notes":""
                },
                {
                  "order":2,
                  "durationMs":10000,
                  "narration":"Close on raindrops on leaves.",
                  "onScreenText":"Rain",
                  "visualDescription":"Close detail",
                  "shotType":"CLOSE_UP",
                  "cameraDirection":"Push in",
                  "bRollSuggestion":"Puddles",
                  "transition":"FADE",
                  "notes":"End beat"
                }
              ]
            }
        """.trimIndent()

        val pkg = GeminiScriptResponseParser.parseToPackage(
            rawModelText = modelJson,
            request = request,
            providerId = GeminiScriptGenerator.PROVIDER_ID
        )

        assertThat(pkg.projectId).isEqualTo("prj_gemini_id")
        assertThat(pkg.topic).isEqualTo("Monsoon in Kharagpur")
        assertThat(pkg.language).isEqualTo("en")
        assertThat(pkg.title).isEqualTo("Monsoon Brief")
        assertThat(pkg.scenes).hasSize(3)
        assertThat(pkg.scenes.map { it.order }).isEqualTo(listOf(0, 1, 2))
        assertThat(pkg.scenes.map { it.sceneId }).isEqualTo(listOf("scn_00", "scn_01", "scn_02"))
        assertThat(pkg.scenes[0].narration).contains("calm street")
        assertThat(pkg.scenes[0].shotType.name).isEqualTo("MEDIUM")
        assertThat(pkg.estimatedDurationMs).isEqualTo(30_000L)
        assertThat(pkg.metadata.generatorId).isEqualTo("gemini")
        assertThat(pkg.validation.isValid).isTrue()
    }

    @Test
    fun parseRejectsMalformedJson() {
        val request = ScriptGenerationRequest(
            projectId = "prj_bad",
            topic = "Bad JSON",
            languageCode = "en",
            duration = DurationRequest.ShortForm
        )
        try {
            GeminiScriptResponseParser.parseToPackage(
                rawModelText = "{not-json",
                request = request,
                providerId = "gemini"
            )
            throw AssertionError("Expected GeminiScriptException")
        } catch (e: GeminiScriptException) {
            assertThat(e.errorInfo.code).isEqualTo(ErrorCode.INVALID_INPUT)
            assertThat(e.errorInfo.message).contains("malformed")
        }
    }

    @Test
    fun parseRejectsEmptyScenes() {
        val request = ScriptGenerationRequest(
            projectId = "prj_empty",
            topic = "Empty",
            languageCode = "en",
            duration = DurationRequest.ShortForm
        )
        try {
            GeminiScriptResponseParser.parseToPackage(
                rawModelText = """{"title":"T","hook":"H","introduction":"I","cta":"C","ending":"E","scenes":[]}""",
                request = request,
                providerId = "gemini"
            )
            throw AssertionError("Expected GeminiScriptException")
        } catch (e: GeminiScriptException) {
            assertThat(e.errorInfo.message).contains("zero scenes")
        }
    }

    @Test
    fun extractModelTextFromApiEnvelope() {
        val api = """
            {
              "candidates":[{
                "content":{"parts":[{"text":"{\"title\":\"X\",\"hook\":\"H\",\"introduction\":\"I\",\"cta\":\"C\",\"ending\":\"E\",\"scenes\":[{\"order\":0,\"durationMs\":8000,\"narration\":\"Hello world script line.\",\"onScreenText\":\"\",\"visualDescription\":\"v\",\"shotType\":\"MEDIUM\",\"cameraDirection\":\"\",\"bRollSuggestion\":\"\",\"transition\":\"CUT\",\"notes\":\"\"},{\"order\":1,\"durationMs\":8000,\"narration\":\"Second spoken line for duration.\",\"onScreenText\":\"\",\"visualDescription\":\"v2\",\"shotType\":\"WIDE\",\"cameraDirection\":\"\",\"bRollSuggestion\":\"\",\"transition\":\"CUT\",\"notes\":\"\"},{\"order\":2,\"durationMs\":8000,\"narration\":\"Third spoken line wraps the piece.\",\"onScreenText\":\"\",\"visualDescription\":\"v3\",\"shotType\":\"CLOSE_UP\",\"cameraDirection\":\"\",\"bRollSuggestion\":\"\",\"transition\":\"CUT\",\"notes\":\"\"}]}"}]
                }
              }]
            }
        """.trimIndent()
        val text = GeminiScriptResponseParser.extractModelTextFromApiResponse(api)
        assertThat(text).contains("\"title\":\"X\"")
        val pkg = GeminiScriptResponseParser.parseToPackage(
            rawModelText = text,
            request = ScriptGenerationRequest(
                projectId = "prj_api",
                topic = "API wrap",
                languageCode = "en",
                duration = DurationRequest.Explicit(24_000L)
            ),
            providerId = "gemini"
        )
        assertThat(pkg.title).isEqualTo("X")
        assertThat(pkg.projectId).isEqualTo("prj_api")
    }

    @Test
    fun missingApiKeyThrowsConfigError() = runBlocking {
        val generator = GeminiScriptGenerator(
            secureConfigStore = secure,
            transport = { _, _, _, _ -> error("transport must not be called") }
        )
        try {
            generator.generate(
                ScriptGenerationRequest(
                    projectId = "prj_nokey",
                    topic = "No key topic",
                    languageCode = "en",
                    duration = DurationRequest.ShortForm
                )
            )
            throw AssertionError("Expected GeminiScriptException")
        } catch (e: GeminiScriptException) {
            assertThat(e.errorInfo.code).isEqualTo(ErrorCode.CONFIG_ERROR)
            assertThat(e.errorInfo.message).contains("not configured")
        }
    }

    @Test
    fun providerHttpFailureSurfacesWithoutFakeSuccess() = runBlocking {
        secure.putSecret(SecureConfigKeys.AI_API, "test-dev-key-not-real")
        val generator = GeminiScriptGenerator(
            secureConfigStore = secure,
            transport = { _, _, _, _ -> GeminiHttpResponse(500, """{"error":{"message":"boom"}}""") }
        )
        try {
            generator.generate(
                ScriptGenerationRequest(
                    projectId = "prj_http",
                    topic = "HTTP fail topic",
                    languageCode = "en",
                    duration = DurationRequest.ShortForm
                )
            )
            throw AssertionError("Expected GeminiScriptException")
        } catch (e: GeminiScriptException) {
            assertThat(e.errorInfo.message).contains("HTTP 500")
            assertThat(e.errorInfo.message).contains("boom")
            assertThat(e.errorInfo.code).isEqualTo(ErrorCode.MODULE_ERROR)
        }
    }

    @Test
    fun http404IncludesSafeApiMessageAndDoesNotFakeSuccess() = runBlocking {
        secure.putSecret(SecureConfigKeys.AI_API, "test-dev-key-not-real")
        val retiredBody =
            """{"error":{"code":404,"message":"This model models/gemini-2.0-flash is no longer available.","status":"NOT_FOUND"}}"""
        val generator = GeminiScriptGenerator(
            secureConfigStore = secure,
            transport = { _, _, _, _ -> GeminiHttpResponse(404, retiredBody) }
        )
        try {
            generator.generate(
                ScriptGenerationRequest(
                    projectId = "prj_404",
                    topic = "my tv",
                    languageCode = "en",
                    duration = DurationRequest.MediumForm
                )
            )
            throw AssertionError("Expected GeminiScriptException")
        } catch (e: GeminiScriptException) {
            assertThat(e.errorInfo.message).contains("Gemini HTTP 404")
            assertThat(e.errorInfo.message).contains("no longer available")
            assertThat(e.errorInfo.message).doesNotContain("test-dev-key-not-real")
            assertThat(e.errorInfo.details).contains("generateContent")
            assertThat(e.errorInfo.code).isEqualTo(ErrorCode.MODULE_ERROR)
        }
    }

    @Test
    fun successfulTransportProducesGeminiLabeledPackage() = runBlocking {
        secure.putSecret(SecureConfigKeys.AI_API, "test-dev-key-not-real")
        val sceneJson = """
            {"title":"Live Path","hook":"Hook","introduction":"Intro","cta":"CTA","ending":"End",
             "scenes":[
               {"order":0,"durationMs":10000,"narration":"First spoken beat for the video.","onScreenText":"","visualDescription":"w","shotType":"WIDE","cameraDirection":"","bRollSuggestion":"","transition":"CUT","notes":""},
               {"order":1,"durationMs":10000,"narration":"Second spoken beat continues story.","onScreenText":"","visualDescription":"m","shotType":"MEDIUM","cameraDirection":"","bRollSuggestion":"","transition":"CUT","notes":""},
               {"order":2,"durationMs":10000,"narration":"Third spoken beat closes cleanly.","onScreenText":"","visualDescription":"c","shotType":"CLOSE_UP","cameraDirection":"","bRollSuggestion":"","transition":"CUT","notes":""}
             ]}
        """.trimIndent()
        val apiBody = """{"candidates":[{"content":{"parts":[{"text":${org.json.JSONObject.quote(sceneJson)}}]}}]}"""
        val generator = GeminiScriptGenerator(
            secureConfigStore = secure,
            transport = { url, body, _, _ ->
                assertThat(url).contains("generativelanguage.googleapis.com/v1beta/models/")
                assertThat(url).contains("gemini-flash-latest:generateContent")
                assertThat(url).contains("key=test-dev-key-not-real")
                assertThat(url).doesNotContain("gemini-2.0-flash")
                assertThat(body).contains("Monsoon")
                GeminiHttpResponse(200, apiBody)
            }
        )
        val pkg = generator.generate(
            ScriptGenerationRequest(
                projectId = "prj_ok",
                topic = "Monsoon briefing",
                languageCode = "en",
                duration = DurationRequest.Explicit(30_000L)
            )
        )
        assertThat(pkg.projectId).isEqualTo("prj_ok")
        assertThat(pkg.metadata.generatorId).isEqualTo(GeminiScriptGenerator.PROVIDER_ID)
        assertThat(pkg.metadata.generatorMode).isEqualTo("REMOTE")
        assertThat(pkg.scenes).hasSize(3)
        assertThat(pkg.validation.isValid).isTrue()
    }

    @Test
    fun registryPrefersGeminiWhenConfiguredElseMock() {
        val mock = MockScriptGenerator()
        val fakeGemini = object : com.avsp.pro.script.generator.ScriptGenerator by mock {
            override val providerId = GeminiScriptGenerator.PROVIDER_ID
            override val displayName = "Gemini Script Generator"
            override val mode = com.avsp.pro.script.generator.GeneratorMode.REMOTE
        }
        val registry = DefaultScriptGeneratorRegistry(
            secureConfigStore = secure,
            mock = mock,
            geminiFactory = { fakeGemini }
        )
        assertThat(registry.isRemoteConfigured()).isFalse()
        assertThat(registry.resolve().providerId).isEqualTo(MockScriptGenerator.PROVIDER_ID)

        secure.putSecret(SecureConfigKeys.AI_API, "configured-key")
        assertThat(registry.isRemoteConfigured()).isTrue()
        assertThat(registry.resolve().providerId).isEqualTo(GeminiScriptGenerator.PROVIDER_ID)
        assertThat(registry.available().map { it.providerId })
            .containsExactly(GeminiScriptGenerator.PROVIDER_ID, MockScriptGenerator.PROVIDER_ID)
            .inOrder()
    }

    @Test
    fun generateContentUrlUsesConfigurableModelAndV1Beta() {
        val url = GeminiConfig().generateContentUrl("test-dev-key-not-real")
        assertThat(url).isEqualTo(
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-flash-latest:generateContent?key=test-dev-key-not-real"
        )
        val custom = GeminiConfig(model = "gemini-2.5-flash").generateContentUrl("k")
        assertThat(custom).contains("models/gemini-2.5-flash:generateContent")
    }
}
