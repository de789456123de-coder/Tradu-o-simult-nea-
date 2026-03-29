package com.seuprojeto.translator

import android.util.Log

object AppLogger {
    private const val TAG = "TraductorApp"
    fun log(msg: String) { Log.d(TAG, msg) }
}
