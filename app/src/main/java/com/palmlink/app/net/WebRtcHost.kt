package com.palmlink.app.net

import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.media.projection.MediaProjection
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.view.WindowManager
import org.webrtc.DataChannel
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.ScreenCapturerAndroid
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.SurfaceTextureHelper
import org.webrtc.VideoCapturer
import java.nio.ByteBuffer

/** 触控指令在 DataChannel 上的收发助手 */
object CtrlLink {

    fun send(dc: DataChannel?, msg: CtrlMsg): Boolean {
        if (dc == null || dc.state() != DataChannel.State.OPEN) return false
        return runCatching {
            val bytes = WsJson.encodeToString(msg).toByteArray(Charsets.UTF_8)
            dc.send(DataChannel.Buffer(ByteBuffer.wrap(bytes), false))
            true
        }.getOrDefault(false)
    }

    fun parse(text: String): CtrlMsg? = runCatching {
        WsJson.decodeFromString<CtrlMsg>(text)
    }.getOrNull()

    fun iceOut(c: IceCandidate): String =
        WsJson.encodeToString(IcePayload(mid = c.sdpMid, mline = c.sdpMLineIndex, sdp = c.sdp))

    fun iceIn(json: String): IceCandidate? = runCatching {
        val p = WsJson.decodeFromString<IcePayload>(json)
        IceCandidate(p.mid ?: "", p.mline, p.sdp)
    }.getOrNull()
}

open class SdpBase : SdpObserver {
    override fun onCreateSuccess(desc: SessionDescription) {}
    override fun onSetSuccess() {}
    override fun onCreateFailure(error: String?) {}
    override fun onSetFailure(error: String?) {}
}

/** 被控端 WebRTC：屏幕采集 -> 编码推流，DataChannel 接收触控指令 */
class WebRtcHost(
    private val context: Context,
    private val send: (WsMsg) -> Unit,
    private val onProjectionLost: () -> Unit
) {
    companion object {
        @Volatile
        private var factory: PeerConnectionFactory? = null
    }

    private fun buildFactory(eglContext: EglBase.Context): PeerConnectionFactory {
        factory?.let { return it }
        synchronized(this) {
            factory?.let { return it }
            PeerConnectionFactory.initialize(
                PeerConnectionFactory.InitializationOptions.builder(context)
                    .setEnableInternalTracer(false)
                    .createInitializationOptions()
            )
            val f = PeerConnectionFactory.builder()
                .setVideoEncoderFactory(
                    DefaultVideoEncoderFactory(eglContext, true, true)
                )
                .setVideoDecoderFactory(
                    DefaultVideoDecoderFactory(eglContext)
                )
                .createPeerConnectionFactory()
            factory = f
            return f
        }
    }

    private val egl = EglBase.create()
    private val main = Handler(Looper.getMainLooper())

    // ScreenCapturerAndroid 构造时内部会创建 Handler，必须在带 Looper 的线程上调用
    private val captureThread = HandlerThread("pl-host-rtc").apply { start() }
    private val captureHandler = Handler(captureThread.looper)

    private var pc: PeerConnection? = null
    private var capturer: VideoCapturer? = null
    private var dc: DataChannel? = null
    private val pendingIce = mutableListOf<IceCandidate>()
    private var remoteSet = false

    val screenW: Int
    val screenH: Int

    init {
        if (Build.VERSION.SDK_INT >= 30) {
            val b: Rect = context.getSystemService(WindowManager::class.java)
                .maximumWindowMetrics.bounds
            screenW = b.width()
            screenH = b.height()
        } else {
            screenW = context.resources.displayMetrics.widthPixels
            screenH = context.resources.displayMetrics.heightPixels
        }
    }

    fun start(resultCode: Int, data: Intent) {
        captureHandler.post { startInternal(resultCode, data) }
    }

    private fun startInternal(code: Int, data: Intent) {
        try {
            val f = buildFactory(egl.eglBaseContext)
            val capt = ScreenCapturerAndroid(data, object : MediaProjection.Callback() {
                override fun onStop() {
                    main.post { onProjectionLost() }
                }
            })
            capturer = capt
            val source = f.createVideoSource(true)
            val helper = SurfaceTextureHelper.create("pl-cap", egl.eglBaseContext)
            capt.initialize(helper, context, source.capturerObserver)
            capt.startCapture(screenW, screenH, 24)

            val init = PeerConnection.RTCConfiguration(emptyList()).apply {
                sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            }
            val obs = object : PeerConnection.Observer {
                override fun onSignalingChange(state: PeerConnection.SignalingState) {}
                override fun onIceConnectionChange(state: PeerConnection.IceConnectionState) {}
                override fun onIceConnectionReceivingChange(receiving: Boolean) {}
                override fun onIceGatheringChange(state: PeerConnection.IceGatheringState) {}
                override fun onIceCandidate(candidate: IceCandidate) {
                    send(WsMsg(type = "ice", data = CtrlLink.iceOut(candidate)))
                }
                override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>) {}
                override fun onAddStream(stream: org.webrtc.MediaStream) {}
                override fun onRemoveStream(stream: org.webrtc.MediaStream) {}
                override fun onDataChannel(channel: DataChannel) {
                    dc = channel
                    channel.registerObserver(hostDcObserver(channel))
                }
                override fun onRenegotiationNeeded() {}
                override fun onAddTrack(receiver: org.webrtc.RtpReceiver, streams: Array<out org.webrtc.MediaStream>) {}
                override fun onRemoveTrack(receiver: org.webrtc.RtpReceiver) {}
                override fun onTrack(transceiver: org.webrtc.RtpTransceiver) {}
                override fun onConnectionChange(state: PeerConnection.PeerConnectionState) {}
                override fun onStandardizedIceConnectionChange(state: PeerConnection.IceConnectionState) {}
                override fun onSelectedCandidatePairChanged(event: org.webrtc.CandidatePairChangeEvent) {}
            }
            val connection = f.createPeerConnection(init, obs)
                ?: throw IllegalStateException("createPeerConnection 失败")
            pc = connection

            val track = f.createVideoTrack("screen", source)
            connection.addTrack(track, listOf("screen"))

            val localDc = connection.createDataChannel("ctrl", DataChannel.Init())
            dc = localDc
            localDc.registerObserver(hostDcObserver(localDc))

            connection.createOffer(object : SdpBase() {
                override fun onCreateSuccess(desc: SessionDescription) {
                    connection.setLocalDescription(object : SdpBase() {
                        override fun onSetSuccess() {
                            send(
                                WsMsg(
                                    type = "offer",
                                    data = desc.description,
                                    w = screenW,
                                    h = screenH
                                )
                            )
                        }
                        override fun onSetFailure(error: String?) { main.post { onProjectionLost() } }
                    }, desc)
                }
                override fun onCreateFailure(error: String?) {
                    main.post { onProjectionLost() }
                }
            }, MediaConstraints())
        } catch (t: Throwable) {
            main.post { onProjectionLost() }
        }
    }

    private fun hostDcObserver(channel: DataChannel): DataChannel.Observer =
        object : DataChannel.Observer {
            override fun onBufferedAmountChange(previous: Long) {}
            override fun onStateChange() {
                if (channel.state() == DataChannel.State.CLOSED) dc = null
            }
            override fun onMessage(buffer: DataChannel.Buffer) {
                if (buffer.binary) return
                val bytes = ByteArray(buffer.data.remaining())
                buffer.data.get(bytes)
                val msg = CtrlLink.parse(String(bytes, Charsets.UTF_8)) ?: return
                when (msg.t) {
                    "touch" -> {
                        val a = msg.a ?: return
                        val x = msg.x ?: return
                        val y = msg.y ?: return
                        main.post {
                            val s = PalmAccessibilityService.instance ?: return@post
                            when (a) {
                                "down" -> lastDown = doubleArrayOf(x, y)
                                "move" -> lastDown?.let {
                                    s.stroke(it[0], it[1], x, y, 16)
                                    lastDown = doubleArrayOf(x, y)
                                }
                                "up" -> {
                                    val d = lastDown
                                    lastDown = null
                                    if (d != null) {
                                        val dx = (x - d[0])
                                        val dy = (y - d[1])
                                        if (dx * dx + dy * dy < 0.00002) s.tap(x, y)
                                        else s.stroke(d[0], d[1], x, y, swipeDuration(dx, dy))
                                    }
                                }
                            }
                        }
                    }
                    "nav" -> {
                        val k = msg.k ?: return
                        main.post {
                            PalmAccessibilityService.instance?.nav(k)
                        }
                    }
                }
            }
        }

    private var lastDown: DoubleArray? = null

    private fun swipeDuration(dx: Double, dy: Double): Long =
        (Math.sqrt(dx * dx + dy * dy) * 900).toLong().coerceIn(120, 700)

    fun onSignal(msg: WsMsg) {
        val connection = pc ?: return
        when (msg.type) {
            "answer" -> {
                val sdp = msg.data ?: return
                captureHandler.post {
                    connection.setRemoteDescription(object : SdpBase() {
                        override fun onSetSuccess() {
                            remoteSet = true
                            val list = synchronized(pendingIce) {
                                val l = pendingIce.toList()
                                pendingIce.clear()
                                l
                            }
                            list.forEach { connection.addIceCandidate(it) }
                        }
                        override fun onSetFailure(error: String?) {}
                    }, SessionDescription(SessionDescription.Type.ANSWER, sdp))
                }
            }
            "ice" -> {
                val c = msg.data?.let { CtrlLink.iceIn(it) } ?: return
                captureHandler.post {
                    if (remoteSet) connection.addIceCandidate(c)
                    else synchronized(pendingIce) { pendingIce.add(c) }
                }
            }
        }
    }

    fun close() {
        captureHandler.post {
            runCatching { capturer?.stopCapture() }
            runCatching { capturer?.dispose() }
            capturer = null
            runCatching { pc?.close() }
            pc = null
            dc = null
            remoteSet = false
            synchronized(pendingIce) { pendingIce.clear() }
        }
    }
}
