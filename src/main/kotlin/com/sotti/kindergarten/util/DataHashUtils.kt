package com.sotti.kindergarten.util

import java.security.MessageDigest

object DataHashUtils {
    fun computeHash(vararg fields: Any?): String {
        val content = fields.joinToString("|") { it?.toString() ?: "" }
        return MessageDigest
            .getInstance("MD5")
            .digest(content.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }
}
