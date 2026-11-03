package com.palmlink.app.ui.host

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.zxing.BarcodeFormat
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import com.palmlink.app.net.HostSession
import com.palmlink.app.net.HostStage
import com.palmlink.app.net.WsJson
import com.palmlink.app.ui.BgBase
import com.palmlink.app.ui.Danger
import com.palmlink.app.ui.DangerDim
import com.palmlink.app.ui.Ok
import com.palmlink.app.ui.PalmButton
import com.palmlink.app.ui.PalmTopBar
import com.palmlink.app.ui.Primary
import com.palmlink.app.ui.PrimaryDim
import com.palmlink.app.ui.Surface1
import com.palmlink.app.ui.Surface2
import com.palmlink.app.ui.TextDim
import com.palmlink.app.ui.TextMain
import com.palmlink.app.ui.TextMuted
import com.palmlink.app.ui.TipBar
import kotlinx.coroutines.delay

// ---------- 授权向导（S3 简化版：仅提示，无系统授权操作） ----------

@Composable
fun HostGuideScreen(onStart: () -> Unit, onBack: () -> Unit, onOpenA11y: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(22.dp)
    ) {
        PalmTopBar("让这台手机被控制", onBack)
        Spacer(Modifier.height(8.dp))
        Text(
            "开始之前",
            color = TextMain,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = 34.sp
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "控制端会通过扫码连接到这台手机",
            color = TextMuted,
            fontSize = 13.sp
        )
        Spacer(Modifier.height(20.dp))

        StepCard(
            num = "1",
            title = "开启无障碍服务",
            desc = "用于接收对方发来的点击与滑动。系统设置 → 已下载的应用 → 掌驭 → 开启",
            tag = "必填"
        )
        Spacer(Modifier.height(10.dp))
        PalmButton("去开启无障碍设置", Modifier.fillMaxWidth(), primary = false, onClick = onOpenA11y)
        Spacer(Modifier.height(10.dp))
        StepCard(
            num = "2",
            title = "关闭电池优化",
            desc = "否则锁屏后系统可能断开连接。vivo 还需在设置里允许「后台高耗电」",
            tag = "建议"
        )
        Spacer(Modifier.height(14.dp))
        TipBar("两台手机必须连接同一个 WiFi。屏幕共享开始时，系统会弹出录制确认，点「立即开始」即可。")
        Spacer(Modifier.weight(1f))
        PalmButton("开始等待连接", Modifier.fillMaxWidth(), onClick = onStart)
    }
}

@Composable
private fun StepCard(num: String, title: String, desc: String, tag: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Surface1)
            .padding(16.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            Modifier
                .size(26.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(PrimaryDim),
            contentAlignment = Alignment.Center
        ) {
            Text(num, color = Primary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, color = TextMain, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(8.dp))
                Text(tag, color = TextDim, fontSize = 10.sp)
            }
            Spacer(Modifier.height(3.dp))
            Text(desc, color = TextMuted, fontSize = 12.sp, lineHeight = 18.sp)
        }
    }
}

// ---------- 等待连接（二维码 + 控制请求弹窗） ----------

@Composable
fun HostWaitingScreen(
    host: HostSession,
    stage: HostStage,
    onBack: () -> Unit,
    onAllow: () -> Unit
) {
    val payload by host.qrPayload.collectAsState()
    val peerName by host.peerName.collectAsState()
    var awaiting by remember { mutableStateOf(false) }

    // 一旦离开“请求中”状态（授权成功/被拒/超时），重置等待标记
    LaunchedEffect(stage) {
        if (stage != HostStage.Requested) awaiting = false
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(22.dp)
        ) {
            PalmTopBar("等待连接", onBack)
            Spacer(Modifier.height(4.dp))

            when (stage) {
                HostStage.Starting -> {
                    Spacer(Modifier.height(28.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(color = Primary, strokeWidth = 3.dp)
                    }
                    Spacer(Modifier.height(14.dp))
                    Text(
                        "正在启动服务…",
                        color = TextMuted, fontSize = 13.sp,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                }
                else -> {
                    val p = payload
                    if (p == null) {
                        Spacer(Modifier.height(28.dp))
                        Text("正在准备…", color = TextMuted, fontSize = 13.sp)
                    } else {
                        Spacer(Modifier.height(6.dp))
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            StatusPillLocal(if (stage == HostStage.Requested) "收到控制请求" else "等待控制端扫码")
                        }
                        Spacer(Modifier.height(18.dp))
                        QrCard(p.ip, p.port, p.token, content = WsJson.encodeToString(p))
                        Spacer(Modifier.height(16.dp))
                        TipBar("用控制端手机扫码，或在控制端手动输入上方信息。两台手机需在同一个 WiFi 下。")
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            Text(
                "本机：${payload?.name ?: ""}",
                color = TextDim,
                fontSize = 11.sp,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        }

        if (stage == HostStage.Requested) {
            RequestDialog(
                name = peerName,
                countdown = !awaiting,
                onAllow = {
                    awaiting = true
                    onAllow()
                },
                onDeny = host::deny
            )
        }
    }
}

@Composable
private fun StatusPillLocal(text: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(PrimaryDim)
            .padding(horizontal = 14.dp, vertical = 7.dp)
    ) {
        Text(text, color = Primary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun QrCard(ip: String, port: Int, token: String, content: String) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Surface1)
            .padding(18.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val bmp = remember(content) { qrBitmap(content) }
        Image(
            bitmap = bmp.asImageBitmap(),
            contentDescription = "配对二维码",
            Modifier.size(216.dp)
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "$ip : $port",
            color = TextMain, fontSize = 14.sp, fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "配对码  $token",
            color = TextMuted, fontSize = 12.sp, letterSpacing = 2.sp
        )
    }
}

private fun qrBitmap(content: String): android.graphics.Bitmap {
    val size = 480
    val bits: BitMatrix = QRCodeWriter().encode(
        content, BarcodeFormat.QR_CODE, size, size,
        mapOf(com.google.zxing.EncodeHintType.MARGIN to 1)
    )
    val bm = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
    for (x in 0 until size) for (y in 0 until size) {
        bm.setPixel(x, y, if (bits[x, y]) 0xFF0B0E12.toInt() else 0xFFFFFFFF.toInt())
    }
    return bm
}

@Composable
private fun RequestDialog(
    name: String?,
    countdown: Boolean,
    onAllow: () -> Unit,
    onDeny: () -> Unit
) {
    var left by remember { mutableIntStateOf(60) }
    LaunchedEffect(countdown) {
        if (!countdown) return@LaunchedEffect
        for (i in 59 downTo 0) {
            left = i
            delay(1000)
        }
        onDeny()
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xB3000000)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier
                .padding(28.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(Surface2)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(PrimaryDim),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    (name?.firstOrNull()?.toString() ?: "?"),
                    color = Primary, fontSize = 24.sp, fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.height(14.dp))
            Text(
                "${name ?: "有设备"} 请求控制这台手机",
                color = TextMain, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "允许后系统会请求屏幕录制，请点「立即开始」",
                color = TextMuted, fontSize = 12.sp
            )
            Spacer(Modifier.height(16.dp))
            LinearProgressIndicator(
                progress = { left / 60f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = Primary,
                trackColor = Surface1
            )
            Spacer(Modifier.height(18.dp))
            Row(Modifier.fillMaxWidth()) {
                PalmButton("拒绝", Modifier.weight(1f), primary = false, onClick = onDeny)
                Spacer(Modifier.width(12.dp))
                PalmButton("允许", Modifier.weight(1f), onClick = onAllow)
            }
        }
    }
}

// ---------- 正在被控 ----------

@Composable
fun HostActiveScreen(host: HostSession, onBack: () -> Unit) {
    val peer by host.peerName.collectAsState()
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            tick++
        }
    }
    val elapsed = ((System.currentTimeMillis() - host.connectedAt) / 1000).toInt()
    val mm = elapsed / 60
    val ss = elapsed % 60

    val pulse = rememberInfiniteTransition(label = "pulse")
    val alpha by pulse.animateFloat(
        initialValue = 0.9f,
        targetValue = 0.35f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "alpha"
    )

    Column(
        Modifier
            .fillMaxSize()
            .padding(22.dp)
    ) {
        PalmTopBar("正在被控制", onBack)
        Spacer(Modifier.weight(1f))
        Column(
            Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                Modifier
                    .size(96.dp)
                    .alpha(alpha)
                    .clip(RoundedCornerShape(30.dp))
                    .background(DangerDim),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(17.dp))
                        .background(Danger),
                    contentAlignment = Alignment.Center
                ) {
                    Text("控", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(18.dp))
            Text(
                "正在被 ${peer ?: "对方"} 控制",
                color = TextMain, fontSize = 19.sp, fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "%02d:%02d".format(mm, ss),
                color = TextMuted, fontSize = 13.sp
            )
        }
        Spacer(Modifier.weight(1f))
        val a11yOn = remember { com.palmlink.app.net.PalmAccessibilityService.instance != null }
        if (!a11yOn) {
            TipBar("无障碍服务没开：对方能看画面，但点不动。请到 设置 → 无障碍 → 掌驭 开启后重新连接。")
            Spacer(Modifier.height(10.dp))
        }
        val rtcErr by host.rtcError.collectAsState()
        if (rtcErr.isNotBlank()) {
            TipBar("投屏出错：$rtcErr")
            Spacer(Modifier.height(10.dp))
        }
        TipBar("屏幕正在共享给对方。锁屏会立即中断投屏；涉及支付、验证码的界面双方都可见。")
        Spacer(Modifier.height(14.dp))
        PalmButton("断开连接", Modifier.fillMaxWidth(), danger = true, onClick = host::disconnect)
    }
}

// ---------- 错误 ----------

@Composable
fun HostErrorScreen(msg: String, onRetry: () -> Unit, onBack: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(22.dp)
    ) {
        PalmTopBar("出错了", onBack)
        Spacer(Modifier.weight(1f))
        Text(msg, color = TextMuted, fontSize = 13.sp, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.weight(1f))
        PalmButton("重试", Modifier.fillMaxWidth(), onClick = onRetry)
        Spacer(Modifier.height(10.dp))
        PalmButton("返回", Modifier.fillMaxWidth(), primary = false, onClick = onBack)
    }
}
