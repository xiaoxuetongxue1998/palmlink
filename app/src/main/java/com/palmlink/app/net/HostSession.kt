package com.palmlink.app.net

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

    val stage = MutableStateFlow(HostStage.Guide)
    val peerName = MutableStateFlow<String?>(null)
    val errorMsg = MutableStateFlow("")
    val qrPayload = MutableStateFlow<PairPayload?>(null)

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
                    }
                    HostEvent.LinkUp -> Unit
                    HostEvent.LinkDown -> {
                        if (stage.value == HostStage.Active || stage.value == HostStage.Requested) {
                            peerName.value = null
                            stage.value = HostStage.Waiting
                        }
                    }
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

    fun approve() {
        if (stage.value != HostStage.Requested) return
        server?.send(WsMsg(type = "approve"))
        connectedAt = System.currentTimeMillis()
        stage.value = HostStage.Active
    }

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
        peerName.value = null
        if (stage.value == HostStage.Active) stage.value = HostStage.Waiting
    }

    fun shutdown() {
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
