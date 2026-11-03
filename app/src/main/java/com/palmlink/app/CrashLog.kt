package com.palmlink.app

import java.io.File

/**
 * 崩溃现场记录。
 * 没有 adb 的情况下，这是拿到真实堆栈的唯一办法：进程挂掉前写文件，
 * 下次启动时在角色选择页展示，用户截图即可。
 */
object CrashLog {

    private const val FILE = "crash-last.txt"

    @Synchronized
    fun write(dir: File, tag: String, t: Throwable) {
        runCatching {
            val text = buildString {
                appendLine("时间：${java.text.SimpleDateFormat("MM-dd HH:mm:ss", java.util.Locale.CHINA).format(java.util.Date())}")
                appendLine("位置：$tag")
                appendLine("线程：${t.javaClass.simpleName}")
                appendLine()
                append(t.stackTraceToString().take(3000))
            }
            File(dir, FILE).writeText(text)
        }
    }

    fun read(dir: File): String? {
        val f = File(dir, FILE)
        if (!f.exists()) return null
        return runCatching { f.readText() }.getOrNull()?.takeIf { it.isNotBlank() }
    }

    fun clear(dir: File) {
        runCatching { File(dir, FILE).delete() }
    }
}
