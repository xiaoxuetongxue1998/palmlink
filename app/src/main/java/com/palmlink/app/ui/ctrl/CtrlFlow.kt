package com.palmlink.app.ui.ctrl

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.palmlink.app.net.ControllerClient
import com.palmlink.app.net.CtrlStage
import com.palmlink.app.net.PairPayload

/** 控制端流程导航：扫码 -> 连接中 -> 远程控制 */
@Composable
fun CtrlFlow(
    client: ControllerClient?,
    onPayload: (PairPayload) -> Unit,
    onClear: () -> Unit
) {
    if (client == null) {
        ScanScreen(onPayload = onPayload)
    } else {
        val st by client.stage.collectAsState()
        when (st) {
            CtrlStage.Approved -> RemoteScreen(client, onExit = onClear)
            else -> ConnectingScreen(client, onClear = onClear)
        }
    }
}
