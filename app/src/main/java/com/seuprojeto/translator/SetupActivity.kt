package com.seuprojeto.translator

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

class SetupActivity : AppCompatActivity() {

    private var selectedContext = ContextManager.ConversationContext.GENERAL
    private var leftLangCode = "pt"
    private var rightLangCode = "en"
    private var leftLangName = "Português"
    private var rightLangName = "Inglês"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_setup)

        // Botão iniciar
        findViewById<Button>(R.id.btn_start)?.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java).apply {
                putExtra("LEFT_LANG_CODE", leftLangCode)
                putExtra("RIGHT_LANG_CODE", rightLangCode)
                putExtra("LEFT_LANG_NAME", leftLangName)
                putExtra("RIGHT_LANG_NAME", rightLangName)
                putExtra("CONTEXT", selectedContext.name)
            }
            startActivity(intent)
        }

        // Botões de contexto
        mapOf(
            R.id.btn_ctx_general     to ContextManager.ConversationContext.GENERAL,
            R.id.btn_ctx_medpaciente to ContextManager.ConversationContext.MEDICO_PACIENTE,
            R.id.btn_ctx_medmedico   to ContextManager.ConversationContext.MEDICO_MEDICO,
            R.id.btn_ctx_emergencia  to ContextManager.ConversationContext.EMERGENCIA,
            R.id.btn_ctx_negocios    to ContextManager.ConversationContext.NEGOCIOS,
            R.id.btn_ctx_amigos      to ContextManager.ConversationContext.AMIGOS,
            R.id.btn_ctx_turismo     to ContextManager.ConversationContext.TURISMO,
            R.id.btn_ctx_tecnologia  to ContextManager.ConversationContext.TECNOLOGIA
        ).forEach { (id, ctx) ->
            findViewById<Button>(id)?.setOnClickListener {
                selectedContext = ctx
                updateContextUI(ctx)
            }
        }
    }

    private fun updateContextUI(ctx: ContextManager.ConversationContext) {
        // Botão de iniciar mostra contexto selecionado
        findViewById<Button>(R.id.btn_start)?.text = "▶ Iniciar — ${ctx.emoji} ${ctx.name}"
    }
}
