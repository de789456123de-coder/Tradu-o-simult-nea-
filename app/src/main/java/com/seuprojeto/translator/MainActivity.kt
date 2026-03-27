package com.seuprojeto.translator

import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var audioManager: AudioChannelManager
    private var isReady = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        audioManager = AudioChannelManager(this)
        audioManager.init(
            localeLeft = Locale.ENGLISH,
            localeRight = Locale("pt", "BR"),
            onReady = {
                runOnUiThread {
                    isReady = true
                    Toast.makeText(this, "Pronto!", Toast.LENGTH_SHORT).show()
                }
            }
        )

        findViewById<Button>(R.id.btn_test_left).setOnClickListener {
            if (!isReady) return@setOnClickListener
            audioManager.speakLeft("Hello! This is the left channel.")
        }

        findViewById<Button>(R.id.btn_test_right).setOnClickListener {
            if (!isReady) return@setOnClickListener
            audioManager.speakRight("Olá! Este é o canal direito.")
        }

        findViewById<Button>(R.id.btn_test_both).setOnClickListener {
            if (!isReady) return@setOnClickListener
            audioManager.speakBoth("Left channel.", "Canal direito.")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        audioManager.release()
    }
}
