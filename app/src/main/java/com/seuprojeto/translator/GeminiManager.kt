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
    private val client = OkHttpClient()
    private val apiKey = "AIzaSyBY2mrVzfmcVp7lsjXdmt7aCNiRaiKvysM"
    private val apiUrl = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=$apiKey"

    suspend fun translateWithContext(text: String, source: String, target: String, instruct: String): String {
        return suspendCancellableCoroutine { cont ->
            val prompt = "Atue como tradutor. Contexto: $instruct. Traduza de $source para $target: \"$text\". Retorne apenas a tradução."
            
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
                override fun onFailure(call: Call, e: IOException) { cont.resume("Erro de conexão") }
                override fun onResponse(call: Call, response: Response) {
                    try {
                        val res = JSONObject(response.body?.string() ?: "")
                        val textRes = res.getJSONArray("candidates").getJSONObject(0)
                            .getJSONObject("content").getJSONArray("parts").getJSONObject(0).getString("text")
                        cont.resume(textRes.trim())
                    } catch (e: Exception) { cont.resume("Erro na IA") }
                }
            })
        }
    }
}
