package com.seuprojeto.translator

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AppLogger {
    private var logFile: File? = null
    private val dateFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())

    fun init(context: Context) {
        // Cria o arquivo na pasta acessível do Android
        val dir = context.getExternalFilesDir(null)
        if (dir != null) {
            logFile = File(dir, "raio_x_arquitetura.txt")
            // Limpa o log antigo toda vez que abrir o app para não virar um livro
            if (logFile?.exists() == true) logFile?.delete()
            logFile?.createNewFile()
            
            log("=== INICIANDO RAIO-X DO TRADUTOR ===")
        }
    }

    fun log(message: String) {
        // Pega exatamente a Thread e a hora exata
        val threadName = Thread.currentThread().name
        val time = dateFormat.format(Date())
        val logMessage = "[$time] [Thread: $threadName] $message\n"
        
        Log.i("RaioX", logMessage) // Mostra no log padrão também
        
        try {
            logFile?.let {
                FileWriter(it, true).use { writer ->
                    writer.append(logMessage)
                }
            }
        } catch (e: Exception) {
            // Ignora erro de gravação para não travar o app
        }
    }
}
