package com.palmlink.app.ui.host

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import com.palmlink.app.net.HostSession
import com.palmlink.app.net.HostStage

/** 被控端流程导航：由 HostSession 的状态机驱动 */
@Composable
fun HostFlow(host: HostSession, stage: HostStage, onExit: () -> Unit) {
    when (stage) {
        HostStage.Guide -> HostGuideScreen(onStart = { host.start() }, onBack = onExit)
        HostStage.Starting, HostStage.Waiting, HostStage.Requested ->
            HostWaitingScreen(host, stage, onBack = onExit)
        HostStage.Active -> HostActiveScreen(host, onBack = onExit)
        HostStage.Error -> HostErrorScreen(
            msg = host.errorMsg.collectAsState().value,
            onRetry = { host.start() },
            onBack = onExit
        )
    }
}
