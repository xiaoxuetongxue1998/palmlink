package com.palmlink.app.net

import java.net.Inet4Address
import java.net.NetworkInterface

object Lan {
    /** 取第一个非回环 IPv4 地址（WiFi 下的本机地址） */
    fun localIpv4(): String? = runCatching {
        NetworkInterface.getNetworkInterfaces().asSequence()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { it.inetAddresses.asSequence() }
            .filterIsInstance<Inet4Address>()
            .firstOrNull { !it.isLoopbackAddress && !it.isAnyLocalAddress }
            ?.hostAddress
    }.getOrNull()
}
