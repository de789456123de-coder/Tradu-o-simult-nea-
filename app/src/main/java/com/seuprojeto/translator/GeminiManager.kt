package com.seuprojeto.translator

import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.IOException
import kotlin.coroutines.resume

class GeminiManager {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .build()
    private val apiKey = "AIzaSyAVhnSi2UjE0VQ7zh56FBIT5ScLvhvUMNo"
    private val apiUrl = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=$apiKey"

    suspend fun translateWithContext(
        text: String,
        sourceLang: String,
        targetLang: String,
        contextInstruction: String
    ): String {
        return suspendCancellableCoroutine { cont ->
            val prompt = "Você é um tradutor especialista. $contextInstruction\n" +
                "Traduza de $sourceLang para $targetLang. Retorne APENAS a tradução, sem explicações.\n" +
                "Frase: \"$text\""

            val json = JSONObject().apply {
                put("contents", JSONArray().apply {
                    put(JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply { put("text", prompt) })
                        })
                    })
                })
            }

            val request = Request.Builder()
                .url(apiUrl)
                .post(json.toString().toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    cont.resume("Erro: ${e.message}")
                }
                override fun onResponse(call: Call, response: Response) {
                    try {
                        val body = response.body?.string() ?: ""
                        val translated = JSONObject(body)
                            .getJSONArray("candidates")
                            .getJSONObject(0)
                            .getJSONObject("content")
                            .getJSONArray("parts")
                            .getJSONObject(0)
                            .getString("text")
                        cont.resume(translated.trim())
                    } catch (e: Exception) {
                        cont.resume("Erro: ${e.message}")
                    }
                }
            })
        }
    }
}
