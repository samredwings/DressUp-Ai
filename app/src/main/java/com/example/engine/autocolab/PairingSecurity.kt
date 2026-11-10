package com.example.engine.autocolab

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.security.SecureRandom

/** Security helpers for the temporary cloud-notebook pairing flow. */
internal object PairingSecurity {
    private val secureRandom = SecureRandom()
    private val allowedTunnelSuffixes = setOf(
        "ngrok-free.app",
        "ngrok-free.dev",
        "ngrok.io",
        "ngrok.app",
        "trycloudflare.com",
        "loca.lt"
    )

    /** 256 bits of entropy; this token is not intended to be typed by a person. */
    fun newToken(): String {
        val bytes = ByteArray(32)
        secureRandom.nextBytes(bytes)
        return bytes.joinToString(separator = "") { byte ->
            "%02x".format(byte.toInt() and 0xff)
        }
    }

    /** Accept only HTTPS tunnel origins; reject credentials, custom ports and path/query tricks. */
    fun validateTunnelUrl(raw: String): String? {
        val parsed = raw.toHttpUrlOrNull() ?: return null
        if (parsed.scheme != "https") return null
        if (parsed.username.isNotEmpty() || parsed.password.isNotEmpty()) return null
        if (parsed.port != 443) return null
        if (parsed.encodedPath != "/" || parsed.query != null || parsed.fragment != null) return null

        val host = parsed.host.lowercase()
        if (allowedTunnelSuffixes.none { host.endsWith(".$it") }) return null
        return parsed.newBuilder().encodedPath("/").build().toString().removeSuffix("/")
    }
}
