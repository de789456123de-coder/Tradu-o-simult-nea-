package com.seuprojeto.translator

import android.content.Context
import android.media.MediaPlayer
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.*
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

class AudioChannelManager(private val context: Context) {

    companion object {
        private const val TAG = "AudioChannelManager"
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var apiKey: String = ""
    private var leftLangCode = "pt"
    private var rightLangCode = "en"

    private val voiceMap = mapOf(
        "pt" to ("pt-BR" to "pt-BR-Wavenet-A"),
        "en" to ("en-US" to "en-US-Wavenet-D"),
        "es" to ("es-ES" to "es-ES-Wavenet-B"),
        "fr" to ("fr-FR" to "fr-FR-Wavenet-C"),
        "de" to ("de-DE" to "de-DE-Wavenet-B"),
        "it" to ("it-IT" to "it-IT-Wavenet-A"),
        "ja" to ("ja-JP" to "ja-JP-Wavenet-B"),
        "zh" to ("cmn-CN" to "cmn-CN-Wavenet-A"),
        "ko" to ("ko-KR" to "ko-KR-Wavenet-A"),
        "ru" to ("ru-RU" to "ru-RU-Wavenet-A"),
        "nl" to ("nl-NL" to "nl-NL-Wavenet-A"),
        "he" to ("he-IL" to "he-IL-Wavenet-A"),
        "ar" to ("ar-XA" to "ar-XA-Wavenet-A"),
        "hi" to ("hi-IN" to "hi-IN-Wavenet-A"),
        "pl" to ("pl-PL" to "pl-PL-Wavenet-A"),
        "sv" to ("sv-SE" to "sv-SE-Wavenet-A"),
        "tr" to ("tr-TR" to "tr-TR-Wavenet-A"),
        "vi" to ("vi-VN" to "vi-VN-Wavenet-A"),
        "id" to ("id-ID" to "id-ID-Wavenet-A"),
        "th" to ("th-TH" to "th-TH-Neural2-C")
    )

    fun init(
        apiKey: String,
        leftLang: String = "pt",
        rightLang: String = "en",
        onReady: () -> Unit
    ) {
        this.apiKey = apiKey
        this.leftLangCode = leftLang
        this.rightLangCode = rightLang
        onReady()
    }

    enum class Channel { LEFT, RIGHT }

    fun speakLeft(text: String) {
        scope.launch {
            val (langCode, voiceName) = voiceMap[leftLangCode] ?: ("pt-BR" to "pt-BR-Wavenet-A")
            val mp3 = fetchTTS(text, langCode, voiceName) ?: return@launch
            playMp3(mp3, Channel.LEFT)
        }
    }

    fun speakRight(text: String) {
        scope.launch {
            val (langCode, voiceName) = voiceMap[rightLangCode] ?: ("en-US" to "en-US-Wavenet-D")
            val mp3 = fetchTTS(text, langCode, voiceName) ?: return@launch
            playMp3(mp3, Channel.RIGHT)
        }
    }

    private fun fetchTTS(text: String, languageCode: String, voiceName: String): ByteArray? {
        return try {
            val url = URL("https://texttospeech.googleapis.com/v1/text:synthesize?key=$apiKey")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true
            conn.connectTimeout = 8000
            conn.readTimeout = 8000

            val body = JSONObject().apply {
                put("input", JSONObject().put("text", text))
                put("voice", JSONObject().apply {
                    put("languageCode", languageCode)
                    put("name", voiceName)
                })
                put("audioConfig", JSONObject().apply {
                    put("audioEncoding", "MP3")
                    put("speakingRate", 0.9)
                    put("pitch", -1.0)
                })
            }

            conn.outputStream.write(body.toString().toByteArray())
            val response = conn.inputStream.bufferedReader().readText()
            val audioContent = JSONObject(response).getString("audioContent")
            Base64.decode(audioContent, Base64.DEFAULT)
        } catch (e: Exception) {
            Log.e(TAG, "TTS erro: ${e.message}")
            null
        }
    }

    private fun playMp3(mp3Data: ByteArray, channel: Channel) {
        try {
            val tempFile = File(context.cacheDir, "tts_${channel}_${System.currentTimeMillis()}.mp3")
            FileOutputStream(tempFile).use { it.write(mp3Data) }

            val player = MediaPlayer()
            player.setDataSource(tempFile.absolutePath)

            // Pan: -1.0 = só esquerdo, 1.0 = só direito, 0.0 = ambos
            when (channel) {
                Channel.LEFT  -> player.setVolume(1.0f, 0.0f)
                Channel.RIGHT -> player.setVolume(0.0f, 1.0f)
            }

            player.prepare()
            player.start()

            player.setOnCompletionListener {
                it.release()
                tempFile.delete()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Playback erro: ${e.message}")
        }
    }

    fun setLanguageLeft(lang: String) { leftLangCode = lang }
    fun setLanguageRight(lang: String) { rightLangCode = lang }

    fun release() { scope.cancel() }
}
