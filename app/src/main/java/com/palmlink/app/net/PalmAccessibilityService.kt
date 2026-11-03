package com.palmlink.app.net

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.SystemClock
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

    // ---------------- 手势聚合 ----------------
    // 无障碍的 dispatchGesture 只能一次性注入「一条完整手势」，不能像真实手指那样持续流式下发。
    // 因此这里把一次触摸的 down/move/up 全部攒成一条路径，抬手时一次性执行：
    //   · 路径包含所有中间点，滑动轨迹才会跟手（不是直线插值）
    //   · 时长取用户实际按住的时间，系统才能算出正确速度，翻页/惯性滚动才会生效
    // （如果每段 move 单独注入，就是几十个互不相干的微型滑动，系统永远不会触发 fling）

    private val pts = ArrayList<DoubleArray>(128)
    private var downAt = 0L

    /** 手指按下：记录起点 */
    fun beginGesture(xn: Double, yn: Double) {
        pts.clear()
        pts.add(doubleArrayOf(xn, yn))
        downAt = SystemClock.uptimeMillis()
    }

    /** 手指移动：累积轨迹点（过滤抖动，避免点数爆炸） */
    fun extendGesture(xn: Double, yn: Double) {
        val last = pts.lastOrNull()
        if (last == null) {
            beginGesture(xn, yn)
            return
        }
        val dx = xn - last[0]
        val dy = yn - last[1]
        if (dx * dx + dy * dy < 0.000004) return
        pts.add(doubleArrayOf(xn, yn))
        // 只保留起点 + 最近的轨迹，防止长时间拖动撑爆
        if (pts.size > 240) pts.removeAt(1)
    }

    /** 手指抬起：整段轨迹一次性注入 */
    fun endGesture(xn: Double, yn: Double) {
        if (pts.isEmpty()) pts.add(doubleArrayOf(xn, yn))
        val last = pts.last()
        if (last[0] != xn || last[1] != yn) pts.add(doubleArrayOf(xn, yn))

        val held = (SystemClock.uptimeMillis() - downAt).coerceIn(0L, 3000L)
        val (w, h) = screenSize()

        val first = pts.first()
        val end = pts.last()
        val dxPx = (end[0] - first[0]) * w
        val dyPx = (end[1] - first[1]) * h
        val dist = kotlin.math.hypot(dxPx, dyPx)

        // 侧边滑动返回：起点贴着左/右屏幕边缘，且明显是「向内水平划」。
        // 这种手势如果照原样注入，很容易被系统的手势导航边缘区吞掉（或被 vivo 的
        // 智能侧边栏抢走），对方屏幕可能毫无反应。直接执行系统返回则 100% 命中，
        // 效果跟真机侧滑返回完全一致。
        if (isEdgeBack(first[0], dxPx, dyPx, w)) {
            pts.clear()
            performGlobalAction(GLOBAL_ACTION_BACK)
            return
        }

        // 坐标必须落在屏幕内：手势越界时 dispatchGesture 会直接失败
        fun fx(v: Double): Float = (v * w).toFloat().coerceIn(0f, (w - 1).toFloat())
        fun fy(v: Double): Float = (v * h).toFloat().coerceIn(0f, (h - 1).toFloat())

        val path = Path()
        path.moveTo(fx(first[0]), fy(first[1]))
        if (pts.size == 1) {
            // 路径只有 moveTo 时 StrokeDescription 会认为无效，补一个极小位移
            path.lineTo(fx(first[0]) + 1f, fy(first[1]) + 1f)
        } else {
            for (i in 1 until pts.size) {
                path.lineTo(fx(pts[i][0]), fy(pts[i][1]))
            }
        }

        val duration = when {
            // 几乎没位移 + 时间短 => 点击
            dist < 12.0 && held < 220 -> 60L
            // 几乎没位移 + 时间长 => 长按
            dist < 12.0 -> held.coerceIn(480, 900)
            // 滑动：按真实速度执行（保留甩动惯性），但最长 320ms，
            // 否则抬手后还要"重放"整段时长，手感会拖
            else -> held.coerceIn(80, 320)
        }
        dispatchPath(path, duration)
        pts.clear()
    }

    /** 是否是「从屏幕左/右边缘向内水平划」的返回手势 */
    private fun isEdgeBack(startXn: Double, dxPx: Double, dyPx: Double, w: Int): Boolean {
        val travel = kotlin.math.abs(dxPx)
        if (travel < w * 0.04f) return false            // 位移太小，更像点击
        if (travel < kotlin.math.abs(dyPx) * 1.5f) return false  // 竖向为主，不是返回
        return when {
            startXn <= 0.06 -> dxPx > 0                 // 左边缘往右划
            startXn >= 0.94 -> dxPx < 0                 // 右边缘往左划
            else -> false
        }
    }

    private fun dispatchPath(path: Path, durationMs: Long) {
        val gesture = GestureDescription.Builder()
            .addStroke(
                GestureDescription.StrokeDescription(path, 0, durationMs.coerceIn(20, 1500))
            )
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
