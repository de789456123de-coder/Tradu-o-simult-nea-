package com.seuprojeto.translator

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class SetupActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_setup)

        // Escaneia a tela inteira procurando os textos para transformá-los em botões
        val root = findViewById<ViewGroup>(android.R.id.content)
        attachListeners(root)
    }

    private fun attachListeners(view: View) {
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) attachListeners(view.getChildAt(i))
        } else if (view is TextView) {
            val text = view.text.toString().uppercase()
            
            // Botão de Iniciar
            if (text.contains("INICIAR CONVERSA")) {
                val clickable = view.parent as? View ?: view
                clickable.setOnClickListener {
                    val intent = Intent(this, MainActivity::class.java)
                    intent.putExtra("SELECTED_CONTEXT", ContextManager.ConversationContext.GERAL.name)
                    startActivity(intent)
                }
                return
            }

            // Botões de Contexto (Médico, Advogado, etc)
            ContextManager.ConversationContext.values().forEach { ctx ->
                if (text == ctx.label.uppercase()) {
                    val clickable = view.parent as? View ?: view
                    clickable.setOnClickListener {
                        val intent = Intent(this, MainActivity::class.java)
                        intent.putExtra("SELECTED_CONTEXT", ctx.name)
                        startActivity(intent)
                    }
                }
            }
        }
    }
}
