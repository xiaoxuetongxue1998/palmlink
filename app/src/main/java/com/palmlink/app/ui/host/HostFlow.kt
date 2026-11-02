package com.palmlink.app.ui.host

import android.app.Activity
import android.content.Context
import android.media.projection.MediaProjectionManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.LocalContext
import com.palmlink.app.net.HostSession
import com.palmlink.app.net.HostStage

/** 被控端流程导航：由 HostSession 的状态机驱动 */
@Composable
fun HostFlow(host: HostSession, stage: HostStage, onExit: () -> Unit) {
    val ctx = LocalContext.current
    val mpm = remember(ctx) {
        ctx.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as? MediaProjectionManager
    }
    val projLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { res ->
        val data = res.data
        if (res.resultCode == Activity.RESULT_OK && data != null && mpm != null) {
            host.grant(res.resultCode, data, ctx)
        } else {
            // 用户在系统弹窗上点了取消：视作拒绝本次控制请求
            host.deny()
        }
    }

    when (stage) {
        HostStage.Guide -> HostGuideScreen(
            onStart = { host.start() },
            onBack = onExit,
            onOpenA11y = {
                runCatching {
                    ctx.startActivity(
                        android.content.Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)
                            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
            }
        )
        HostStage.Starting, HostStage.Waiting, HostStage.Requested ->
            HostWaitingScreen(
                host = host,
                stage = stage,
                onBack = onExit,
                onAllow = {
                    mpm?.createScreenCaptureIntent()?.let { projLauncher.launch(it) }
                }
            )
        HostStage.Active -> HostActiveScreen(host, onBack = onExit)
        HostStage.Error -> HostErrorScreen(
            msg = host.errorMsg.collectAsState().value,
            onRetry = { host.start() },
            onBack = onExit
        )
    }
}
