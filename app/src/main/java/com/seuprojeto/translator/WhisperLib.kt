package com.seuprojeto.translator

class WhisperLib {
    companion object {
        init {
            System.loadLibrary("ggml-base")
            System.loadLibrary("ggml-cpu")
            System.loadLibrary("ggml")
            System.loadLibrary("whisper")
            System.loadLibrary("whisper_jni")
        }
    }

    external fun initContext(modelPath: String): Long
    external fun transcribeData(contextPtr: Long, audioData: FloatArray): String
    external fun freeContext(contextPtr: Long)
}
