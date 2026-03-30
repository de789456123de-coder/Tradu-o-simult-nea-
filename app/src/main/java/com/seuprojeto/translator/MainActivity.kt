package com.seuprojeto.translator

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private lateinit var audioManager: AudioChannelManager
    private lateinit var speechManager: SpeechManager
    private lateinit var translationManager: TranslationManager
    private lateinit var geminiManager: GeminiManager
    
    private var isAudioReady = false
    private var isContinuous = false
    private var lastDetectedLang = ""
    private var currentContext = ContextManager.ConversationContext.GERAL
    private var leftLangCode = "pt"
    private var rightLangCode = "en"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        leftLangCode = intent.getStringExtra("LEFT_LANG_CODE") ?: "pt"
        rightLangCode = intent.getStringExtra("RIGHT_LANG_CODE") ?: "en"
        val contextName = intent.getStringExtra("SELECTED_CONTEXT") ?: "GERAL"
        currentContext = try { ContextManager.ConversationContext.valueOf(contextName) } catch(e: Exception) { ContextManager.ConversationContext.GERAL }

        speechManager = SpeechManager(this)
        translationManager = TranslationManager()
        geminiManager = GeminiManager()
        
        audioManager = AudioChannelManager(this)
        audioManager.init(leftLangCode, rightLangCode) { runOnUiThread { isAudioReady = true } }

        lifecycleScope.launch {
            setStatus("📡 Verificando sistemas...")
            translationManager.prepareOfflineModel(leftLangCode, rightLangCode)
            translationManager.prepareOfflineModel(rightLangCode, leftLangCode)
            setStatus("✨ ${currentContext.emoji} Pronto")
        }

        // Blindagem com Safe Call (?.): Se o botão não existir no layout, não fecha o app.
        findViewById<Button>(R.id.btn_listen)?.setOnClickListener {
            val btn = it as Button
            if (!isContinuous) {
                isContinuous = true
                btn.text = "⏹ Parar"
                speechManager.startListeningContinuous(if(leftLangCode=="pt")"pt-BR" else "en-US", if(rightLangCode=="en")"en-US" else "pt-BR")
            } else {
                isContinuous = false
                speechManager.stopListening()
                btn.text = "▶ Iniciar"
            }
        }

        speechManager.onSpeechResult = { text ->
            lifecycleScope.launch {
                setStatus("🔍 Traduzindo...")
                val detected = translationManager.detectLanguageSmart(text, leftLangCode, rightLangCode, lastDetectedLang, currentContext)
                lastDetectedLang = detected
                val target = if (detected == leftLangCode) rightLangCode else leftLangCode
                
                var res = geminiManager.translateWithContext(text, detected, target, currentContext.instruction)
                if (res.contains("Erro")) res = translationManager.translate(text, detected, target, currentContext)

                runOnUiThread {
                    // Mais blindagem: atualiza os textos apenas se os TextViews existirem na tela
                    findViewById<TextView>(if (detected == leftLangCode) R.id.tv_left else R.id.tv_right)?.text = text
                    findViewById<TextView>(if (detected == leftLangCode) R.id.tv_right else R.id.tv_left)?.text = res
                }

                if (isAudioReady) {
                    speechManager.stopListening()
                    if (detected == leftLangCode) audioManager.speakRight(res) else audioManager.speakLeft(res)
                    delay((res.length * 85L) + 1200L)
                    if (isContinuous) speechManager.startListeningContinuous(if(leftLangCode=="pt")"pt-BR" else "en-US", if(rightLangCode=="en")"en-US" else "pt-BR")
                }
                setStatus("🎙 Ouvindo...")
            }
        }
    }

    private fun setStatus(m: String) { 
        runOnUiThread { findViewById<TextView>(R.id.tv_status)?.text = m } 
    }
    
    override fun onDestroy() {
        super.onDestroy()
        audioManager.release()
        speechManager.release()
        translationManager.release()
    }
}
