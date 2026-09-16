package com.avsp.pro.m7.capture.camera.planner

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

/**
 * Gemini planner implementation using the public Generative Language REST API.
 * The API key is supplied at runtime; it is never hard-coded in this source.
 * If the key is blank or the request/response fails, the local planner is used.
 */
class GeminiShotPlanner(
    private val apiKeyProvider: () -> String?,
    private val fallback: ShotPlanner = LocalShotPlanner(),
    private val model: String = "gemini-2.0-flash"
) : ShotPlanner {

    override suspend fun createPlan(request: String): MasterShotPlan {
        val key = apiKeyProvider()?.trim().orEmpty()
        if (key.isBlank()) return fallback.createPlan(request)

        return try {
            withContext(Dispatchers.IO) { requestGemini(key, request) }
        } catch (_: Exception) {
            fallback.createPlan(request)
        }
    }

    private fun requestGemini(key: String, request: String): MasterShotPlan {
        val url = URL("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$key")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 30_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
        }

        val prompt = """
You are the AVSP media-planning AI. Create a practical capture plan from the user's request.
Do NOT force WIDE/MEDIUM/CLOSE terminology for every request. For people, use portrait,
full_body, or environmental when appropriate. For documents use document. For products,
food, temples, events, landscapes, choose the framing that actually makes sense.

Return ONLY valid JSON with this exact shape:
{
  "intent":"GENERAL_COVERAGE|PERSON_PORTRAIT|FOOD|PLACE|TEMPLE|PRODUCT|DOCUMENT|EVENT|LANDSCAPE|CUSTOM",
  "title":"...",
  "summary":"...",
  "shots":[
    {"title":"...","purpose":"...","mediaType":"PHOTO|VIDEO","framing":"WIDE|MEDIUM|CLOSE|PORTRAIT|FULL_BODY|ENVIRONMENTAL|DETAIL|MACRO|DOCUMENT|TRACKING|UNSPECIFIED","subject":"...","guidance":"...","priority":0,"required":true,"applicabilityHint":"..."}
  ]
}
Generate 3-12 distinct useful shots. Avoid redundant shots. Mark a shot required=false when it is naturally optional.
User request: ${request.replace("\"", "\\\"")}
""".trimIndent()

        val body = JSONObject()
            .put("contents", JSONArray().put(JSONObject().put("parts", JSONArray().put(JSONObject().put("text", prompt)))))
            .put("generationConfig", JSONObject().put("temperature", 0.2).put("responseMimeType", "application/json"))
            .toString()

        connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        val responseCode = connection.responseCode
        val stream = if (responseCode in 200..299) connection.inputStream else connection.errorStream
        val response = BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { it.readText() }
        if (responseCode !in 200..299) error("Gemini HTTP $responseCode")

        val root = JSONObject(response)
        val text = root.getJSONArray("candidates")
            .getJSONObject(0)
            .getJSONObject("content")
            .getJSONArray("parts")
            .getJSONObject(0)
            .getString("text")
            .trim()
            .removePrefix("```")
            .removePrefix("json")
            .removeSuffix("```")
            .trim()

        return parsePlan(request, JSONObject(text))
    }

    private fun parsePlan(request: String, json: JSONObject): MasterShotPlan {
        val intent = runCatching { CaptureIntent.valueOf(json.optString("intent", "GENERAL_COVERAGE")) }
            .getOrDefault(CaptureIntent.GENERAL_COVERAGE)
        val shotsJson = json.optJSONArray("shots") ?: JSONArray()
        val shots = buildList {
            for (i in 0 until shotsJson.length()) {
                val s = shotsJson.optJSONObject(i) ?: continue
                val media = runCatching { PlannedMediaType.valueOf(s.optString("mediaType", "PHOTO")) }.getOrDefault(PlannedMediaType.PHOTO)
                val framing = runCatching { PlannedFraming.valueOf(s.optString("framing", "UNSPECIFIED")) }.getOrDefault(PlannedFraming.UNSPECIFIED)
                add(PlannedShot(
                    id = "shot_${UUID.randomUUID().toString().take(8)}",
                    sequence = i + 1,
                    title = s.optString("title", "Shot ${i + 1}"),
                    purpose = s.optString("purpose", ""),
                    mediaType = media,
                    framing = framing,
                    subject = s.optString("subject", ""),
                    guidance = s.optString("guidance", ""),
                    priority = s.optInt("priority", 50).coerceIn(0, 100),
                    required = s.optBoolean("required", true),
                    applicabilityHint = s.optString("applicabilityHint", "Skip when the required subject/scene is not present.")
                ))
            }
        }
        if (shots.isEmpty()) error("Gemini returned no shots")

        return MasterShotPlan(
            id = "plan_${UUID.randomUUID().toString().take(8)}",
            userRequest = request,
            intent = intent,
            title = json.optString("title", "AI Coverage Plan"),
            summary = json.optString("summary", "AI-generated coverage plan"),
            shots = shots,
            planner = "GEMINI_$model"
        )
    }
}
