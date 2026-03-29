package com.seuprojeto.translator

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView

class SetupActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_setup)

        // Configuração dos botões baseada na sua print da grade
        setupContextButton(R.id.btn_context_general, ContextManager.ConversationContext.GERAL)
        setupContextButton(R.id.btn_context_med_pac, ContextManager.ConversationContext.MEDICO_PACIENTE)
        setupContextButton(R.id.btn_context_med_med, ContextManager.ConversationContext.MEDICO_MEDICO)
        setupContextButton(R.id.btn_context_emergencia, ContextManager.ConversationContext.EMERGENCIA_MEDICA)
        setupContextButton(R.id.btn_context_negocios, ContextManager.ConversationContext.REUNIAO_NEGOCIOS)
        setupContextButton(R.id.btn_context_tech, ContextManager.ConversationContext.TECNOLOGIA_TI)
        // Adicione os outros IDs de botões conforme o seu XML aqui...

        findViewById<Button>(R.id.btn_start_main).setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            intent.putExtra("SELECTED_CONTEXT", ContextManager.ConversationContext.GERAL.name)
            startActivity(intent)
        }
    }

    private fun setupContextButton(id: Int, context: ContextManager.ConversationContext) {
        try {
            findViewById<CardView>(id).setOnClickListener {
                val intent = Intent(this, MainActivity::class.java)
                intent.putExtra("SELECTED_CONTEXT", context.name)
                startActivity(intent)
            }
        } catch (e: Exception) { /* Botão ainda não implementado no XML */ }
    }
}
