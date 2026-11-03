package com.palmlink.app.net

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import kotlinx.coroutines.CompletableDeferred

/**
 * 屏幕采集前台服务。
 * Android 14+ 要求 MediaProjection 运行期间必须存在 mediaProjection 类型的前台服务，
 * 所以先把它拉起来并 startForeground，WebRtcHost 再用授权数据创建采集器。
 */
class CaptureService : Service() {

    companion object {
        /** 服务完成 startForeground 后 complete(true)，采集端 await 它 */
        @Volatile
        var ready: CompletableDeferred<Boolean> = newDeferred()

        private const val CH_ID = "palmlink_capture"
        private const val NOTIF_ID = 42

        private fun newDeferred(): CompletableDeferred<Boolean> = CompletableDeferred()

        fun start(ctx: Context, resultCode: Int, data: Intent) {
            ready = newDeferred()
            val i = Intent(ctx, CaptureService::class.java)
                .putExtra("code", resultCode)
                .putExtra("data", data)
            ContextCompat.startForegroundService(ctx, i)
        }

        /** 停止采集服务。注意用 stopService：Android 12+ 从后台 startForegroundService 会直接抛异常崩溃 */
        fun stop(ctx: Context) {
            runCatching { ctx.stopService(Intent(ctx, CaptureService::class.java)) }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        ready = newDeferred()
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 契约优先：startForegroundService 后必须 5 秒内 startForeground，
        // 所以第一件事就是挂通知，所有业务校验都放到这之后。
        // （此前校验 code < 0 会把 RESULT_OK == -1 误判为无效数据，
        //   导致 stopSelf 先于 startForeground，系统直接抛
        //   ForegroundServiceDidNotStartInTimeException 闪退）
        createChannel()
        val notif: Notification = NotificationCompat.Builder(this, CH_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("掌驭正在共享屏幕")
            .setContentText("对方正在查看与操作这台手机")
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
        val type = if (Build.VERSION.SDK_INT >= 29) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
        } else 0
        ServiceCompat.startForeground(this, NOTIF_ID, notif, type)
        ready.complete(true)

        // 无效启动（缺投屏数据）：先完成前台契约再退出
        val data = intent?.let { IntentCompat.getParcelableExtra(it, "data", Intent::class.java) }
        if (data == null) {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun createChannel() {
        val nm = getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(CH_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CH_ID, "屏幕共享", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }
}
