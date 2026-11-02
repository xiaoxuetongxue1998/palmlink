package com.palmlink.app.net

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** WebSocket 上的通用消息。type 必填，其余按需。 */
@Serializable
data class WsMsg(
    val type: String,
    val name: String? = null,
    val t: Long? = null,
    val reason: String? = null
) {
    companion object {
        // 消息类型约定：
        // request  控制端 -> 被控端：请求控制，name=控制端设备名
        // approve  被控端 -> 控制端：允许
        // deny     被控端 -> 控制端：拒绝
        // ping     控制端 -> 被控端：t=发送时刻毫秒
        // pong     被控端 -> 控制端：原样回传 t
        // bye      任意一方 -> 对方：主动断开
    }
}

/** 二维码配对载荷 */
@Serializable
data class PairPayload(
    val v: Int = 1,
    val name: String,
    val ip: String,
    val port: Int,
    val token: String
)

val WsJson: Json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = false
}
