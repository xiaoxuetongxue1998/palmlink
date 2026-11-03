package com.palmlink.app.net

import android.content.Context
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import io.ktor.websocket.send
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow

/** 控制端会话状态机 */
enum class CtrlStage { Connecting, Requesting, Approved, Denied, Failed, PeerClosed, Stopped }

class ControllerClient(val payload: PairPayload) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val stage = MutableStateFlow(CtrlStage.Connecting)
    val detail = MutableStateFlow("")
    val rtt = MutableStateFlow(-1)

    private var client: HttpClient? = null
    var rtc: WebRtcCtrl? = null
        private set

    fun start() {
        // WebRTC 初始化在极少数机型上会抛 UnsatisfiedLinkError / IllegalState，
        // 这里必须兜住，否则主线程直接崩溃退出
        runCatching {
            rtc = WebRtcCtrl({ msg ->
                scope.launch {
                    runCatching { wsSession?.send(Frame.Text(WsJson.encodeToString(msg))) }
                }
            })
        }.onFailure { t ->
            com.palmlink.app.CrashLog.write(
                (WebRtcCtrl.appContext?.filesDir) ?: return@onFailure,
                "WebRtcCtrl 初始化",
                t
            )
            detail.value = "WebRTC 初始化失败：" + (t.message ?: t.javaClass.simpleName)
            stage.value = CtrlStage.Failed
            return
        }
        scope.launch {
            try {
                val c = HttpClient {
                    install(WebSockets)
                }
                client = c
                c.webSocket(urlString = "ws://${payload.ip}:${payload.port}/ws?token=${payload.token}") {
                    wsSession = this
                    send(Frame.Text(WsJson.encodeToString(WsMsg(type = "request", name = deviceName()))))
                    stage.value = CtrlStage.Requesting

                    val pinger = launch {
                        while (isActive) {
                            delay(2000)
                            runCatching {
                                send(Frame.Text(
                                    WsJson.encodeToString(WsMsg(type = "ping", t = System.currentTimeMillis()))
                                ))
                            }
                        }
                    }

                    loop@ for (frame in incoming) {
                        if (frame !is Frame.Text) continue
                        val msg = runCatching {
                            WsJson.decodeFromString<WsMsg>(frame.readText())
                        }.getOrNull() ?: continue
                        when (msg.type) {
                            "approve" -> stage.value = CtrlStage.Approved
                            "deny" -> { stage.value = CtrlStage.Denied; break@loop }
                            "pong" -> msg.t?.let {
                                rtt.value = (System.currentTimeMillis() - it).toInt()
                            }
                            "offer", "ice" -> rtc?.onSignal(msg)
                            "bye" -> { stage.value = CtrlStage.PeerClosed; break@loop }
                        }
                    }
                    pinger.cancel()
                    if (stage.value == CtrlStage.Approved) stage.value = CtrlStage.PeerClosed
                }
            } catch (t: Throwable) {
                val st = stage.value
                if (st == CtrlStage.Connecting || st == CtrlStage.Requesting) {
                    detail.value = t.message ?: t.javaClass.simpleName
                    stage.value = CtrlStage.Failed
                }
            }
        }
    }

    @Volatile
    private var wsSession: io.ktor.websocket.DefaultWebSocketSession? = null

    fun sendNav(key: String) {
        rtc?.sendCtrl(CtrlMsg(t = "nav", k = key))
    }

    fun sendTouch(action: String, xn: Double, yn: Double) {
        rtc?.sendCtrl(CtrlMsg(t = "touch", a = action, x = xn, y = yn))
    }

    fun disconnect() {
        stage.value = CtrlStage.Stopped
        rtc?.close()
        rtc = null
        val c = client
        client = null
        scope.launch {
            runCatching { c?.close() }
        }
    }
}
