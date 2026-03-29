package com.seuprojeto.translator

import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.IOException
import kotlin.coroutines.resume

class GeminiManager() {
    private val client = OkHttpClient()
    private val apiKey = "AIzaSyAVhnSi2UjE0VQ7zh56FBIT5ScLvhvUMNo"
    private val apiUrl = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=$apiKey"

    suspend fun translateWithContext(
        text: String,
        sourceLang: String,
        targetLang: String,
        context: String
    ): String {
        return suspendCancellableCoroutine { continuation ->
            val prompt = """
                Atue como um tradutor especialista no contexto: $context.
                Traduza a seguinte frase de $sourceLang para $targetLang.
                Retorne APENAS a tradução, sem explicações.
                Frase: "$text"
            """.trimIndent()

            val json = JSONObject().apply {
                put("contents", JSONObject().apply {
                    put("parts", JSONObject().apply {
                        put("text", prompt)
                    })
                })
            }

            val request = Request.Builder()
                .url(apiUrl)
                .post(json.toString().toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    continuation.resume("Erro de conexão")
                }

                override fun onResponse(call: Call, response: Response) {
                    val body = response.body?.string()
                    try {
                        val resJson = JSONObject(body ?: "")
                        val translated = resJson.getJSONArray("candidates")
                            .getJSONObject(0)
                            .getJSONObject("content")
                            .getJSONArray("parts")
                            .getJSONObject(0)
                            .getString("text")
                        continuation.resume(translated.trim())
                    } catch (e: Exception) {
                        continuation.resume("Erro ao processar tradução")
                    }
                }
            })
        }
    }
}
