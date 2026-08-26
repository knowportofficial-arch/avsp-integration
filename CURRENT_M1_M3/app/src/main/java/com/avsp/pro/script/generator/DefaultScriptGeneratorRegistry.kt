package com.avsp.pro.script.generator

import com.avsp.pro.script.generator.gemini.GeminiScriptGenerator
import com.avsp.pro.settings.ConfigState
import com.avsp.pro.settings.SecureConfigKeys
import com.avsp.pro.settings.SecureConfigStore

/**
 * Registry: Mock always available.
 * When [SecureConfigKeys.AI_API] is CONFIGURED, Gemini is preferred for resolve().
 * Never silently labels mock output as Gemini.
 */
class DefaultScriptGeneratorRegistry(
    private val secureConfigStore: SecureConfigStore,
    private val mock: ScriptGenerator = MockScriptGenerator(),
    private val geminiFactory: () -> ScriptGenerator = {
        GeminiScriptGenerator(secureConfigStore)
    }
) : ScriptGeneratorRegistry {

    private val gemini by lazy { geminiFactory() }

    override fun available(): List<ScriptGenerator> {
        return if (isRemoteConfigured()) listOf(gemini, mock) else listOf(mock)
    }

    override fun isRemoteConfigured(): Boolean =
        secureConfigStore.configState(SecureConfigKeys.AI_API) == ConfigState.CONFIGURED

    override fun resolve(preferredProviderId: String?): ScriptGenerator {
        val preferred = preferredProviderId?.let { id ->
            available().find { it.providerId == id }
        }
        if (preferred != null) return preferred
        return if (isRemoteConfigured()) gemini else mock
    }

    fun aiConfigState(): ConfigState = secureConfigStore.configState(SecureConfigKeys.AI_API)

    fun activeProviderSummary(): String {
        val g = resolve()
        return "${g.displayName} (${g.providerId}/${g.mode.name})"
    }
}
