package com.avsp.pro.script.generator

import com.avsp.pro.settings.ConfigState
import com.avsp.pro.settings.SecureConfigKeys
import com.avsp.pro.settings.SecureConfigStore

/**
 * Default registry: always offers Mock. Remote providers stay unavailable until configured.
 * Does not invent AI output when credentials are NOT_CONFIGURED.
 */
class DefaultScriptGeneratorRegistry(
    private val secureConfigStore: SecureConfigStore,
    private val mock: ScriptGenerator = MockScriptGenerator()
) : ScriptGeneratorRegistry {

    override fun available(): List<ScriptGenerator> = listOf(mock)

    override fun isRemoteConfigured(): Boolean =
        secureConfigStore.configState(SecureConfigKeys.AI_API) == ConfigState.CONFIGURED

    override fun resolve(preferredProviderId: String?): ScriptGenerator {
        // Remote providers are extension points only — M2 ships with Mock.
        // When AI is CONFIGURED, still use Mock until a real provider adapter is registered.
        // This keeps the app operable and avoids unpaid/mandatory API dependency.
        val preferred = preferredProviderId?.let { id ->
            available().find { it.providerId == id }
        }
        return preferred ?: mock
    }

    fun aiConfigState(): ConfigState = secureConfigStore.configState(SecureConfigKeys.AI_API)
}
