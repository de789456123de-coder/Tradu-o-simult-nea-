package com.seuprojeto.translator

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.*
import java.io.File
import java.io.FileInputStream
import java.util.Locale

class AudioChannelManager(private val context: Context) {

    companion object {
        private const val TAG = "AudioChannelManager"
        private const val SAMPLE_RATE = 44100
    }

    private var ttsLeft: TextToSpeech? = null
    private var ttsRight: TextToSpeech? = null
    private var isTtsLeftReady = false
    private var isTtsRightReady = false
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun init(
        localeLeft: Locale = Locale.ENGLISH,
        localeRight: Locale = Locale("pt", "BR"),
        onReady: () -> Unit
    ) {
        ttsLeft = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                ttsLeft?.language = localeLeft
                isTtsLeftReady = true
                if (isTtsRightReady) onReady()
            }
        }
        ttsRight = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                ttsRight?.language = localeRight
                isTtsRightReady = true
                if (isTtsLeftReady) onReady()
            }
        }
    }

    enum class Channel { LEFT, RIGHT }

    fun speakLeft(text: String) {
        if (!isTtsLeftReady) return
        scope.launch {
            val f = synthesizeToFile(ttsLeft, text, "left_${System.currentTimeMillis()}")
            f?.let { playOnChannel(it, Channel.LEFT) }
        }
    }

    fun speakRight(text: String) {
        if (!isTtsRightReady) return
        scope.launch {
            val f = synthesizeToFile(ttsRight, text, "right_${System.currentTimeMillis()}")
            f?.let { playOnChannel(it, Channel.RIGHT) }
        }
    }

    fun speakBoth(textLeft: String, textRight: String) {
        speakLeft(textLeft)
        speakRight(textRight)
    }

    private fun playOnChannel(wavFile: File, channel: Channel) {
        try {
            val pcmData = extractPcmFromWav(wavFile)
            if (pcmData.isEmpty()) return

            val stereoData = ShortArray(pcmData.size * 2)
            for (i in pcmData.indices) {
                when (channel) {
                    Channel.LEFT -> { stereoData[i * 2] = pcmData[i]; stereoData[i * 2 + 1] = 0 }
                    Channel.RIGHT -> { stereoData[i * 2] = 0; stereoData[i * 2 + 1] = pcmData[i] }
                }
            }

            val bufferSize = AudioTrack.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_STEREO,
                AudioFormat.ENCODING_PCM_16BIT
            )

            val audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .build()
                )
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
            wavFile.delete()
        } catch (e: Exception) {
            Log.e(TAG, "Erro: ${e.message}")
        }
    }

    private suspend fun synthesizeToFile(
        tts: TextToSpeech?,
        text: String,
        filename: String
    ): File? = suspendCancellableCoroutine { cont ->
        if (tts == null) { cont.resume(null) {}; return@suspendCancellableCoroutine }
        val outputFile = File(context.cacheDir, "$filename.wav")
        val uttId = "utt_$filename"
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) {}
            override fun onDone(id: String?) { if (id == uttId) cont.resume(outputFile) {} }
            override fun onError(id: String?) { cont.resume(null) {} }
        })
        if (tts.synthesizeToFile(text, null, outputFile, uttId) != TextToSpeech.SUCCESS) {
            cont.resume(null) {}
        }
    }

    private fun extractPcmFromWav(wavFile: File): ShortArray {
        return try {
            val bytes = FileInputStream(wavFile).use { it.readBytes() }
            val pcmBytes = bytes.drop(44).toByteArray()
            ShortArray(pcmBytes.size / 2) { i ->
                ((pcmBytes[i * 2 + 1].toInt() shl 8) or (pcmBytes[i * 2].toInt() and 0xFF)).toShort()
            }
        } catch (e: Exception) { ShortArray(0) }
    }

    fun release() {
        scope.cancel()
        ttsLeft?.shutdown()
        ttsRight?.shutdown()
    }
}
