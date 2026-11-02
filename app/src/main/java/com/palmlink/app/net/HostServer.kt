package com.palmlink.app.net

import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.close
import io.ktor.websocket.readText
import io.ktor.websocket.send
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.concurrent.atomic.AtomicReference

sealed interface HostEvent {
    data object LinkUp : HostEvent
    data object LinkDown : HostEvent
    data class Msg(val msg: WsMsg) : HostEvent
}

/**
 * 被控端内嵌 WebSocket 服务（S3 局域网信令）。
 * 端口随机分配，token 在 /ws 握手时校验。
 */
class HostServer(private val expectedToken: String) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val sessionRef = AtomicReference<WebSocketSession?>(null)
    private var server: EmbeddedServer<*, *>? = null

    val events = MutableSharedFlow<HostEvent>(extraBufferCapacity = 64)
    val running = MutableStateFlow(false)

    var port: Int = 0
        private set

    fun start(onReady: (Int) -> Unit, onError: (String) -> Unit) {
        scope.launch {
            try {
                val srv = embeddedServer(
                    CIO,
                    port = 0,
                    host = "0.0.0.0",
                    watchPaths = emptyList()
                ) {
                    install(WebSockets)
                    routing {
                        webSocket("/ws") {
                            if (call.request.queryParameters["token"] != expectedToken) {
                                close(CloseReason(4401.toShort(), "bad token"))
                                return@webSocket
                            }
                            if (!sessionRef.compareAndSet(null, this)) {
                                close(CloseReason(4402.toShort(), "busy"))
                                return@webSocket
                            }
                            events.tryEmit(HostEvent.LinkUp)
                            try {
                                for (frame in incoming) {
                                    if (frame is Frame.Text) {
                                        val msg = runCatching {
                                            WsJson.decodeFromString<WsMsg>(frame.readText())
                                        }.getOrNull() ?: continue
                                        events.tryEmit(HostEvent.Msg(msg))
                                    }
                                }
                            } finally {
                                sessionRef.compareAndSet(this, null)
                                events.tryEmit(HostEvent.LinkDown)
                            }
                        }
                    }
                }
                srv.start(wait = false)
                server = srv
                port = srv.engine.resolvedConnectors().first().port
                running.value = true
                onReady(port)
            } catch (t: Throwable) {
                onError(t.message ?: t.javaClass.simpleName)
            }
        }
    }

    /** 向当前会话发送消息（无会话时静默丢弃） */
    fun send(msg: WsMsg) {
        val s = sessionRef.get() ?: return
        scope.launch {
            runCatching { s.send(Frame.Text(WsJson.encodeToString(msg))) }
        }
    }

    /** 踢掉当前会话（拒绝 / 断开时用） */
    fun dropSession(code: Short, reason: String) {
        val s = sessionRef.getAndSet(null) ?: return
        scope.launch {
            runCatching { s.close(CloseReason(code, reason)) }
        }
    }

    fun stop() {
        scope.launch {
            sessionRef.getAndSet(null)?.let { s ->
                runCatching { s.close(CloseReason(1001.toShort(), "bye")) }
            }
            runCatching { server?.stop(500, 1000) }
            server = null
            running.value = false
        }
    }
}
