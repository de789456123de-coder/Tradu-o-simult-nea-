package com.seuprojeto.translator

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
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
    private var isContinuousMode = false
    private var lastDetectedLang = ""
    private var currentContext = ContextManager.ConversationContext.GENERAL
    private var leftLangCode = "pt"; private var rightLangCode = "en"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        leftLangCode = intent.getStringExtra("LEFT_LANG_CODE") ?: "pt"
        rightLangCode = intent.getStringExtra("RIGHT_LANG_CODE") ?: "en"
        val contextName = intent.getStringExtra("SELECTED_CONTEXT") ?: "GENERAL"
        currentContext = try { ContextManager.ConversationContext.valueOf(contextName) } catch(e: Exception) { ContextManager.ConversationContext.GENERAL }

        translationManager = TranslationManager()
        geminiManager = GeminiManager()
        audioManager = AudioChannelManager(this)
        audioManager.init(leftLangCode, rightLangCode) { runOnUiThread { isAudioReady = true } }
        speechManager = SpeechManager(this)

        lifecycleScope.launch {
            setStatus("📡 Conectando ao Gemini...")
            val ok1 = translationManager.prepareOfflineModel(leftLangCode, rightLangCode)
            val ok2 = translationManager.prepareOfflineModel(rightLangCode, leftLangCode)
            
            val test = geminiManager.translateWithContext("Oi", "pt", "en", "Teste")
            if (!test.contains("Erro")) setStatus("✨ Gemini Online (${currentContext.emoji})")
            else setStatus("✅ Modo Offline (${currentContext.emoji})")
        }

        findViewById<Button>(R.id.btn_listen).setOnClickListener {
            if (!isContinuousMode) {
                isContinuousMode = true
                (it as Button).text = "⏹ Parar Conversa"
                speechManager.startListeningContinuous(if(leftLangCode=="pt")"pt-BR" else "en-US", if(rightLangCode=="en")"en-US" else "pt-BR")
            } else {
                isContinuousMode = false; speechManager.stopListening()
                (it as Button).text = "▶ Iniciar Conversa"
                setStatus("● Pausado")
            }
        }

        speechManager.onSpeechResult = { text ->
            lifecycleScope.launch {
                setStatus("🔍 Analisando...")
                val detected = translationManager.detectLanguageSmart(text, leftLangCode, rightLangCode, lastDetectedLang, currentContext)
                lastDetectedLang = detected
                val target = if (detected == leftLangCode) rightLangCode else leftLangCode
                
                setStatus("✨ Gemini Traduzindo...")
                var res = geminiManager.translateWithContext(text, detected, target, currentContext.instruction)
                if (res.contains("Erro")) res = translationManager.translate(text, detected, target, currentContext)

                runOnUiThread {
                    findViewById<TextView>(if (detected == leftLangCode) R.id.tv_left else R.id.tv_right).text = text
                    findViewById<TextView>(if (detected == leftLangCode) R.id.tv_right else R.id.tv_left).text = res
                }

                if (isAudioReady) {
                    speechManager.stopListening()
                    if (detected == leftLangCode) audioManager.speakRight(res) else audioManager.speakLeft(res)
                    delay((res.length * 85L) + 1200L)
                    if (isContinuousMode) speechManager.startListeningContinuous("pt-BR", "en-US")
                }
                setStatus("🎙 ${currentContext.emoji} Ouvindo...")
            }
        }
    }

    private fun setStatus(msg: String) { runOnUiThread { findViewById<TextView>(R.id.tv_status).text = msg } }
}
