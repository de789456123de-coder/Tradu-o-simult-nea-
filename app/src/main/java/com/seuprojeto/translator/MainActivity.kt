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
    private var isLeftTalking = true

    // Contexto padrão
    private var currentContext = ContextManager.ConversationContext.GENERAL

    private var leftLangCode = "pt"; private var rightLangCode = "en"
    private var leftLangName = "Português"; private var rightLangName = "Inglês"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Configurações de idioma vindo da tela anterior
        leftLangCode  = intent.getStringExtra("LEFT_LANG_CODE")  ?: "pt"
        rightLangCode = intent.getStringExtra("RIGHT_LANG_CODE") ?: "en"
        
        // Pega o contexto selecionado na tela de botões (1000368949.jpg)
        val contextName = intent.getStringExtra("SELECTED_CONTEXT") ?: "GENERAL"
        currentContext = try { ContextManager.ConversationContext.valueOf(contextName) } 
                        catch(e: Exception) { ContextManager.ConversationContext.GENERAL }

        translationManager = TranslationManager()
        geminiManager = GeminiManager()
        audioManager = AudioChannelManager(this)
        audioManager.init(leftLangCode, rightLangCode) { runOnUiThread { isAudioReady = true } }
        speechManager = SpeechManager(this)

        lifecycleScope.launch {
            setStatus("⬇️ Preparando Offline...")
            val ok1 = translationManager.prepareOfflineModel(leftLangCode, rightLangCode)
            val ok2 = translationManager.prepareOfflineModel(rightLangCode, leftLangCode)
            modelsReady = ok1 && ok2
            setStatus("✅ ${currentContext.emoji} Pronto (${currentContext.name})")
        }

        findViewById<Button>(R.id.btn_listen).setOnClickListener {
            if (!isContinuousMode) {
                isContinuousMode = true
                (it as Button).text = "⏹ Parar Conversa"
                speechManager.startListeningContinuous(getLangCodeForSpeech(leftLangCode), getLangCodeForSpeech(rightLangCode))
            } else {
                isContinuousMode = false
                speechManager.stopListening()
                (it as Button).text = "▶ Iniciar Conversa"
                setStatus("● Pausado")
            }
        }

        speechManager.onSpeechResult = { text ->
            lifecycleScope.launch {
                setStatus("🔍 Analisando...")
                val detectedLang = if (isContinuousMode) {
                    translationManager.detectLanguageSmart(text, leftLangCode, rightLangCode, lastDetectedLang, currentContext)
                } else { if (isLeftTalking) leftLangCode else rightLangCode }

                lastDetectedLang = detectedLang
                val targetCode = if (detectedLang == leftLangCode) rightLangCode else leftLangCode
                
                setStatus("✨ Gemini Traduzindo...")
                
                // TENTA GEMINI ONLINE PRIMEIRO
                var translated = geminiManager.translateWithContext(
                    text, detectedLang, targetCode, currentContext.instruction
                )

                // SE FALHAR (SEM INTERNET), USA O ML KIT OFFLINE
                if (translated.contains("Erro")) {
                    setStatus("🔄 Offline (Sem Sinal)...")
                    translated = translationManager.translate(text, detectedLang, targetCode, currentContext)
                }

                runOnUiThread {
                    val tvSource = findViewById<TextView>(if (detectedLang == leftLangCode) R.id.tv_left else R.id.tv_right)
                    val tvTarget = findViewById<TextView>(if (detectedLang == leftLangCode) R.id.tv_right else R.id.tv_left)
                    tvSource.text = text
                    animateText(tvTarget, translated)
                }

                if (isAudioReady) {
                    speechManager.stopListening()
                    if (detectedLang == leftLangCode) audioManager.speakRight(translated) else audioManager.speakLeft(translated)
                    delay((translated.length * 85L) + 1200L)
                    if (isContinuousMode) speechManager.startListeningContinuous(getLangCodeForSpeech(leftLangCode), getLangCodeForSpeech(rightLangCode))
                }
                setStatus(if (isContinuousMode) "🎙 Ouvindo ambiente..." else "✅ Pronto")
            }
        }
        
        speechManager.onError = { msg ->
            if (isContinuousMode) {
                Handler(Looper.getMainLooper()).postDelayed({
                    if (isContinuousMode) speechManager.startListeningContinuous(getLangCodeForSpeech(leftLangCode), getLangCodeForSpeech(rightLangCode))
                }, 600)
            }
        }
    }

    private fun getLangCodeForSpeech(code: String) = if (code == "pt") "pt-BR" else "en-US"
    private fun setStatus(msg: String) { runOnUiThread { findViewById<TextView>(R.id.tv_status).text = msg } }
    private fun animateText(view: TextView, text: String) { view.text = text }
    private fun requestMicPermission() { if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 1) }
}
