package com.palmlink.app.ui.ctrl

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.palmlink.app.net.ControllerClient
import com.palmlink.app.net.CtrlStage
import com.palmlink.app.net.PairPayload
import com.palmlink.app.net.WebRtcCtrl
import com.palmlink.app.net.WsJson
import com.palmlink.app.ui.Danger
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
import kotlinx.coroutines.flow.MutableStateFlow
import org.webrtc.SurfaceViewRenderer
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

// ---------- 扫码 ----------

@Composable
fun ScanScreen(onPayload: (PairPayload) -> Unit) {
    val ctx = LocalContext.current
    var hasCam by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { hasCam = it }

    var showManual by remember { mutableStateOf(false) }
    var mip by remember { mutableStateOf("") }
    var mport by remember { mutableStateOf("") }
    var mtoken by remember { mutableStateOf("") }
    var err by remember { mutableStateOf("") }
    var handled by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .padding(22.dp)
    ) {
        PalmTopBar("扫码配对")
        Text(
            "用这台手机扫描被控端显示的二维码",
            color = TextMuted, fontSize = 13.sp
        )
        Spacer(Modifier.height(16.dp))

        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFF0F141A))
        ) {
            if (hasCam) {
                CameraQrView { txt ->
                    if (handled) return@CameraQrView
                    val p = runCatching { WsJson.decodeFromString<PairPayload>(txt) }.getOrNull()
                    if (p != null && p.ip.isNotBlank() && p.port in 1..65535) {
                        handled = true
                        onPayload(p)
                    } else {
                        err = "二维码内容无法识别"
                    }
                }
                ViewfinderOverlay()
            } else {
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text("需要相机权限来扫码", color = TextMain, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "仅用于识别配对二维码，不会拍照或录像",
                        color = TextMuted, fontSize = 12.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(16.dp))
                    PalmButton("授予相机权限") { permLauncher.launch(Manifest.permission.CAMERA) }
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        if (!showManual) {
            PalmButton(
                "手动输入连接信息",
                Modifier.fillMaxWidth(),
                primary = false
            ) { showManual = true }
        } else {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Surface1)
                    .padding(14.dp)
            ) {
                OutlinedTextField(
                    value = mip,
                    onValueChange = { mip = it },
                    label = { Text("被控端 IP，如 192.168.1.23") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Row {
                    OutlinedTextField(
                        value = mport,
                        onValueChange = { mport = it.filter { c -> c.isDigit() } },
                        label = { Text("端口") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    OutlinedTextField(
                        value = mtoken,
                        onValueChange = { mtoken = it.uppercase() },
                        label = { Text("配对码") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(Modifier.height(10.dp))
                PalmButton(
                    "连接",
                    Modifier.fillMaxWidth()
                ) {
                    val port = mport.toIntOrNull() ?: 0
                    if (mip.isBlank() || port !in 1..65535 || mtoken.length < 4) {
                        err = "请填写完整：IP、端口和至少 4 位配对码"
                    } else {
                        handled = true
                        onPayload(PairPayload(name = "", ip = mip.trim(), port = port, token = mtoken.trim()))
                    }
                }
            }
        }

        if (err.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Text(err, color = Danger, fontSize = 12.sp)
        }
    }
}

@Composable
private fun ViewfinderOverlay() {
    Box(
        Modifier
            .fillMaxSize()
            .padding(52.dp)
            .border(2.dp, Primary.copy(alpha = 0.75f), RoundedCornerShape(18.dp))
    )
}

@Composable
fun CameraQrView(onQr: (String) -> Unit) {
    val lifecycleOwner = LocalLifecycleOwner.current
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { viewCtx ->
            val previewView = PreviewView(viewCtx)
            val future = ProcessCameraProvider.getInstance(viewCtx)
            future.addListener({
                try {
                    val provider = future.get()
                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }
                    val analysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                        .also {
                            it.setAnalyzer(
                                Executors.newSingleThreadExecutor(),
                                QrAnalyzer(onQr)
                            )
                        }
                    provider.unbindAll()
                    provider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        analysis
                    )
                } catch (_: Throwable) {
                }
            }, ContextCompat.getMainExecutor(viewCtx))
            previewView
        }
    )
}

private class QrAnalyzer(private val onQr: (String) -> Unit) : ImageAnalysis.Analyzer {

    private val scanner = BarcodeScanning.getClient(
        BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .build()
    )

    @androidx.annotation.OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        val media = imageProxy.image
        if (media == null) {
            imageProxy.close()
            return
        }
        val input = InputImage.fromMediaImage(media, imageProxy.imageInfo.rotationDegrees)
        scanner.process(input)
            .addOnSuccessListener { codes ->
                codes.firstOrNull()?.rawValue?.let { onQr(it) }
            }
            .addOnCompleteListener { imageProxy.close() }
    }
}

// ---------- 连接中 / 结果 ----------

@Composable
fun ConnectingScreen(client: ControllerClient, onClear: () -> Unit) {
    val st by client.stage.collectAsState()
    val detail by client.detail.collectAsState()

    LaunchedEffect(st) {
        if (st == CtrlStage.Denied || st == CtrlStage.PeerClosed || st == CtrlStage.Stopped) {
            delay(1800)
            onClear()
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(22.dp)
    ) {
        PalmTopBar("连接中")
        Spacer(Modifier.weight(1f))
        Column(
            Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CircularProgressIndicator(color = Primary, strokeWidth = 3.dp, modifier = Modifier.size(40.dp))
            Spacer(Modifier.height(20.dp))
            Text(
                when (st) {
                    CtrlStage.Denied -> "对方拒绝了本次控制"
                    CtrlStage.PeerClosed, CtrlStage.Stopped -> "连接已断开"
                    else -> "正在连接 ${client.payload.name.ifBlank { client.payload.ip }}"
                },
                color = TextMain, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )
            if (st == CtrlStage.Failed && detail.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    detail,
                    color = TextMuted, fontSize = 12.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
        Spacer(Modifier.weight(1f))

        StepRow("扫码成功", done = true)
        StepRow("建立连接", done = st != CtrlStage.Connecting, spinning = st == CtrlStage.Connecting)
        StepRow("等待对方允许", done = false, spinning = st == CtrlStage.Requesting)

        Spacer(Modifier.height(16.dp))
        if (st == CtrlStage.Failed) {
            PalmButton("重新扫码", Modifier.fillMaxWidth(), onClick = onClear)
        } else if (st != CtrlStage.Denied && st != CtrlStage.PeerClosed && st != CtrlStage.Stopped) {
            TipBar("被控端手机上会弹出确认框，需要对方点「允许」。")
        }
    }
}

@Composable
private fun StepRow(text: String, done: Boolean, spinning: Boolean = false) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(20.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(if (done) PrimaryDim else Surface1),
            contentAlignment = Alignment.Center
        ) {
            Text(
                if (done) "✓" else "",
                color = Primary, fontSize = 12.sp, fontWeight = FontWeight.Bold
            )
            if (spinning) CircularProgressIndicator(color = Primary, strokeWidth = 2.dp, modifier = Modifier.size(14.dp))
        }
        Spacer(Modifier.width(10.dp))
        Text(text, color = if (done) TextMain else TextMuted, fontSize = 13.sp)
    }
}

// ---------- 远程控制（收流 + 触控） ----------

@Composable
fun RemoteScreen(client: ControllerClient, onExit: () -> Unit) {
    val rtt by client.rtt.collectAsState()
    val remoteW by client.rtc?.remoteW?.collectAsState() ?: remember { mutableStateOf(0) }
    val remoteH by client.rtc?.remoteH?.collectAsState() ?: remember { mutableStateOf(0) }
    val hasVideo by (client.rtc?.trackReady ?: MutableStateFlow(null)).collectAsState()

    var lastMoveAt by remember { mutableStateOf(0L) }

    fun mapAndSend(action: String, px: Float, py: Float, vw: Int, vh: Int): Boolean {
        val w = remoteW
        val h = remoteH
        if (w <= 0 || h <= 0 || vw <= 0 || vh <= 0) return false
        val scale = minOf(vw.toFloat() / w, vh.toFloat() / h)
        val ox = (vw - w * scale) / 2f
        val oy = (vh - h * scale) / 2f
        val x = (px - ox) / scale
        val y = (py - oy) / scale
        if (x < 0f || y < 0f || x > w || y > h) return false
        client.sendTouch(action, (x / w).toDouble(), (y / h).toDouble())
        return true
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(22.dp)
    ) {
        PalmTopBar("远程控制")
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Surface1)
                .padding(horizontal = 16.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Ok)
            )
            Spacer(Modifier.width(10.dp))
            Text(
                client.payload.name.ifBlank { client.payload.ip },
                color = TextMain, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            Text(
                if (rtt < 0) "测量延迟中…" else "$rtt ms",
                color = when {
                    rtt < 0 -> TextMuted
                    rtt < 80 -> Ok
                    rtt < 200 -> Primary
                    else -> Danger
                },
                fontSize = 12.sp, fontWeight = FontWeight.Medium
            )
        }

        Spacer(Modifier.height(14.dp))

        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFF0F141A))
        ) {
            AndroidView(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            mapAndSend("down", down.position.x, down.position.y, size.width, size.height)
                            while (true) {
                                val ev = awaitPointerEvent()
                                val ch = ev.changes.firstOrNull() ?: break
                                if (ch.pressed) {
                                    val now = System.currentTimeMillis()
                                    if (now - lastMoveAt > 24) {
                                        lastMoveAt = now
                                        mapAndSend("move", ch.position.x, ch.position.y, size.width, size.height)
                                    }
                                    ch.consume()
                                } else {
                                    mapAndSend("up", ch.position.x, ch.position.y, size.width, size.height)
                                    break
                                }
                            }
                        }
                    },
                factory = { vctx ->
                    SurfaceViewRenderer(vctx).also { r ->
                        r.init(WebRtcCtrl.eglBase.eglBaseContext, null)
                        client.rtc?.attachRenderer(r)
                    }
                },
                onRelease = { r ->
                    client.rtc?.detachRenderer()
                    runCatching { r.release() }
                }
            )
            if (hasVideo == null) {
                Column(
                    Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(color = Primary, strokeWidth = 3.dp)
                    Spacer(Modifier.height(12.dp))
                    Text("等待对方画面…", color = TextMuted, fontSize = 12.sp)
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth()) {
            ToolChip("返回", Modifier.weight(1f)) { client.sendNav("back") }
            Spacer(Modifier.width(10.dp))
            ToolChip("主页", Modifier.weight(1f)) { client.sendNav("home") }
            Spacer(Modifier.width(10.dp))
            ToolChip("最近任务", Modifier.weight(1f)) { client.sendNav("recents") }
        }
        Spacer(Modifier.height(12.dp))
        PalmButton(
            "断开连接",
            Modifier.fillMaxWidth(),
            danger = true
        ) {
            client.disconnect()
            onExit()
        }
    }
}

@Composable
private fun ToolChip(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Surface1)
            .clickable(onClick = onClick)
            .padding(vertical = 11.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = TextMuted, fontSize = 12.sp)
    }
}
