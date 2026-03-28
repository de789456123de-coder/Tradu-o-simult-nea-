package com.seuprojeto.translator

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var audioManager: AudioChannelManager
    private lateinit var speechManager: SpeechManager
    private lateinit var translationManager: TranslationManager
    private var isAudioReady = false
    private var isListening = false
    private var modelsReady = false

    private val API_KEY = "AIzaSyAmTZS9c0xiaJZMe62s_AgsONhOsyboMFI"

    private var leftLangCode = "pt"
    private var rightLangCode = "en"
    private var leftLangName = "Português"
    private var rightLangName = "Inglês"
    private var lastDetectedLang = ""
    private val recentLangs = mutableListOf<String>()
    private var currentContext = ContextManager.ConversationContext.GENERAL

    // Conta quantas vezes seguidas detectou o mesmo idioma
    // Se detectar o mesmo 3x seguidas sem traduzir, força alternância
    private var sameLanguageCount = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        leftLangCode  = intent.getStringExtra("LEFT_LANG_CODE")  ?: "pt"
        rightLangCode = intent.getStringExtra("RIGHT_LANG_CODE") ?: "en"
        leftLangName  = intent.getStringExtra("LEFT_LANG_NAME")  ?: "Português"
        rightLangName = intent.getStringExtra("RIGHT_LANG_NAME") ?: "Inglês"

        val contextName = intent.getStringExtra("CONTEXT") ?: "GENERAL"
        currentContext = try {
            ContextManager.ConversationContext.valueOf(contextName)
        } catch (e: Exception) {
            ContextManager.ConversationContext.GENERAL
        }

        updateLabels()
        requestMicPermission()

        translationManager = TranslationManager(API_KEY)
        audioManager = AudioChannelManager(this)
        audioManager.init(
            leftLang = leftLangCode,
            rightLang = rightLangCode,
            onReady = { runOnUiThread { isAudioReady = true } }
        )

        speechManager = SpeechManager(this)
        speechManager.init()

        lifecycleScope.launch {
            setStatus("⬇️ Preparando modo offline...")
            val ok1 = translationManager.prepareOfflineModel(leftLangCode, rightLangCode)
            val ok2 = translationManager.prepareOfflineModel(rightLangCode, leftLangCode)
            modelsReady = ok1 && ok2
            val ctxLabel = if (currentContext != ContextManager.ConversationContext.GENERAL)
                " · ${currentContext.emoji}" else ""
            setStatus(if (modelsReady) "✅ Offline$ctxLabel" else "✅ Online$ctxLabel")
        }

        speechManager.onPartialSpeech = { partial ->
            lifecycleScope.launch {
                val guessed = translationManager.detectLanguageSmart(
                    partial, leftLangCode, rightLangCode,
                    recentLangs.lastOrNull() ?: lastDetectedLang, currentContext
                )
                runOnUiThread {
                    if (guessed == leftLangCode)
                        findViewById<TextView>(R.id.tv_left).text = "💬 $partial"
                    else
                        findViewById<TextView>(R.id.tv_right).text = "💬 $partial"
                }
            }
        }

        speechManager.onSpeechDetected = { text, _ ->
            lifecycleScope.launch {
                setStatus("🔄 Traduzindo...")

                val activeContext = if (currentContext == ContextManager.ConversationContext.GENERAL)
                    ContextManager.detectContext(text) ?: currentContext
                else currentContext

                var detectedLang = translationManager.detectLanguageSmart(
                    text, leftLangCode, rightLangCode,
                    recentLangs.lastOrNull() ?: lastDetectedLang, activeContext
                )

                // Anti-bug: se detectou o mesmo idioma 2x seguidas, força o oposto
                if (detectedLang == lastDetectedLang) {
                    sameLanguageCount++
                    if (sameLanguageCount >= 2) {
                        detectedLang = if (detectedLang == leftLangCode) rightLangCode else leftLangCode
                        sameLanguageCount = 0
                    }
                } else {
                    sameLanguageCount = 0
                }

                recentLangs.add(detectedLang)
                if (recentLangs.size > 5) recentLangs.removeAt(0)
                lastDetectedLang = detectedLang

                if (detectedLang == leftLangCode) {
                    runOnUiThread { findViewById<TextView>(R.id.tv_left).text = text }
                    val translated = translationManager.translate(text, leftLangCode, rightLangCode, activeContext)
                    runOnUiThread { findViewById<TextView>(R.id.tv_right).text = translated }
                    if (isAudioReady) audioManager.speakRight(translated)
                } else {
                    runOnUiThread { findViewById<TextView>(R.id.tv_right).text = text }
                    val translated = translationManager.translate(text, rightLangCode, leftLangCode, activeContext)
                    runOnUiThread { findViewById<TextView>(R.id.tv_left).text = translated }
                    if (isAudioReady) audioManager.speakLeft(translated)
                }

                setStatus(if (modelsReady) "● Ouvindo (offline)" else "● Ouvindo")
            }
        }

        speechManager.onListeningState = { active ->
            runOnUiThread {
                if (isListening) setStatus(if (active) "🎙 Ouvindo..." else "⏳ Aguardando...")
            }
        }

        findViewById<Button>(R.id.btn_listen).setOnClickListener {
            if (!isListening) {
                speechManager.startListening()
                isListening = true
                sameLanguageCount = 0
                lastDetectedLang = ""
                recentLangs.clear()
                findViewById<Button>(R.id.btn_listen).text = "⏹ Parar"
                setStatus("🎙 Ouvindo...")
            } else {
                speechManager.stopListening()
                isListening = false
                recentLangs.clear()
                lastDetectedLang = ""
                sameLanguageCount = 0
                findViewById<Button>(R.id.btn_listen).text = "▶ Iniciar Conversa"
                setStatus("● Pausado")
            }
        }

        findViewById<ImageButton>(R.id.btn_swap).setOnClickListener {
            val wasListening = isListening
            if (wasListening) { speechManager.stopListening(); isListening = false }

            val tmpCode = leftLangCode; val tmpName = leftLangName
            leftLangCode = rightLangCode; leftLangName = rightLangName
            rightLangCode = tmpCode; rightLangName = tmpName

            recentLangs.clear(); lastDetectedLang = ""; sameLanguageCount = 0
            updateLabels()
            audioManager.init(leftLang = leftLangCode, rightLang = rightLangCode,
                onReady = { runOnUiThread { isAudioReady = true } })

            lifecycleScope.launch {
                setStatus("⬇️ Preparando...")
                val ok1 = translationManager.prepareOfflineModel(leftLangCode, rightLangCode)
                val ok2 = translationManager.prepareOfflineModel(rightLangCode, leftLangCode)
                modelsReady = ok1 && ok2
                setStatus("🔄 Trocado!")
                if (wasListening) {
                    speechManager.startListening(); isListening = true
                    setStatus("🎙 Ouvindo...")
                }
            }
        }
    }

    private fun updateLabels() {
        runOnUiThread {
            findViewById<TextView>(R.id.tv_left_label).text  = "🎧 $leftLangName"
            findViewById<TextView>(R.id.tv_right_label).text = "🎧 $rightLangName"
        }
    }

    private fun setStatus(msg: String) {
        runOnUiThread { findViewById<TextView>(R.id.tv_status).text = msg }
    }

    private fun requestMicPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 1)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        audioManager.release()
        speechManager.release()
        translationManager.release()
    }
}
