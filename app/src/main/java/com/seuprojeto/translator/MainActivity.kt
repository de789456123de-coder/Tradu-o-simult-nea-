package com.seuprojeto.translator

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var audioManager: AudioChannelManager
    private lateinit var speechManager: SpeechManager
    private lateinit var whisperManager: WhisperManager
    private lateinit var translationManager: TranslationManager

    private var isAudioReady = false
    private var isListening = false
    private var modelsReady = false
    private var lastDetectedLang = ""

    private val API_KEY = "AIzaSyAmTZS9c0xiaJZMe62s_AgsONhOsyboMFI"

    private var leftLangCode = "pt"
    private var rightLangCode = "en"
    private var leftLangName = "Português"
    private var rightLangName = "Inglês"
    private var currentContext = ContextManager.ConversationContext.GENERAL

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
        } catch (e: Exception) { ContextManager.ConversationContext.GENERAL }

        updateLabels()
        requestMicPermission()

        translationManager = TranslationManager(API_KEY)
        audioManager = AudioChannelManager(this)
        audioManager.init(leftLang = leftLangCode, rightLang = rightLangCode,
            onReady = { runOnUiThread { isAudioReady = true } })

        speechManager = SpeechManager(this)
        speechManager.init(leftLangCode, rightLangCode)

        whisperManager = WhisperManager(this)
        whisperManager.onStatusUpdate = { msg -> setStatus(msg) }

        lifecycleScope.launch {
            setStatus("⬇️ Preparando offline...")
            val ok1 = translationManager.prepareOfflineModel(leftLangCode, rightLangCode)
            val ok2 = translationManager.prepareOfflineModel(rightLangCode, leftLangCode)
            modelsReady = ok1 && ok2
            val whisperOk = whisperManager.init()
            val ctx = if (currentContext != ContextManager.ConversationContext.GENERAL)
                " · ${currentContext.emoji}" else ""
            setStatus(if (modelsReady && whisperOk) "✅ Offline + Whisper$ctx"
                      else if (modelsReady) "✅ Offline$ctx"
                      else "✅ Online$ctx")
        }

        // Texto parcial em tempo real
        speechManager.onPartialSpeech = { partial ->
            runOnUiThread { findViewById<TextView>(R.id.tv_left).text = "💬 $partial" }
        }

        // Resultado do STT nativo — rápido!
        speechManager.onSpeechResult = { text, _ ->
            lifecycleScope.launch {
                setStatus("🔍 Detectando idioma...")

                // Usa Whisper para detectar idioma OU TranslationManager
                val detectedLang = if (whisperManager.isReady()) {
                    whisperManager.detectLanguage(leftLangCode, rightLangCode)
                } else {
                    translationManager.detectLanguageSmart(
                        text, leftLangCode, rightLangCode, lastDetectedLang, currentContext)
                }
                lastDetectedLang = detectedLang

                setStatus("🔄 Traduzindo...")

                if (detectedLang == leftLangCode) {
                    runOnUiThread {
                        setActiveCard(left = true)
                        animateText(findViewById(R.id.tv_left), text)
                    }
                    val translated = translationManager.translate(
                        text, leftLangCode, rightLangCode, currentContext)
                    runOnUiThread { animateText(findViewById(R.id.tv_right), translated) }
                    if (isAudioReady) audioManager.speakRight(translated)
                } else {
                    runOnUiThread {
                        setActiveCard(left = false)
                        animateText(findViewById(R.id.tv_right), text)
                    }
                    val translated = translationManager.translate(
                        text, rightLangCode, leftLangCode, currentContext)
                    runOnUiThread { animateText(findViewById(R.id.tv_left), translated) }
                    if (isAudioReady) audioManager.speakLeft(translated)
                }

                runOnUiThread { resetActiveCards() }
                setStatus("🎙 Ouvindo...")
            }
        }

        speechManager.onListeningState = { active ->
            runOnUiThread { if (isListening) setMicIcon(active) }
        }

        findViewById<Button>(R.id.btn_listen).setOnClickListener {
            if (!isListening) {
                speechManager.startListening()
                isListening = true
                lastDetectedLang = ""
                findViewById<Button>(R.id.btn_listen).apply {
                    text = "⏹ Parar"
                    setBackgroundResource(R.drawable.btn_mic_active)
                }
                setStatus("🎙 Ouvindo...")
            } else {
                speechManager.stopListening()
                isListening = false
                lastDetectedLang = ""
                resetActiveCards()
                findViewById<Button>(R.id.btn_listen).apply {
                    text = "▶ Iniciar Conversa"
                    setBackgroundResource(R.drawable.btn_mic_inactive)
                }
                setStatus("● Pausado")
                setMicIcon(false)
            }
        }

        findViewById<ImageButton>(R.id.btn_swap).setOnClickListener {
            val wasListening = isListening
            if (wasListening) { speechManager.stopListening(); isListening = false }

            val tmpCode = leftLangCode; val tmpName = leftLangName
            leftLangCode = rightLangCode; leftLangName = rightLangName
            rightLangCode = tmpCode; rightLangName = tmpName

            lastDetectedLang = ""
            updateLabels()
            speechManager.init(leftLangCode, rightLangCode)
            audioManager.init(leftLang = leftLangCode, rightLang = rightLangCode,
                onReady = { runOnUiThread { isAudioReady = true } })

            lifecycleScope.launch {
                setStatus("⬇️ Preparando...")
                translationManager.prepareOfflineModel(leftLangCode, rightLangCode)
                translationManager.prepareOfflineModel(rightLangCode, leftLangCode)
                setStatus("🔄 Trocado!")
                if (wasListening) {
                    speechManager.startListening(); isListening = true
                    setStatus("🎙 Ouvindo...")
                }
            }
        }
    }

    private fun animateText(view: TextView, text: String) {
        val anim = AnimationUtils.loadAnimation(this, R.anim.slide_up)
        view.startAnimation(anim)
        view.text = text
    }

    private fun setActiveCard(left: Boolean) {
        val pulseAnim = AnimationUtils.loadAnimation(this, R.anim.pulse)
        if (left) {
            findViewById<CardView>(R.id.card_left).setCardBackgroundColor(0xFF1A1A3E.toInt())
            findViewById<CardView>(R.id.card_right).setCardBackgroundColor(0xFF0F1A12.toInt())
            findViewById<View>(R.id.indicator_left).apply { visibility = View.VISIBLE; startAnimation(pulseAnim) }
            findViewById<View>(R.id.indicator_right).apply { visibility = View.INVISIBLE; clearAnimation() }
        } else {
            findViewById<CardView>(R.id.card_right).setCardBackgroundColor(0xFF1A2E1A.toInt())
            findViewById<CardView>(R.id.card_left).setCardBackgroundColor(0xFF12122A.toInt())
            findViewById<View>(R.id.indicator_right).apply { visibility = View.VISIBLE; startAnimation(pulseAnim) }
            findViewById<View>(R.id.indicator_left).apply { visibility = View.INVISIBLE; clearAnimation() }
        }
    }

    private fun resetActiveCards() {
        findViewById<CardView>(R.id.card_left).setCardBackgroundColor(0xFF12122A.toInt())
        findViewById<CardView>(R.id.card_right).setCardBackgroundColor(0xFF0F1A12.toInt())
        findViewById<View>(R.id.indicator_left).apply { visibility = View.INVISIBLE; clearAnimation() }
        findViewById<View>(R.id.indicator_right).apply { visibility = View.INVISIBLE; clearAnimation() }
    }

    private fun setMicIcon(active: Boolean) {
        val icon = findViewById<TextView>(R.id.tv_mic_icon)
        icon.text = if (active) "🎙" else "●"
        icon.textSize = if (active) 14f else 10f
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
            ActivityCompat.requestPermissions(this,
                arrayOf(Manifest.permission.RECORD_AUDIO), 1)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        audioManager.release()
        speechManager.release()
        whisperManager.release()
        translationManager.release()
    }
}
