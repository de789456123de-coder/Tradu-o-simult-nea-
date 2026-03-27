package com.seuprojeto.translator

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.MediaPlayer
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.*
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

class AudioChannelManager(private val context: Context) {

    companion object {
        private const val TAG = "AudioChannelManager"
        private const val SAMPLE_RATE = 44100
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var apiKey: String = ""

    private var leftLangCode = "pt-BR"
    private var rightLangCode = "en-US"

    // Mapa idioma → voz Google Cloud TTS
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
        "uk" to ("uk-UA" to "uk-UA-Wavenet-A"),
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
            playMp3OnChannel(mp3, Channel.LEFT)
        }
    }

    fun speakRight(text: String) {
        scope.launch {
            val (langCode, voiceName) = voiceMap[rightLangCode] ?: ("en-US" to "en-US-Wavenet-D")
            val mp3 = fetchTTS(text, langCode, voiceName) ?: return@launch
            playMp3OnChannel(mp3, Channel.RIGHT)
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
                    put("effectsProfileId", listOf("headphone-class-device"))
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

    private fun playMp3OnChannel(mp3Data: ByteArray, channel: Channel) {
        try {
            // Salva MP3 temporário
            val tempFile = File(context.cacheDir, "tts_${channel}_${System.currentTimeMillis()}.mp3")
            FileOutputStream(tempFile).use { it.write(mp3Data) }

            // Converte MP3 → PCM via MediaPlayer + AudioTrack
            val pcm = decodeMp3ToPcm(tempFile) ?: return
            tempFile.delete()

            val stereoData = ShortArray(pcm.size * 2)
            for (i in pcm.indices) {
                when (channel) {
                    Channel.LEFT  -> { stereoData[i * 2] = pcm[i]; stereoData[i * 2 + 1] = 0 }
                    Channel.RIGHT -> { stereoData[i * 2] = 0; stereoData[i * 2 + 1] = pcm[i] }
                }
            }

            val bufferSize = AudioTrack.getMinBufferSize(
                SAMPLE_RATE, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT
            )

            val audioTrack = AudioTrack.Builder()
                .setAudioAttributes(AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build())
                .setAudioFormat(AudioFormat.Builder()
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .build())
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            audioTrack.play()
            var offset = 0
            while (offset < stereoData.size) {
                val end = minOf(offset + bufferSize / 2, stereoData.size)
                audioTrack.write(stereoData, offset, end - offset)
                offset = end
            }
            audioTrack.stop()
            audioTrack.release()

        } catch (e: Exception) {
            Log.e(TAG, "Playback erro: ${e.message}")
        }
    }

    private fun decodeMp3ToPcm(mp3File: File): ShortArray? {
        return try {
            val extractor = android.media.MediaExtractor()
            extractor.setDataSource(mp3File.absolutePath)
            extractor.selectTrack(0)
            val format = extractor.getTrackFormat(0)
            val mime = format.getString(android.media.MediaFormat.KEY_MIME) ?: return null

            val codec = android.media.MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()

            val pcmBuffer = mutableListOf<Short>()
            val info = android.media.MediaCodec.BufferInfo()
            var sawEOS = false

            while (!sawEOS) {
                val inIdx = codec.dequeueInputBuffer(10000)
                if (inIdx >= 0) {
                    val buf = codec.getInputBuffer(inIdx)!!
                    val sampleSize = extractor.readSampleData(buf, 0)
                    if (sampleSize < 0) {
                        codec.queueInputBuffer(inIdx, 0, 0, 0, android.media.MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        sawEOS = true
                    } else {
                        codec.queueInputBuffer(inIdx, 0, sampleSize, extractor.sampleTime, 0)
                        extractor.advance()
                    }
                }

                val outIdx = codec.dequeueOutputBuffer(info, 10000)
                if (outIdx >= 0) {
                    val buf = codec.getOutputBuffer(outIdx)!!
                    val chunk = ShortArray(info.size / 2)
                    buf.order(java.nio.ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(chunk)
                    pcmBuffer.addAll(chunk.toList())
                    codec.releaseOutputBuffer(outIdx, false)
                }
            }

            codec.stop()
            codec.release()
            extractor.release()

            pcmBuffer.toShortArray()
        } catch (e: Exception) {
            Log.e(TAG, "Decode erro: ${e.message}")
            null
        }
    }

    fun setLanguageLeft(lang: String) { leftLangCode = lang }
    fun setLanguageRight(lang: String) { rightLangCode = lang }

    fun release() {
        scope.cancel()
    }
}
