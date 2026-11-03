package com.palmlink.app.net

import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow

/** 被控端会话状态机 */
enum class HostStage { Guide, Starting, Waiting, Requested, Active, Error }

class HostSession {

    val token: String = randomToken()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var server: HostServer? = null
    private var collector: Job? = null
    private var rtc: WebRtcHost? = null
    private var appCtx: Context? = null

    val stage = MutableStateFlow(HostStage.Guide)
    val peerName = MutableStateFlow<String?>(null)
    val errorMsg = MutableStateFlow("")
    val qrPayload = MutableStateFlow<PairPayload?>(null)

    /** 投屏链路错误（非致命，界面提示用） */
    val rtcError = MutableStateFlow("")

    var connectedAt: Long = 0L
        private set

    fun start() {
        val st = stage.value
        if (st != HostStage.Guide && st != HostStage.Error) return
        stage.value = HostStage.Starting

        val ip = Lan.localIpv4()
        if (ip == null) {
            errorMsg.value = "没有获取到局域网 IP，请确认两台手机连的是同一个 WiFi"
            stage.value = HostStage.Error
            return
        }

        val srv = HostServer(token)
        server = srv
        collector?.cancel()
        collector = scope.launch {
            srv.events.collect { ev ->
                when (ev) {
                    is HostEvent.Msg -> when (ev.msg.type) {
                        "request" -> {
                            if (stage.value == HostStage.Waiting) {
                                peerName.value = ev.msg.name?.ifBlank { null } ?: "未知设备"
                                stage.value = HostStage.Requested
                            } else {
                                srv.dropSession(4403.toShort(), "busy")
                            }
                        }
                        "ping" -> srv.send(WsMsg(type = "pong", t = ev.msg.t))
                        "answer" -> rtc?.onSignal(ev.msg)
                        "ice" -> rtc?.onSignal(ev.msg)
                    }
                    HostEvent.LinkUp -> Unit
                    HostEvent.LinkDown -> onLinkDown()
                }
            }
        }

        srv.start(
            onReady = { port ->
                qrPayload.value = PairPayload(
                    name = deviceName(),
                    ip = ip,
                    port = port,
                    token = token
                )
                stage.value = HostStage.Waiting
            },
            onError = { m ->
                errorMsg.value = m
                stage.value = HostStage.Error
            }
        )
    }

    /** 系统屏幕录制授权成功后的入口：批准会话 + 起前台服务 + 开始投屏 */
    fun grant(resultCode: Int, data: Intent, ctx: Context) {
        if (stage.value != HostStage.Requested) return
        val ac = ctx.applicationContext
        appCtx = ac

        server?.send(WsMsg(type = "approve"))
        connectedAt = System.currentTimeMillis()
        rtcError.value = ""
        stage.value = HostStage.Active

        CaptureService.start(ac, resultCode, data)
        val r = runCatching {
            WebRtcHost(
                context = ac,
                send = { m -> server?.send(m) },
                onProjectionLost = { projectionLost() },
                onError = { m -> rtcError.value = m }
            )
        }.onFailure { t ->
            com.palmlink.app.CrashLog.write(ac.filesDir, "WebRtcHost 初始化", t)
            rtcError.value = t.message ?: t.javaClass.simpleName
        }.getOrNull()
        rtc = r
        r?.start(resultCode, data)
    }

    /** 采集被系统终止（用户停止投屏 / 出错） */
    private fun projectionLost() {
        rtc?.close()
        rtc = null
        appCtx?.let { CaptureService.stop(it) }
        if (stage.value == HostStage.Active) {
            peerName.value = null
            stage.value = HostStage.Waiting
        }
    }

    private fun onLinkDown() {
        rtc?.close()
        rtc = null
        if (stage.value == HostStage.Active) {
            appCtx?.let { CaptureService.stop(it) }
        }
        if (stage.value == HostStage.Active || stage.value == HostStage.Requested) {
            peerName.value = null
            stage.value = HostStage.Waiting
        }
    }

    fun approve() = Unit // 已由 grant() 取代（需先拿到系统授权结果）

    fun deny() {
        if (stage.value != HostStage.Requested) return
        server?.send(WsMsg(type = "deny"))
        server?.dropSession(4403.toShort(), "denied")
        peerName.value = null
        stage.value = HostStage.Waiting
    }

    fun disconnect() {
        server?.send(WsMsg(type = "bye"))
        server?.dropSession(1001.toShort(), "bye")
        rtc?.close()
        rtc = null
        appCtx?.let { CaptureService.stop(it) }
        peerName.value = null
        if (stage.value == HostStage.Active) stage.value = HostStage.Waiting
    }

    fun shutdown() {
        rtc?.close()
        rtc = null
        appCtx?.let { CaptureService.stop(it) }
        server?.stop()
        server = null
        collector?.cancel()
        collector = null
        scope.coroutineContext.cancelChildren()
        peerName.value = null
        qrPayload.value = null
        stage.value = HostStage.Guide
    }
}
