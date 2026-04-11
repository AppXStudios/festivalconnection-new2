package com.appxstudios.festivalconnection.security

object NostrBech32 {
    private const val CHARSET = "qpzry9x8gf2tvdw0s3jn54khce6mua7l"

    fun encode(hrp: String, data: ByteArray): String {
        val values = convertBits(data.toList().map { it.toInt() and 0xFF }, 8, 5, true)
        val checksum = createChecksum(hrp, values)
        val combined = values + checksum
        val encoded = combined.map { CHARSET[it] }
        return hrp + "1" + String(encoded.toCharArray())
    }

    fun decode(str: String): Pair<String, ByteArray>? {
        val lower = str.lowercase()
        val sepIdx = lower.lastIndexOf('1')
        if (sepIdx < 0) return null
        val hrp = lower.substring(0, sepIdx)
        val dataStr = lower.substring(sepIdx + 1)

        val values = dataStr.map { c ->
            val idx = CHARSET.indexOf(c)
            if (idx < 0) return null
            idx
        }

        if (values.size < 6) return null
        val payload = values.dropLast(6)
        val converted = convertBits(payload, 5, 8, false)
        return Pair(hrp, ByteArray(converted.size) { converted[it].toByte() })
    }

    fun npub(publicKeyHex: String): String {
        val data = NostrIdentity.hexToBytes(publicKeyHex) ?: return ""
        if (data.size != 32) return ""
        return encode("npub", data)
    }

    fun publicKeyHex(fromNpub: String): String? {
        val (hrp, data) = decode(fromNpub) ?: return null
        if (hrp != "npub" || data.size != 32) return null
        return NostrIdentity.bytesToHex(data)
    }

    fun note(eventIdHex: String): String {
        val data = NostrIdentity.hexToBytes(eventIdHex) ?: return ""
        if (data.size != 32) return ""
        return encode("note", data)
    }

    private fun convertBits(data: List<Int>, fromBits: Int, toBits: Int, pad: Boolean): List<Int> {
        var acc = 0
        var bits = 0
        val result = mutableListOf<Int>()
        val maxv = (1 shl toBits) - 1

        for (value in data) {
            acc = (acc shl fromBits) or value
            bits += fromBits
            while (bits >= toBits) {
                bits -= toBits
                result.add((acc shr bits) and maxv)
            }
        }

        if (pad && bits > 0) {
            result.add((acc shl (toBits - bits)) and maxv)
        }
        return result
    }

    private fun createChecksum(hrp: String, values: List<Int>): List<Int> {
        val hrpExpand = expandHRP(hrp)
        val polymod = polymod(hrpExpand + values + listOf(0, 0, 0, 0, 0, 0)) xor 1
        return (0 until 6).map { (polymod shr (5 * (5 - it))) and 31 }
    }

    private fun expandHRP(hrp: String): List<Int> {
        val result = mutableListOf<Int>()
        for (c in hrp) result.add(c.code shr 5)
        result.add(0)
        for (c in hrp) result.add(c.code and 31)
        return result
    }

    private fun polymod(values: List<Int>): Int {
        val gen = intArrayOf(0x3b6a57b2.toInt(), 0x26508e6d, 0x1ea119fa, 0x3d4233dd, 0x2a1462b3)
        var chk = 1
        for (v in values) {
            val b = chk ushr 25
            chk = (chk and 0x1ffffff) shl 5 xor v
            for (i in 0 until 5) {
                if ((b ushr i) and 1 != 0) {
                    chk = chk xor gen[i]
                }
            }
        }
        return chk
    }
}
