package com.palmlink.app.net

import android.os.Build

fun deviceName(): String {
    val mfg = Build.MANUFACTURER.replaceFirstChar { it.uppercase() }
    return "$mfg ${Build.MODEL}".trim()
}

/** 人工可录入的配对码（去掉易混淆的 0/O/1/I/L） */
private const val TOKEN_CHARS = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"

fun randomToken(): String = (1..8).map { TOKEN_CHARS.random() }.joinToString("")
