package com.avsp.pro.script.generator.gemini

import com.avsp.pro.core.error.ErrorCode
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException

data class GeminiHttpResponse(
    val code: Int,
    val body: String
)

/**
 * Minimal transport for Generative Language REST API.
 * Injectable for unit tests — production uses [HttpUrlConnectionGeminiTransport].
 */
fun interface GeminiHttpTransport {
    fun postJson(
        url: String,
        jsonBody: String,
        connectTimeoutMs: Int,
        readTimeoutMs: Int
    ): GeminiHttpResponse
}

class HttpUrlConnectionGeminiTransport : GeminiHttpTransport {
    override fun postJson(
        url: String,
        jsonBody: String,
        connectTimeoutMs: Int,
        readTimeoutMs: Int
    ): GeminiHttpResponse {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = connectTimeoutMs
            readTimeout = readTimeoutMs
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
        }
        return try {
            connection.outputStream.use { it.write(jsonBody.toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = if (stream != null) {
                BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { it.readText() }
            } else {
                ""
            }
            GeminiHttpResponse(code, body)
        } catch (e: SocketTimeoutException) {
            throw GeminiScriptException(
                message = "Gemini request timed out",
                code = ErrorCode.MODULE_ERROR,
                details = "timeout",
                cause = e
            )
        } catch (e: UnknownHostException) {
            throw GeminiScriptException(
                message = "Gemini network failure (host unreachable)",
                code = ErrorCode.MODULE_ERROR,
                details = "network",
                cause = e
            )
        } catch (e: GeminiScriptException) {
            throw e
        } catch (e: Exception) {
            throw GeminiScriptException(
                message = "Gemini network failure",
                code = ErrorCode.MODULE_ERROR,
                details = e.message,
                cause = e
            )
        } finally {
            connection.disconnect()
        }
    }
}
