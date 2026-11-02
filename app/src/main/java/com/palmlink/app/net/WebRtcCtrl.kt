package com.palmlink.app.net

import org.webrtc.DataChannel
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoTrack
import kotlinx.coroutines.flow.MutableStateFlow

/** 控制端 WebRTC：收流渲染 + DataChannel 发送触控/导航指令 */
class WebRtcCtrl(private val send: (WsMsg) -> Unit) {

    companion object {
        /** 渲染器与解码器共用一个 EGL 上下文 */
        val eglBase: EglBase = EglBase.create()

        @Volatile
        private var factory: PeerConnectionFactory? = null

        fun buildFactory(): PeerConnectionFactory {
            factory?.let { return it }
            synchronized(this) {
                factory?.let { return it }
                PeerConnectionFactory.initialize(
                    PeerConnectionFactory.InitializationOptions.builder(
                        appContext ?: throw IllegalStateException("no context")
                    ).createInitializationOptions()
                )
                val f = PeerConnectionFactory.builder()
                    .setVideoEncoderFactory(
                        DefaultVideoEncoderFactory(eglBase.eglBaseContext, true, true)
                    )
                    .setVideoDecoderFactory(
                        DefaultVideoDecoderFactory(eglBase.eglBaseContext)
                    )
                    .createPeerConnectionFactory()
                factory = f
                return f
            }
        }

        @Volatile
        var appContext: android.content.Context? = null
    }

    private var pc: PeerConnection? = null
    private var dc: DataChannel? = null
    private var track: VideoTrack? = null
    private var renderer: SurfaceViewRenderer? = null
    private val pendingIce = mutableListOf<IceCandidate>()
    private var remoteSet = false

    /** 对方屏幕尺寸（来自 offer 的 w/h），用于触控坐标换算 */
    val remoteW = MutableStateFlow(0)
    val remoteH = MutableStateFlow(0)
    val trackReady = MutableStateFlow<VideoTrack?>(null)

    init {
        val f = buildFactory()
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
                channel.registerObserver(object : DataChannel.Observer {
                    override fun onBufferedAmountChange(previous: Long) {}
                    override fun onStateChange() {
                        if (channel.state() == DataChannel.State.CLOSED) dc = null
                    }
                    override fun onMessage(buffer: DataChannel.Buffer) {}
                })
            }
            override fun onRenegotiationNeeded() {}
            override fun onAddTrack(receiver: org.webrtc.RtpReceiver, streams: Array<out org.webrtc.MediaStream>) {}
            override fun onRemoveTrack(receiver: org.webrtc.RtpReceiver) {}
            override fun onTrack(transceiver: org.webrtc.RtpTransceiver) {
                val t = transceiver.receiver.track()
                if (t is VideoTrack) {
                    track = t
                    trackReady.value = t
                    t.addSink(renderer ?: return)
                }
            }
            override fun onConnectionChange(state: PeerConnection.PeerConnectionState) {}
            override fun onStandardizedIceConnectionChange(state: PeerConnection.IceConnectionState) {}
            override fun onSelectedCandidatePairChanged(event: org.webrtc.CandidatePairChangeEvent) {}
        }
        pc = f.createPeerConnection(init, obs)
    }

    fun onSignal(msg: WsMsg) {
        val connection = pc ?: return
        when (msg.type) {
            "offer" -> {
                val sdp = msg.data ?: return
                remoteW.value = msg.w ?: 0
                remoteH.value = msg.h ?: 0
                connection.setRemoteDescription(object : SdpBase() {
                    override fun onSetSuccess() {
                        remoteSet = true
                        val list = synchronized(pendingIce) {
                            val l = pendingIce.toList()
                            pendingIce.clear()
                            l
                        }
                        list.forEach { connection.addIceCandidate(it) }
                        connection.createAnswer(object : SdpBase() {
                            override fun onCreateSuccess(desc: SessionDescription) {
                                connection.setLocalDescription(object : SdpBase() {
                                    override fun onSetSuccess() {
                                        send(WsMsg(type = "answer", data = desc.description))
                                    }
                                    override fun onSetFailure(error: String?) {}
                                }, desc)
                            }
                            override fun onCreateFailure(error: String?) {}
                        }, MediaConstraints())
                    }
                    override fun onSetFailure(error: String?) {}
                }, SessionDescription(SessionDescription.Type.OFFER, sdp))
            }
            "ice" -> {
                val c = msg.data?.let { CtrlLink.iceIn(it) } ?: return
                if (remoteSet) connection.addIceCandidate(c)
                else synchronized(pendingIce) { pendingIce.add(c) }
            }
        }
    }

    fun attachRenderer(r: SurfaceViewRenderer) {
        renderer = r
        track?.addSink(r)
    }

    fun detachRenderer() {
        track?.let { t ->
            renderer?.let { r -> runCatching { t.removeSink(r) } }
        }
        renderer = null
    }

    fun sendCtrl(msg: CtrlMsg): Boolean = CtrlLink.send(dc, msg)

    fun close() {
        detachRenderer()
        track = null
        trackReady.value = null
        runCatching { dc?.close() }
        dc = null
        runCatching { pc?.close() }
        pc = null
        remoteSet = false
        synchronized(pendingIce) { pendingIce.clear() }
    }
}
