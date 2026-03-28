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
    private var currentChannel = "LEFT"
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
            val ctx = if (currentContext != ContextManager.ConversationContext.GENERAL)
                " · ${currentContext.emoji}" else ""
            setStatus(if (modelsReady) "✅ Offline$ctx" else "✅ Online$ctx")
        }

        // Texto parcial em tempo real
        speechManager.onPartialSpeech = { partial, channel ->
            runOnUiThread {
                if (channel == "LEFT") {
                    setActiveCard(left = true)
                    findViewById<TextView>(R.id.tv_left).text = "💬 $partial"
                } else {
                    setActiveCard(left = false)
                    findViewById<TextView>(R.id.tv_right).text = "💬 $partial"
                }
            }
        }

        // Canal alternado — mostra visualmente qual está ativo
        speechManager.onChannelChanged = { channel ->
            currentChannel = channel
            runOnUiThread {
                setActiveCard(left = channel == "LEFT")
                val label = if (channel == "LEFT") leftLangName else rightLangName
                setStatus("🎙 $label falando...")
            }
        }

        // Resultado final — traduz direto pelo canal sem detecção
        speechManager.onSpeechDetected = { text, channel ->
            lifecycleScope.launch {
                setStatus("🔄 Traduzindo...")

                if (channel == "LEFT") {
                    // Esquerdo falou → traduz para direito
                    runOnUiThread { animateText(findViewById(R.id.tv_left), text) }
                    val translated = translationManager.translate(
                        text, leftLangCode, rightLangCode, currentContext)
                    runOnUiThread { animateText(findViewById(R.id.tv_right), translated) }
                    if (isAudioReady) audioManager.speakRight(translated)
                } else {
                    // Direito falou → traduz para esquerdo
                    runOnUiThread { animateText(findViewById(R.id.tv_right), text) }
                    val translated = translationManager.translate(
                        text, rightLangCode, leftLangCode, currentContext)
                    runOnUiThread { animateText(findViewById(R.id.tv_left), translated) }
                    if (isAudioReady) audioManager.speakLeft(translated)
                }

                runOnUiThread { resetActiveCards() }
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
                currentChannel = "LEFT"
                setActiveCard(left = true)
                findViewById<Button>(R.id.btn_listen).apply {
                    text = "⏹ Parar"
                    setBackgroundResource(R.drawable.btn_mic_active)
                }
                setStatus("🎙 $leftLangName falando...")
            } else {
                speechManager.stopListening()
                speechManager.resetChannel()
                isListening = false
                resetActiveCards()
                findViewById<Button>(R.id.btn_listen).apply {
                    text = "▶ Iniciar Conversa"
                    setBackgroundResource(R.drawable.btn_mic_inactive)
                }
                setStatus("● Pausado")
            }
        }

        findViewById<ImageButton>(R.id.btn_swap).setOnClickListener {
            val wasListening = isListening
            if (wasListening) { speechManager.stopListening(); isListening = false }

            val tmpCode = leftLangCode; val tmpName = leftLangName
            leftLangCode = rightLangCode; leftLangName = rightLangName
            rightLangCode = tmpCode; rightLangName = tmpName

            speechManager.resetChannel()
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
                    speechManager.startListening()
                    isListening = true
                    setStatus("🎙 $leftLangName falando...")
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
