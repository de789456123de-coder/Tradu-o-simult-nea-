#include <jni.h>
#include <string>
#include <android/log.h>
#include "whisper.h"

#define TAG "WhisperJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_seuprojeto_translator_WhisperLib_initContext(JNIEnv *env, jobject thiz, jstring model_path) {
    const char *path = env->GetStringUTFChars(model_path, nullptr);
    LOGI("Carregando modelo: %s", path);
    struct whisper_context_params cparams = whisper_context_default_params();
    struct whisper_context *ctx = whisper_init_from_file_with_params(path, cparams);
    env->ReleaseStringUTFChars(model_path, path);
    if (ctx == nullptr) {
        LOGE("Falha ao inicializar Whisper");
        return 0;
    }
    LOGI("Modelo carregado!");
    return reinterpret_cast<jlong>(ctx);
}

JNIEXPORT jstring JNICALL
Java_com_seuprojeto_translator_WhisperLib_transcribeData(JNIEnv *env, jobject thiz, jlong context_ptr, jfloatArray audio_data) {
    auto *ctx = reinterpret_cast<struct whisper_context *>(context_ptr);
    if (ctx == nullptr) return env->NewStringUTF("error|Contexto nulo");

    jsize audio_len = env->GetArrayLength(audio_data);
    jfloat *audio_elements = env->GetFloatArrayElements(audio_data, nullptr);

    whisper_full_params wparams = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    wparams.language       = "auto";
    wparams.print_progress = false;
    wparams.print_special  = false;
    wparams.print_realtime = false;
    wparams.n_threads      = 4;

    // Filtros anti-alucinação
    wparams.no_context     = true;
    wparams.single_segment = true;
    wparams.entropy_thold  = 2.8f;
    wparams.logprob_thold  = -1.0f;

    LOGI("Transcrevendo %d samples...", audio_len);
    int ret = whisper_full(ctx, wparams, audio_elements, audio_len);
    env->ReleaseFloatArrayElements(audio_data, audio_elements, JNI_ABORT);

    if (ret != 0) {
        LOGE("Falha whisper_full: %d", ret);
        return env->NewStringUTF("error|Falha na transcrição");
    }

    const int lang_id = whisper_full_lang_id(ctx);
    std::string lang_str = whisper_lang_str(lang_id);

    const int n_segments = whisper_full_n_segments(ctx);
    std::string result_text = "";
    for (int i = 0; i < n_segments; ++i) {
        result_text += whisper_full_get_segment_text(ctx, i);
    }

    // Remove espaço inicial comum no Whisper
        result_text = result_text.substr(1);
    }

    LOGI("Resultado [%s]: %s", lang_str.c_str(), result_text.c_str());
    std::string final_result = lang_str + "|" + result_text;
    return env->NewStringUTF(final_result.c_str());
}

JNIEXPORT void JNICALL
Java_com_seuprojeto_translator_WhisperLib_freeContext(JNIEnv *env, jobject thiz, jlong context_ptr) {
    auto *ctx = reinterpret_cast<struct whisper_context *>(context_ptr);
    if (ctx != nullptr) {
        whisper_free(ctx);
        LOGI("Whisper liberado");
    }
}

} // extern "C"
