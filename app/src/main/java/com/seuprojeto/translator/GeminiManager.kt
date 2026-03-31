package com.seuprojeto.translator

import android.util.Log
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

class GeminiManager {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
    private val apiKey = "AIzaSyBJVXS8yMiJ_vB3mx88UAkbMH96Wvhe6O0"
    private val apiUrl = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$apiKey"

    suspend fun translateWithContext(
        text: String,
        sourceLang: String,
        targetLang: String,
        contextInstruction: String
    ): String {
        return suspendCancellableCoroutine { cont ->
            val prompt = "Translate from $sourceLang to $targetLang. Context: $contextInstruction. Reply with translation only, no explanation.\nText: \"$text\""

            val json = JSONObject().apply {
                put("contents", JSONArray().apply {
                    put(JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply { put("text", prompt) })
                        })
                    })
                })
            }

            Log.d("GeminiManager", "Enviando para: $apiUrl")
            Log.d("GeminiManager", "Body: ${json.toString().take(200)}")

            val request = Request.Builder()
                .url(apiUrl)
                .post(json.toString().toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    Log.e("GeminiManager", "Falha de rede: ${e.message}")
                    cont.resume("Erro: ${e.message}")
                }

                override fun onResponse(call: Call, response: Response) {
                    val bodyStr = response.body?.string() ?: ""
                    Log.d("GeminiManager", "HTTP ${response.code} | Resposta: ${bodyStr.take(300)}")
                    try {
                        val res = JSONObject(bodyStr)
                        
                        // Verifica erro da API
                        if (res.has("error")) {
                            val errMsg = res.getJSONObject("error").getString("message")
                            Log.e("GeminiManager", "Erro API: $errMsg")
                            cont.resume("Erro: $errMsg")
                            return
                        }

                        val translated = res
                            .getJSONArray("candidates")
                            .getJSONObject(0)
                            .getJSONObject("content")
                            .getJSONArray("parts")
                            .getJSONObject(0)
                            .getString("text")
                            .trim()

                        Log.d("GeminiManager", "Traduzido: $translated")
                        cont.resume(translated)
                    } catch (e: Exception) {
                        Log.e("GeminiManager", "Parse erro: ${e.message} | Body: ${bodyStr.take(200)}")
                        cont.resume("Erro: parse")
                    }
                }
            })
        }
    }
}
