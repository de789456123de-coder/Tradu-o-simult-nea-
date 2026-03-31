package com.seuprojeto.translator

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var audioManager: AudioChannelManager
    private lateinit var speechManager: SpeechManager
    private lateinit var translationManager: TranslationManager
    private lateinit var geminiManager: GeminiManager

    private var isAudioReady = false
    private var modelsReady = false
    private var isContinuousMode = false
    private var isLeftTalking = true
    private var lastDetectedLang = ""
    private var geminiEnabled = false  // OFF por padrão
    private var currentContext = ContextManager.ConversationContext.GENERAL

    private var leftLangCode = "pt"
    private var rightLangCode = "en"
    private var leftLangName = "Português"
    private var rightLangName = "Inglês"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        leftLangCode  = intent.getStringExtra("LEFT_LANG_CODE")  ?: "pt"
        rightLangCode = intent.getStringExtra("RIGHT_LANG_CODE") ?: "en"
        leftLangName  = intent.getStringExtra("LEFT_LANG_NAME")  ?: "Português"
        rightLangName = intent.getStringExtra("RIGHT_LANG_NAME") ?: "Inglês"

        currentContext = try {
            ContextManager.ConversationContext.valueOf(
                intent.getStringExtra("CONTEXT") ?: "GENERAL")
        } catch (e: Exception) { ContextManager.ConversationContext.GENERAL }

        updateLabels()
        requestMicPermission()

        translationManager = TranslationManager()
        geminiManager = GeminiManager()
        audioManager = AudioChannelManager(this)
        audioManager.init(leftLang = leftLangCode, rightLang = rightLangCode,
            onReady = { runOnUiThread { isAudioReady = true } })
        speechManager = SpeechManager(this)

        lifecycleScope.launch {
            setStatus("⬇️ Preparando offline...")
            val ok1 = translationManager.prepareOfflineModel(leftLangCode, rightLangCode)
            val ok2 = translationManager.prepareOfflineModel(rightLangCode, leftLangCode)
            modelsReady = ok1 && ok2
            setStatus("✅ ${currentContext.emoji} Pronto! Toque para falar.")
        }

        // === BOTÃO GEMINI ON/OFF ===
        val btnGemini = findViewById<Button>(R.id.btn_gemini)
        btnGemini?.apply {
            text = "🤖 Gemini OFF"
            setBackgroundColor(0xFF333333.toInt())
            setOnClickListener {
                geminiEnabled = !geminiEnabled
                if (geminiEnabled) {
                    text = "✨ Gemini ON"
                    setBackgroundColor(0xFF7C4DFF.toInt())
                    setStatus("✨ Gemini ativado — ${currentContext.emoji} ${currentContext.name}")
                } else {
                    text = "🤖 Gemini OFF"
                    setBackgroundColor(0xFF333333.toInt())
                    setStatus("🔄 Modo offline (ML Kit)")
                }
            }
        }

        // === WALKIE-TALKIE ===
        findViewById<CardView>(R.id.card_left).setOnClickListener {
            if (!modelsReady) return@setOnClickListener
            isLeftTalking = true
            setActiveCard(left = true)
            speechManager.startListening(getLangCode(leftLangCode))
            setStatus("🎙 $leftLangName...")
        }

        findViewById<CardView>(R.id.card_right).setOnClickListener {
            if (!modelsReady) return@setOnClickListener
            isLeftTalking = false
            setActiveCard(left = false)
            speechManager.startListening(getLangCode(rightLangCode))
            setStatus("🎙 $rightLangName...")
        }

        // === BOTÃO CONTÍNUO ===
        findViewById<Button>(R.id.btn_listen).setOnClickListener {
            if (!isContinuousMode) {
                isContinuousMode = true
                lastDetectedLang = ""
                (it as Button).text = "⏹ Parar"
                speechManager.startListeningContinuous(
                    getLangCode(leftLangCode), getLangCode(rightLangCode))
                setStatus("🎙 Ouvindo...")
            } else {
                isContinuousMode = false
                speechManager.stopListening()
                (it as Button).text = "▶ Iniciar Conversa"
                resetActiveCards()
                setStatus("● Pausado")
            }
        }

        speechManager.onPartialSpeech = { partial ->
            runOnUiThread {
                val tvId = if (isLeftTalking) R.id.tv_left else R.id.tv_right
                findViewById<TextView>(tvId).text = "💬 $partial"
            }
        }

        speechManager.onListeningState = { active ->
            runOnUiThread { setMicIcon(active) }
        }

        speechManager.onError = { _ ->
            runOnUiThread { resetActiveCards() }
            if (isContinuousMode) {
                Handler(Looper.getMainLooper()).postDelayed({
                    if (isContinuousMode) speechManager.startListeningContinuous(
                        getLangCode(leftLangCode), getLangCode(rightLangCode))
                }, 600)
            }
        }

        speechManager.onSpeechResult = { text ->
            lifecycleScope.launch {
                val detectedLang = if (isContinuousMode) {
                    translationManager.detectLanguageSmart(
                        text, leftLangCode, rightLangCode, lastDetectedLang, currentContext)
                } else {
                    if (isLeftTalking) leftLangCode else rightLangCode
                }
                lastDetectedLang = detectedLang

                val sourceCode = detectedLang
                val targetCode = if (detectedLang == leftLangCode) rightLangCode else leftLangCode
                val sourceTvId = if (detectedLang == leftLangCode) R.id.tv_left else R.id.tv_right
                val targetTvId = if (detectedLang == leftLangCode) R.id.tv_right else R.id.tv_left

                runOnUiThread {
                    setActiveCard(left = detectedLang == leftLangCode)
                    animateText(findViewById(sourceTvId), text)
                }

                val translated: String
                if (geminiEnabled) {
                    setStatus("✨ Gemini traduzindo...")
                    val result = geminiManager.translateWithContext(
                        text, sourceCode, targetCode, currentContext.instruction)
                    if (result.startsWith("Erro")) {
                        setStatus("🔄 Gemini falhou: $result")
                        translated = translationManager.translate(text, sourceCode, targetCode, currentContext)
                    } else {
                        translated = result
                        setStatus("✨ Gemini ✓")
                    }
                } else {
                    setStatus("🔄 Traduzindo...")
                    translated = translationManager.translate(text, sourceCode, targetCode, currentContext)
                }

                runOnUiThread { animateText(findViewById(targetTvId), translated) }

                if (isAudioReady) {
                    speechManager.stopListening()
                    if (detectedLang == leftLangCode) audioManager.speakRight(translated)
                    else audioManager.speakLeft(translated)
                    delay((translated.length * 80L) + 1000L)
                }

                runOnUiThread { resetActiveCards() }

                if (isContinuousMode) {
                    speechManager.startListeningContinuous(
                        getLangCode(leftLangCode), getLangCode(rightLangCode))
                    setStatus(if (geminiEnabled) "✨ Gemini ON — Ouvindo..." else "🎙 Ouvindo...")
                } else {
                    setStatus("✅ Traduzido. Toque para falar.")
                }
            }
        }

        findViewById<ImageButton>(R.id.btn_swap).setOnClickListener {
            speechManager.stopListening()
            isContinuousMode = false
            val tmpCode = leftLangCode; val tmpName = leftLangName
            leftLangCode = rightLangCode; leftLangName = rightLangName
            rightLangCode = tmpCode; rightLangName = tmpName
            updateLabels()
            audioManager.init(leftLang = leftLangCode, rightLang = rightLangCode,
                onReady = { runOnUiThread { isAudioReady = true } })
            (findViewById<Button>(R.id.btn_listen)).text = "▶ Iniciar Conversa"
            setStatus("🔄 Trocado!")
        }
    }

    private fun getLangCode(code: String) = when(code) {
        "pt" -> "pt-BR"; "en" -> "en-US"; "es" -> "es-ES"
        "fr" -> "fr-FR"; "de" -> "de-DE"; "it" -> "it-IT"
        "nl" -> "nl-NL"; "he" -> "he-IL"; "ja" -> "ja-JP"
        "zh" -> "zh-CN"; "ko" -> "ko-KR"; "ru" -> "ru-RU"
        "ar" -> "ar-SA"; "hi" -> "hi-IN"
        else -> "en-US"
    }

    private fun animateText(view: TextView, text: String) {
        view.startAnimation(AnimationUtils.loadAnimation(this, R.anim.slide_up))
        view.text = text
    }

    private fun setActiveCard(left: Boolean) {
        val pulse = AnimationUtils.loadAnimation(this, R.anim.pulse)
        if (left) {
            findViewById<CardView>(R.id.card_left).setCardBackgroundColor(0xFF1A1A3E.toInt())
            findViewById<CardView>(R.id.card_right).setCardBackgroundColor(0xFF0F1A12.toInt())
            findViewById<View>(R.id.indicator_left).apply { visibility = View.VISIBLE; startAnimation(pulse) }
            findViewById<View>(R.id.indicator_right).apply { visibility = View.INVISIBLE; clearAnimation() }
        } else {
            findViewById<CardView>(R.id.card_right).setCardBackgroundColor(0xFF1A2E1A.toInt())
            findViewById<CardView>(R.id.card_left).setCardBackgroundColor(0xFF12122A.toInt())
            findViewById<View>(R.id.indicator_right).apply { visibility = View.VISIBLE; startAnimation(pulse) }
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
