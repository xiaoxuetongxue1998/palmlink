package com.palmlink.app.net

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent

/**
 * 无障碍服务：把控制端发来的触控/导航指令注入本机。
 * 用户必须在系统设置里手动开启（Android 安全模型要求）。
 */
class PalmAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile
        var instance: PalmAccessibilityService? = null
            private set
    }

    override fun onServiceConnected() {
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    /** 本机真实屏幕尺寸（含系统栏） */
    private fun screenSize(): IntArray {
        if (Build.VERSION.SDK_INT >= 30) {
            getSystemService(WindowManager::class.java)?.let { wm ->
                val b: Rect = wm.maximumWindowMetrics.bounds
                return intArrayOf(b.width(), b.height())
            }
        }
        return intArrayOf(resources.displayMetrics.widthPixels, resources.displayMetrics.heightPixels)
    }

    fun tap(xn: Double, yn: Double) {
        stroke(xn, yn, xn, yn, 60)
    }

    /** 归一化坐标的滑动/拖动；起点终点相同时即为点击 */
    fun stroke(x1n: Double, y1n: Double, x2n: Double, y2n: Double, durationMs: Long) {
        val (w, h) = screenSize()
        val x1 = (x1n * w).toFloat()
        val y1 = (y1n * h).toFloat()
        val x2 = (x2n * w).toFloat()
        val y2 = (y2n * h).toFloat()
        val path = Path().apply {
            moveTo(x1, y1)
            lineTo(x2, y2)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, durationMs.coerceIn(60, 900)))
            .build()
        dispatchGesture(gesture, null, null)
    }

    fun nav(key: String): Boolean {
        val action = when (key) {
            "back" -> GLOBAL_ACTION_BACK
            "home" -> GLOBAL_ACTION_HOME
            "recents" -> GLOBAL_ACTION_RECENTS
            else -> return false
        }
        return performGlobalAction(action)
    }
}
