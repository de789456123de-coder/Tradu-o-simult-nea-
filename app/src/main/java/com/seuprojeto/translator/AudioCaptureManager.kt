package com.seuprojeto.translator

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.*

class AudioCaptureManager(private val whisperLib: WhisperLib, private val contextPtr: Long) {

    private var audioRecord: AudioRecord? = null
    private var isRecording = false

    private val sampleRate = 16000
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    private val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat) * 2

    @SuppressLint("MissingPermission")
    fun startRecordingAndTranscribing(onTranscriptionResult: (String) -> Unit) {
        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.UNPROCESSED,
            sampleRate, channelConfig, audioFormat, bufferSize
        )

        audioRecord?.startRecording()
        isRecording = true

        CoroutineScope(Dispatchers.IO).launch {
            val chunkSamples = 16000 * 3
            val shortBuffer = ShortArray(chunkSamples)

            while (isRecording) {
                var totalRead = 0
                while (totalRead < chunkSamples && isRecording) {
                    val readSize = audioRecord?.read(shortBuffer, totalRead, chunkSamples - totalRead) ?: 0
                    if (readSize > 0) totalRead += readSize
                }

                if (totalRead > 0) {
                    val floatBuffer = FloatArray(totalRead)
                    for (i in 0 until totalRead) {
                        floatBuffer[i] = shortBuffer[i] / 32768.0f
                    }

                    val startTime = System.currentTimeMillis()
                    val result = whisperLib.transcribeData(contextPtr, floatBuffer)
                    val elapsed = System.currentTimeMillis() - startTime

                    withContext(Dispatchers.Main) {
                        onTranscriptionResult("$result [${elapsed}ms]")
                    }
                }
            }
        }
    }

    fun stopRecording() {
        isRecording = false
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
    }
}
