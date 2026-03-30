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

        findViewById<Button>(R.id.btn_start)?.setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java).apply {
                putExtra("LEFT_LANG_CODE", leftLangCode)
                putExtra("RIGHT_LANG_CODE", rightLangCode)
                putExtra("LEFT_LANG_NAME", leftLangName)
                putExtra("RIGHT_LANG_NAME", rightLangName)
                putExtra("CONTEXT", selectedContext.name)
            })
        }
    }
}
