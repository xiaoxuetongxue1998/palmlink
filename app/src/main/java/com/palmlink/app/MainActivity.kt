package com.palmlink.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.palmlink.app.net.ControllerClient
import com.palmlink.app.net.CtrlStage
import com.palmlink.app.net.HostSession
import com.palmlink.app.net.HostStage
import com.palmlink.app.net.PairPayload
import com.palmlink.app.net.WebRtcCtrl
import com.palmlink.app.ui.BgBase
import com.palmlink.app.ui.Danger
import com.palmlink.app.ui.OnPrimary
import com.palmlink.app.ui.PalmLinkTheme
import com.palmlink.app.ui.Primary
import com.palmlink.app.ui.PrimaryDim
import com.palmlink.app.ui.Surface1
import com.palmlink.app.ui.TextDim
import com.palmlink.app.ui.TextMain
import com.palmlink.app.ui.TextMuted
import com.palmlink.app.ui.ctrl.CtrlFlow
import com.palmlink.app.ui.host.HostFlow
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WebRtcCtrl.appContext = applicationContext
        enableEdgeToEdge()
        setContent {
            PalmLinkTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = BgBase) {
                    AppRoot()
                }
            }
        }
    }
}

@Composable
fun AppRoot() {
    var mode by rememberSaveable { mutableStateOf("role") } // role | host | ctrl
    val host = remember { HostSession() }
    var ctrl by remember { mutableStateOf<ControllerClient?>(null) }
    var flash by remember { mutableStateOf("") }

    val hostStage by host.stage.collectAsState()
    val client = ctrl
    val ctrlStage = if (client != null) client.stage.collectAsState().value else null

    LaunchedEffect(flash) {
        if (flash.isNotEmpty()) {
            delay(2400)
            flash = ""
        }
    }

    BackHandler(enabled = mode != "role") {
        when (mode) {
            "host" -> { host.shutdown(); mode = "role" }
            "ctrl" -> { client?.disconnect(); ctrl = null; mode = "role" }
        }
    }

    Box(Modifier.fillMaxSize()) {
        when (mode) {
            "role" -> RoleScreen(
                onHost = { mode = "host" },
                onCtrl = { mode = "ctrl" }
            )
            "host" -> HostFlow(
                host = host,
                stage = hostStage,
                onExit = { host.shutdown(); mode = "role" }
            )
            "ctrl" -> CtrlFlow(
                client = client,
                onPayload = { p -> ctrl = ControllerClient(p).also { it.start() } },
                onClear = { ctrl = null }
            )
        }

        if (flash.isNotEmpty()) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 34.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(Surface1)
                    .padding(horizontal = 18.dp, vertical = 10.dp)
            ) {
                Text(flash, color = TextMain, fontSize = 12.sp)
            }
        }
    }
}

// ---------- 角色选择 ----------

@Composable
private fun RoleScreen(onHost: () -> Unit, onCtrl: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(26.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            Modifier
                .size(62.dp)
                .clip(RoundedCornerShape(19.dp))
                .background(Primary),
            contentAlignment = Alignment.Center
        ) {
            Text("掌", color = OnPrimary, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(18.dp))
        Text("掌驭", color = TextMain, fontSize = 24.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 4.sp)
        Text("P A L M L I N K", color = TextDim, fontSize = 10.sp, letterSpacing = 3.sp)
        Spacer(Modifier.height(34.dp))

        RoleCard(
            icon = "控",
            title = "控制别人的手机",
            desc = "看到对方屏幕，点、滑、导航",
            tint = Primary,
            onClick = onCtrl
        )
        Spacer(Modifier.height(14.dp))
        RoleCard(
            icon = "被",
            title = "让这台手机被控制",
            desc = "共享屏幕，授权后由对方操作",
            tint = TextMain,
            onClick = onHost
        )

        Spacer(Modifier.height(30.dp))
        val ctx = LocalContext.current
        var crash by remember { mutableStateOf(CrashLog.read(ctx.filesDir)) }
        if (crash != null) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Surface1)
                    .clickable { CrashLog.clear(ctx.filesDir); crash = null }
                    .padding(12.dp)
            ) {
                Column {
                    Text(
                        "上次崩溃（点此清除，截图发给小强）",
                        color = Danger, fontSize = 11.sp, fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        crash!!.take(600),
                        color = TextMuted, fontSize = 9.5.sp, lineHeight = 13.sp
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
        }
        Text(
            "v0.3.1 · 局域网投屏 + 手势控制\n被控端请在系统设置中开启掌驭的无障碍服务",
            color = TextDim,
            fontSize = 11.sp,
            lineHeight = 17.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun RoleCard(icon: String, title: String, desc: String, tint: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Surface1)
            .clickable(onClick = onClick)
            .padding(20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(PrimaryDim),
            contentAlignment = Alignment.Center
        ) {
            Text(icon, color = tint, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(15.dp))
        Column {
            Text(title, color = TextMain, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(3.dp))
            Text(desc, color = TextMuted, fontSize = 12.sp)
        }
    }
}
