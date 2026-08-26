package com.avsp.pro.script.generator.gemini

import com.avsp.pro.script.contract.ScriptGenerationRequest

/**
 * Deterministic instruction layer for Gemini → existing ScriptPackage fields only.
 */
object GeminiScriptPrompt {

    fun systemAndUserPrompt(request: ScriptGenerationRequest): String {
        val targetMs = request.duration.resolveTargetMs()
        val instructions = request.userInstructions?.trim().orEmpty()
        val factual = request.factualRequirements?.trim().orEmpty()
        return buildString {
            appendLine("You are the AVSP Script AI for short-form video production.")
            appendLine("Return ONLY valid JSON. No markdown fences. No commentary outside JSON.")
            appendLine("Language of ALL narrative text must be language code: ${request.languageCode}")
            appendLine("Topic: ${request.topic.trim()}")
            appendLine("Target total durationMs for all scenes combined: $targetMs")
            appendLine("Content type: ${request.contentType.name}")
            appendLine("Audience: ${request.audience.name}")
            appendLine("Platform: ${request.platform.name}")
            appendLine("Aspect ratio: ${request.aspectRatioLabel}")
            if (instructions.isNotEmpty()) appendLine("User instructions: $instructions")
            appendLine("Requirements:")
            appendLine("1. JSON must match the schema below exactly (field names and enums).")
            appendLine("2. Produce 3-6 scenes ordered from 0..n-1 with unique order values.")
            appendLine("3. Sum of scene durationMs must be within ±15% of $targetMs.")
            appendLine("4. Each scene durationMs between 2000 and 45000.")
            appendLine("5. Narration must be natural spoken language in ${request.languageCode}.")
            appendLine("6. Treat numbers naturally for the requested language/locale (no digit spam).")
            appendLine("7. Visual/shot guidance must be practical for phone capture.")
            appendLine("8. Do not invent fields outside the schema.")
            if (factual.isNotEmpty()) {
                appendLine("9. Respect factual requirements: $factual")
            }
            appendLine()
            appendLine(
                """
                Schema:
                {
                  "title":"string",
                  "hook":"string",
                  "introduction":"string",
                  "cta":"string",
                  "ending":"string",
                  "scenes":[
                    {
                      "order":0,
                      "durationMs":8000,
                      "narration":"string",
                      "onScreenText":"string",
                      "visualDescription":"string",
                      "shotType":"WIDE|MEDIUM|CLOSE_UP|B_ROLL|TEXT_CARD|INTRO|OUTRO",
                      "cameraDirection":"string",
                      "bRollSuggestion":"string",
                      "transition":"CUT|FADE|DISSOLVE|NONE",
                      "notes":"string"
                    }
                  ]
                }
                """.trimIndent()
            )
        }
    }
}
