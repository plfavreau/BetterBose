package org.pilou.betterbose.bmap

/** BMAP operator codes (low nibble of the flags byte). */
object Op {
    const val SET = 0
    const val GET = 1
    const val SET_GET = 2
    const val STATUS = 3
    const val ERROR = 4
    const val START = 5
    const val RESULT = 6
    const val PROCESSING = 7

    fun name(op: Int): String = when (op) {
        SET -> "SET"
        GET -> "GET"
        SET_GET -> "SETGET"
        STATUS -> "STATUS"
        ERROR -> "ERROR"
        START -> "START"
        RESULT -> "RESULT"
        PROCESSING -> "PROCESSING"
        else -> "OP$op"
    }
}

/** One BMAP packet: [fblock][function][flags][len][payload]. */
data class BmapFrame(
    val block: Int,
    val function: Int,
    val op: Int,
    val payload: ByteArray = ByteArray(0),
) {
    fun encode(): ByteArray {
        require(payload.size <= MAX_PAYLOAD) { "payload too long: ${payload.size}" }
        val out = ByteArray(HEADER_SIZE + payload.size)
        out[0] = block.toByte()
        out[1] = function.toByte()
        out[2] = (op and OP_MASK).toByte()
        out[3] = payload.size.toByte()
        payload.copyInto(out, HEADER_SIZE)
        return out
    }

    val isError: Boolean get() = op == Op.ERROR
    val errorCode: Int get() = if (isError && payload.isNotEmpty()) payload[0].u8 else -1

    fun ascii(): String = String(payload, Charsets.UTF_8).trimEnd('\u0000')

    override fun toString(): String =
        "[$block.$function] ${Op.name(op)} (${payload.size}) ${payload.hex()}"

    override fun equals(other: Any?): Boolean =
        other is BmapFrame && block == other.block && function == other.function &&
            op == other.op && payload.contentEquals(other.payload)

    override fun hashCode(): Int =
        ((block * 31 + function) * 31 + op) * 31 + payload.contentHashCode()

    companion object {
        const val HEADER_SIZE = 4
        const val MAX_PAYLOAD = 255
        private const val OP_MASK = 0x0F
    }
}

/**
 * Incremental stream parser: RFCOMM reads may split or merge frames, so bytes are
 * buffered until a full header + payload is available.
 */
class BmapStreamParser {
    private var buffer = ByteArray(0)

    fun feed(bytes: ByteArray, count: Int = bytes.size): List<BmapFrame> {
        buffer += bytes.copyOf(count)
        val frames = mutableListOf<BmapFrame>()
        while (buffer.size >= BmapFrame.HEADER_SIZE) {
            val len = buffer[3].u8
            val total = BmapFrame.HEADER_SIZE + len
            if (buffer.size < total) break
            frames += BmapFrame(
                block = buffer[0].u8,
                function = buffer[1].u8,
                op = buffer[2].u8 and 0x0F,
                payload = buffer.copyOfRange(BmapFrame.HEADER_SIZE, total),
            )
            buffer = buffer.copyOfRange(total, buffer.size)
        }
        return frames
    }
}

val Byte.u8: Int get() = toInt() and 0xFF

fun ByteArray.hex(): String = joinToString(" ") { "%02x".format(it.u8) }

fun bytesOf(vararg values: Int): ByteArray = ByteArray(values.size) { values[it].toByte() }
