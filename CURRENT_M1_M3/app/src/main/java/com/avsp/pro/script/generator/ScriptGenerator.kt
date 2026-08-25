package com.avsp.pro.script.generator

import com.avsp.pro.script.contract.ScriptGenerationRequest
import com.avsp.pro.script.contract.ScriptPackage

/**
 * Provider-independent script generation abstraction.
 * Future providers (local model, OpenAI, Gemini, Grok, …) implement this interface.
 */
interface ScriptGenerator {
    val providerId: String
    val displayName: String
    val mode: GeneratorMode
    suspend fun generate(request: ScriptGenerationRequest): ScriptPackage
}

enum class GeneratorMode {
    MOCK,
    LOCAL,
    REMOTE
}

/**
 * Resolves which generator to use. Safe when AI credentials are NOT_CONFIGURED.
 */
interface ScriptGeneratorRegistry {
    fun available(): List<ScriptGenerator>
    fun resolve(preferredProviderId: String? = null): ScriptGenerator
    fun isRemoteConfigured(): Boolean
}
