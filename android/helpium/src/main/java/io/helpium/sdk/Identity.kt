package io.helpium.sdk

import org.json.JSONObject

/** Who is chatting, as the host app told the SDK. */
internal data class Identity(
    val userJwt: String? = null,
    val email: String? = null,
    val name: String? = null,
    val firstName: String? = null,
    val lastName: String? = null,
    val marketingOptIn: Boolean? = null,
) {
    /** Scopes the stored conversation to the signed-in user. */
    val storageKey: String? get() = jwtClaim("user_id") ?: jwtClaim("email") ?: email

    /** Reads a claim of the identity token. The server verifies it; this only reads. */
    fun jwtClaim(name: String): String? {
        val parts = userJwt?.split(".") ?: return null
        if (parts.size != 3) return null
        val payload = base64UrlDecode(parts[1]) ?: return null
        val claims = runCatching { JSONObject(String(payload, Charsets.UTF_8)) }.getOrNull() ?: return null
        return if (claims.has(name) && !claims.isNull(name)) claims.get(name).toString() else null
    }

    companion object {
        private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"

        /** java.util.Base64 needs API 26 and android.util.Base64 is absent in unit tests. */
        fun base64UrlDecode(input: String): ByteArray? {
            val clean = input.replace('+', '-').replace('/', '_').trimEnd('=')
            val out = java.io.ByteArrayOutputStream()
            var buffer = 0
            var bits = 0
            for (char in clean) {
                val value = ALPHABET.indexOf(char)
                if (value < 0) return null
                buffer = (buffer shl 6) or value
                bits += 6
                if (bits >= 8) {
                    bits -= 8
                    out.write((buffer shr bits) and 0xFF)
                }
            }
            return out.toByteArray()
        }
    }
}
