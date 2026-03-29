package com.seuprojeto.translator

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
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
    private var lastDetectedLang = ""
    private var activeLanguageCode = ""
    private var isLeftTalking = true

    

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
        
        translationManager = TranslationManager()
        geminiManager = GeminiManager()
        audioManager = AudioChannelManager(this)
        audioManager.init(leftLang = leftLangCode, rightLang = rightLangCode,
            onReady = { runOnUiThread { isAudioReady = true } })

        speechManager = SpeechManager(this)

        lifecycleScope.launch {
            setStatus("⬇️ Baixando dicionários offline...")
            val ok1 = translationManager.prepareOfflineModel(leftLangCode, rightLangCode)
            val ok2 = translationManager.prepareOfflineModel(rightLangCode, leftLangCode)
            modelsReady = ok1 && ok2
            setStatus(if (modelsReady) "✅ Sistema Pronto" else "❌ Erro nos Modelos")
        }

        findViewById<Button>(R.id.btn_listen).setOnClickListener {
            if (!modelsReady) {
                Toast.makeText(this, "Aguarde o download...", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            if (!isContinuousMode) {
                isContinuousMode = true
                lastDetectedLang = ""
                (it as Button).text = "⏹ Parar Conversa"
                it.setBackgroundResource(R.drawable.btn_mic_active)
                
                val ptCode = getLangCodeForSpeech(leftLangCode)
                val enCode = getLangCodeForSpeech(rightLangCode)
                speechManager.startListeningContinuous(ptCode, enCode)
            } else {
                isContinuousMode = false
                speechManager.stopListening()
                (it as Button).text = "▶ Iniciar Conversa"
                it.setBackgroundResource(R.drawable.btn_mic_inactive)
                setStatus("● Pausado")
            }
        }

        findViewById<CardView>(R.id.card_left).setOnClickListener {
            if (!modelsReady) return@setOnClickListener
            isContinuousMode = false
            findViewById<Button>(R.id.btn_listen).text = "▶ Iniciar Conversa"
            
            isLeftTalking = true
            activeLanguageCode = getLangCodeForSpeech(leftLangCode)
            setActiveCard(left = true)
            speechManager.startListening(activeLanguageCode)
        }

        findViewById<CardView>(R.id.card_right).setOnClickListener {
            if (!modelsReady) return@setOnClickListener
            isContinuousMode = false
            findViewById<Button>(R.id.btn_listen).text = "▶ Iniciar Conversa"
            
            isLeftTalking = false
            activeLanguageCode = getLangCodeForSpeech(rightLangCode)
            setActiveCard(left = false)
            speechManager.startListening(activeLanguageCode)
        }

        speechManager.onListeningState = { active ->
            runOnUiThread { 
                setMicIcon(active)
                if (active) {
                    if (isContinuousMode) setStatus("🎙 Ouvindo ambiente...")
                    else setStatus("🎙 Ouvindo ${if (isLeftTalking) leftLangName else rightLangName}...")
                }
            }
        }

        speechManager.onError = { msg ->
            runOnUiThread { 
                if (isContinuousMode) {
                    if (msg != "Não entendi" && msg != "Silêncio...") setStatus(msg)
                    // LOOP BLINDADO: Se der "Não entendi" ou "Silêncio", ele finge que nada aconteceu e religa a escuta!
                    val ptCode = getLangCodeForSpeech(leftLangCode)
                    val enCode = getLangCodeForSpeech(rightLangCode)
                    
                    // Delay de 100ms para a placa de áudio do celular não travar com religações muito rápidas
                    Handler(Looper.getMainLooper()).postDelayed({
                        if (isContinuousMode) speechManager.startListeningContinuous(ptCode, enCode)
                    }, 600)
                } else {
                    // Se estiver no modo manual, mostra o erro e para
                    setStatus(msg)
                    resetActiveCards()
                }
            }
        }

        speechManager.onPartialSpeech = { partial ->
            runOnUiThread { 
                val tv = findViewById<TextView>(if (isContinuousMode) R.id.tv_left else (if (isLeftTalking) R.id.tv_left else R.id.tv_right))
                tv.text = "💬 $partial" 
            }
        }

        speechManager.onSpeechResult = { text ->
            lifecycleScope.launch {
                setStatus("🔍 Analisando...")

                val detectedLang = if (isContinuousMode) {
                    if (msg != "Não entendi" && msg != "Silêncio...") setStatus(msg)
                    translationManager.detectLanguageSmart(text, leftLangCode, rightLangCode, lastDetectedLang, currentContext)
                } else {
                    if (isLeftTalking) leftLangCode else rightLangCode
                }
                
                lastDetectedLang = detectedLang
                isLeftTalking = (detectedLang == leftLangCode)
                
                val targetCode = if (isLeftTalking) rightLangCode else leftLangCode
                val sourceTv = findViewById<TextView>(if (isLeftTalking) R.id.tv_left else R.id.tv_right)
                val targetTv = findViewById<TextView>(if (isLeftTalking) R.id.tv_right else R.id.tv_left)

                runOnUiThread { 
                    setActiveCard(isLeftTalking)
                    animateText(sourceTv, text) 
                }

                setStatus("🔄 Traduzindo...")
                val translated = translationManager.translate(text, detectedLang, targetCode, currentContext)
                
                runOnUiThread { animateText(targetTv, translated) }
                
                if (isAudioReady) {
                    speechManager.stopListening() 
                    if (isLeftTalking) audioManager.speakRight(translated) else audioManager.speakLeft(translated)
                    
                    delay((translated.length * 85L) + 1200L)
                    
                    if (isContinuousMode) {
                    if (msg != "Não entendi" && msg != "Silêncio...") setStatus(msg)
                        val ptCode = getLangCodeForSpeech(leftLangCode)
                        val enCode = getLangCodeForSpeech(rightLangCode)
                        speechManager.startListeningContinuous(ptCode, enCode)
                    }
                }

                runOnUiThread { 
                    if (!isContinuousMode) resetActiveCards()
                    setStatus(if (isContinuousMode) "🎙 Reativando microfone..." else "✅ Traduzido.")
                }
            }
        }

        findViewById<ImageButton>(R.id.btn_swap).setOnClickListener {
            val wasContinuous = isContinuousMode
            isContinuousMode = false
            speechManager.stopListening()
            
            val tmpCode = leftLangCode; val tmpName = leftLangName
            leftLangCode = rightLangCode; leftLangName = rightLangName
            rightLangCode = tmpCode; rightLangName = tmpName

            updateLabels()
            audioManager.init(leftLang = leftLangCode, rightLang = rightLangCode,
                onReady = { 
                    runOnUiThread { 
                        isAudioReady = true
                        if (wasContinuous) findViewById<Button>(R.id.btn_listen).performClick()
                    } 
                })
        }
    }
    
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
