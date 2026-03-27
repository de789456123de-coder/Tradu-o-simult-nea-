package com.seuprojeto.translator

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class TranslationManager(private val apiKey: String) {

    // Detecta o idioma do texto via API
    suspend fun detectLanguage(text: String): String {
        return withContext(Dispatchers.IO) {
            try {
                val encodedText = URLEncoder.encode(text, "UTF-8")
                val urlStr = "https://translation.googleapis.com/language/translate/v2/detect?key=$apiKey&q=$encodedText"
                val url = URL(urlStr)
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.connectTimeout = 5000
                conn.readTimeout = 5000

                val response = conn.inputStream.bufferedReader().readText()
                val json = JSONObject(response)
                json.getJSONObject("data")
                    .getJSONArray("detections")
                    .getJSONArray(0)
                    .getJSONObject(0)
                    .getString("language")
            } catch (e: Exception) {
                "pt"
            }
        }
    }

    suspend fun translate(text: String, sourceLang: String, targetLang: String): String {
        return withContext(Dispatchers.IO) {
            try {
                val encodedText = URLEncoder.encode(text, "UTF-8")
                val urlStr = "https://translation.googleapis.com/language/translate/v2" +
                    "?key=$apiKey" +
                    "&q=$encodedText" +
                    "&source=$sourceLang" +
                    "&target=$targetLang" +
                    "&format=text"

                val url = URL(urlStr)
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.connectTimeout = 5000
                conn.readTimeout = 5000

                val response = conn.inputStream.bufferedReader().readText()
                val json = JSONObject(response)
                json.getJSONObject("data")
                    .getJSONArray("translations")
                    .getJSONObject(0)
                    .getString("translatedText")
            } catch (e: Exception) {
                text
            }
        }
    }
}
