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
    private var modelsReady = false
    
    // Variável para saber qual lado está a falar no momento (Walkie-Talkie)
    private var activeLanguageCode = ""
    private var isLeftTalking = true

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
        
        // Configura o botão principal apenas como um aviso
        findViewById<Button>(R.id.btn_listen).apply {
            text = "Toque nos painéis para falar"
            isEnabled = false
        }

        translationManager = TranslationManager(API_KEY)
        audioManager = AudioChannelManager(this)
        audioManager.init(leftLang = leftLangCode, rightLang = rightLangCode,
            onReady = { runOnUiThread { isAudioReady = true } })

        speechManager = SpeechManager(this)

        lifecycleScope.launch {
            setStatus("⬇️ Preparando modelos...")
            val ok1 = translationManager.prepareOfflineModel(leftLangCode, rightLangCode)
            val ok2 = translationManager.prepareOfflineModel(rightLangCode, leftLangCode)
            modelsReady = ok1 && ok2
            setStatus(if (modelsReady) "✅ Modo Direcional Pronto" else "❌ Erro nos Modelos")
        }

        // EVENTOS DE CLIQUE DOS PAINÉIS (Modo Walkie-Talkie)
        findViewById<CardView>(R.id.card_left).setOnClickListener {
            if (!modelsReady) return@setOnClickListener
            isLeftTalking = true
            activeLanguageCode = getLangCodeForSpeech(leftLangCode)
            setActiveCard(left = true)
            speechManager.startListening(activeLanguageCode)
        }

        findViewById<CardView>(R.id.card_right).setOnClickListener {
            if (!modelsReady) return@setOnClickListener
            isLeftTalking = false
            activeLanguageCode = getLangCodeForSpeech(rightLangCode)
            setActiveCard(left = false)
            speechManager.startListening(activeLanguageCode)
        }

        // LÓGICA DE CAPTURA E TRADUÇÃO
        speechManager.onListeningState = { active ->
            runOnUiThread { 
                setMicIcon(active)
                if (active) setStatus("🎙 Ouvindo ${if (isLeftTalking) leftLangName else rightLangName}...")
            }
        }

        speechManager.onError = { msg ->
            runOnUiThread { 
                setStatus(msg)
                resetActiveCards()
            }
        }

        speechManager.onPartialSpeech = { partial ->
            runOnUiThread { 
                val tv = findViewById<TextView>(if (isLeftTalking) R.id.tv_left else R.id.tv_right)
                tv.text = "💬 $partial" 
            }
        }

        speechManager.onSpeechResult = { text ->
            lifecycleScope.launch {
                setStatus("🔄 Traduzindo...")

                val sourceCode = if (isLeftTalking) leftLangCode else rightLangCode
                val targetCode = if (isLeftTalking) rightLangCode else leftLangCode
                val sourceTv = findViewById<TextView>(if (isLeftTalking) R.id.tv_left else R.id.tv_right)
                val targetTv = findViewById<TextView>(if (isLeftTalking) R.id.tv_right else R.id.tv_left)

                runOnUiThread { animateText(sourceTv, text) }

                val translated = translationManager.translate(text, sourceCode, targetCode, currentContext)
                
                runOnUiThread { animateText(targetTv, translated) }
                
                if (isAudioReady) {
                    if (isLeftTalking) audioManager.speakRight(translated) else audioManager.speakLeft(translated)
                }

                runOnUiThread { 
                    resetActiveCards()
                    setStatus("✅ Traduzido. Toque para falar.")
                }
            }
        }

        findViewById<ImageButton>(R.id.btn_swap).setOnClickListener {
            speechManager.stopListening()
            
            val tmpCode = leftLangCode; val tmpName = leftLangName
            leftLangCode = rightLangCode; leftLangName = rightLangName
            rightLangCode = tmpCode; rightLangName = tmpName

            updateLabels()
            audioManager.init(leftLang = leftLangCode, rightLang = rightLangCode,
                onReady = { runOnUiThread { isAudioReady = true } })
        }
    }
    
    // Converte o código "pt" para "pt-BR" exigido pelo SpeechRecognizer nativo
    private fun getLangCodeForSpeech(shortCode: String): String {
        return when(shortCode) {
            "pt" -> "pt-BR"; "en" -> "en-US"; "es" -> "es-ES"
            "fr" -> "fr-FR"; "de" -> "de-DE"; "it" -> "it-IT"
            else -> "en-US"
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

    private fun setStatus(msg: String) { runOnUiThread { findViewById<TextView>(R.id.tv_status).text = msg } }

    private fun requestMicPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
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
