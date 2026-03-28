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
        } catch (e: Exception) { ContextManager.ConversationContext.GENERAL }

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
            setStatus("⬇️ Preparando offline...")
            val ok1 = translationManager.prepareOfflineModel(leftLangCode, rightLangCode)
            val ok2 = translationManager.prepareOfflineModel(rightLangCode, leftLangCode)
            modelsReady = ok1 && ok2
            val ctxLabel = if (currentContext != ContextManager.ConversationContext.GENERAL)
                " · ${currentContext.emoji}" else ""
            setStatus(if (modelsReady) "Offline$ctxLabel" else "Online$ctxLabel")
        }

        speechManager.onPartialSpeech = { partial ->
            lifecycleScope.launch {
                val guessed = translationManager.detectLanguageSmart(
                    partial, leftLangCode, rightLangCode,
                    recentLangs.lastOrNull() ?: lastDetectedLang, currentContext
                )
                runOnUiThread {
                    if (guessed == leftLangCode) {
                        setActiveCard(left = true)
                        animateText(findViewById(R.id.tv_left), "💬 $partial")
                    } else {
                        setActiveCard(left = false)
                        animateText(findViewById(R.id.tv_right), "💬 $partial")
                    }
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
                    runOnUiThread {
                        setActiveCard(left = true)
                        animateText(findViewById(R.id.tv_left), text)
                    }
                    val translated = translationManager.translate(text, leftLangCode, rightLangCode, activeContext)
                    runOnUiThread { animateText(findViewById(R.id.tv_right), translated) }
                    if (isAudioReady) audioManager.speakRight(translated)
                } else {
                    runOnUiThread {
                        setActiveCard(left = false)
                        animateText(findViewById(R.id.tv_right), text)
                    }
                    val translated = translationManager.translate(text, rightLangCode, leftLangCode, activeContext)
                    runOnUiThread { animateText(findViewById(R.id.tv_left), translated) }
                    if (isAudioReady) audioManager.speakLeft(translated)
                }

                runOnUiThread { resetActiveCards() }
                setStatus(if (modelsReady) "Offline" else "Online")
            }
        }

        speechManager.onListeningState = { active ->
            runOnUiThread {
                if (isListening) {
                    setStatus(if (active) "Ouvindo" else "Aguardando")
                    setMicIcon(active)
                }
            }
        }

        findViewById<Button>(R.id.btn_listen).setOnClickListener {
            if (!isListening) {
                speechManager.startListening()
                isListening = true
                sameLanguageCount = 0
                lastDetectedLang = ""
                recentLangs.clear()
                findViewById<Button>(R.id.btn_listen).apply {
                    text = "⏹ Parar"
                    setBackgroundResource(R.drawable.btn_mic_active)
                }
                setStatus("Ouvindo")
                setMicIcon(true)
            } else {
                speechManager.stopListening()
                isListening = false
                recentLangs.clear()
                lastDetectedLang = ""
                sameLanguageCount = 0
                resetActiveCards()
                findViewById<Button>(R.id.btn_listen).apply {
                    text = "▶ Iniciar Conversa"
                    setBackgroundResource(R.drawable.btn_mic_inactive)
                }
                setStatus("Pausado")
                setMicIcon(false)
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
                setStatus("Preparando...")
                val ok1 = translationManager.prepareOfflineModel(leftLangCode, rightLangCode)
                val ok2 = translationManager.prepareOfflineModel(rightLangCode, leftLangCode)
                modelsReady = ok1 && ok2
                setStatus("Trocado!")
                if (wasListening) {
                    speechManager.startListening(); isListening = true
                    setStatus("Ouvindo")
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
        val cardLeft  = findViewById<CardView>(R.id.card_left)
        val cardRight = findViewById<CardView>(R.id.card_right)
        val indLeft   = findViewById<View>(R.id.indicator_left)
        val indRight  = findViewById<View>(R.id.indicator_right)
        val pulseAnim = AnimationUtils.loadAnimation(this, R.anim.pulse)

        if (left) {
            cardLeft.setCardBackgroundColor(0xFF1A1A3E.toInt())
            cardRight.setCardBackgroundColor(0xFF0F1A12.toInt())
            indLeft.visibility = View.VISIBLE
            indLeft.startAnimation(pulseAnim)
            indRight.visibility = View.INVISIBLE
            indRight.clearAnimation()
        } else {
            cardRight.setCardBackgroundColor(0xFF1A2E1A.toInt())
            cardLeft.setCardBackgroundColor(0xFF12122A.toInt())
            indRight.visibility = View.VISIBLE
            indRight.startAnimation(pulseAnim)
            indLeft.visibility = View.INVISIBLE
            indLeft.clearAnimation()
        }
    }

    private fun resetActiveCards() {
        findViewById<CardView>(R.id.card_left).setCardBackgroundColor(0xFF12122A.toInt())
        findViewById<CardView>(R.id.card_right).setCardBackgroundColor(0xFF0F1A12.toInt())
        findViewById<View>(R.id.indicator_left).apply {
            visibility = View.INVISIBLE; clearAnimation()
        }
        findViewById<View>(R.id.indicator_right).apply {
            visibility = View.INVISIBLE; clearAnimation()
        }
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
