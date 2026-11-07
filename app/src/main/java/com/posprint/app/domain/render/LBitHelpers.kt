package com.posprint.app.domain.render

object LBitHelpers {
    private val DIGITS = "0123456789ABCDEF".toCharArray()

    fun hex(data: ByteArray): String {
        val sb = StringBuilder(data.size * 2)
        for (b in data) {
            val v = b.toInt() and 0xFF
            sb.append(DIGITS[v shr 4]).append(DIGITS[v and 15])
        }
        return sb.toString()
    }
}
