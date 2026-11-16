package dev.shiko.toolib.logic

import java.io.ByteArrayOutputStream

/**
 * 动图 WebP 封装（纯 Kotlin）。
 *
 * Android 没有动图 WebP 编码 API，但 `Bitmap.compress(WEBP_LOSSLESS)` 能把单帧编码成
 * 合法的 WebP 文件；这里把这些单帧的 VP8L 码流抽出来，按 WebP 容器规范重新拼成动图：
 * RIFF → VP8X（Animation 标志）→ ANIM（循环）→ 每帧一个 ANMF。
 */
object AnimatedWebpWriter {

    /**
     * @param frameFiles 每一帧的完整 WebP 文件字节（`Bitmap.compress` 的输出）
     * @param durationMillis 每帧停留时间（毫秒）
     * @param loopCount 0 = 无限循环
     */
    fun encode(
        frameFiles: List<ByteArray>,
        width: Int,
        height: Int,
        durationMillis: Int,
        loopCount: Int = 0,
    ): ByteArray {
        require(frameFiles.isNotEmpty()) { "no frames" }
        require(width > 0 && height > 0) { "empty canvas" }

        val chunks = ByteArrayOutputStream()
        // VP8X：动画标志
        val vp8x = ByteArrayOutputStream().apply {
            write(0x02)                       // flags: animation
            write(byteArrayOf(0, 0, 0))       // reserved
            writeUInt24(width - 1)
            writeUInt24(height - 1)
        }.toByteArray()
        writeChunk(chunks, "VP8X", vp8x)

        // ANIM：背景色 + 循环次数
        val anim = ByteArrayOutputStream().apply {
            write(byteArrayOf(0, 0, 0, 0))    // background color (BGRA)
            write(loopCount and 0xFF)
            write((loopCount shr 8) and 0xFF)
        }.toByteArray()
        writeChunk(chunks, "ANIM", anim)

        for (frame in frameFiles) {
            val bitstream = extractBitstream(frame)
            val payload = ByteArrayOutputStream().apply {
                writeUInt24(0)                 // frame x（以 2px 为单位）
                writeUInt24(0)                 // frame y
                writeUInt24(width - 1)
                writeUInt24(height - 1)
                writeUInt24(durationMillis)
                write(0x02)                    // flags: 不做混合（每帧都是整幅不透明画面）
                write(bitstream)
            }.toByteArray()
            writeChunk(chunks, "ANMF", payload)
        }

        val body = chunks.toByteArray()
        val out = ByteArrayOutputStream()
        out.write("RIFF".toByteArray(Charsets.US_ASCII))
        writeUInt32(out, body.size + 4)
        out.write("WEBP".toByteArray(Charsets.US_ASCII))
        out.write(body)
        return out.toByteArray()
    }

    /** 从单帧 WebP 里取出 VP8L / VP8 码流（含 chunk 头，按 ANMF 的要求）。 */
    private fun extractBitstream(webp: ByteArray): ByteArray {
        require(webp.size > 12) { "not a webp" }
        var offset = 12
        while (offset + 8 <= webp.size) {
            val fourcc = String(webp, offset, 4, Charsets.US_ASCII)
            val size = readUInt32(webp, offset + 4)
            val dataStart = offset + 8
            val dataEnd = dataStart + size
            if (dataEnd > webp.size) break
            if (fourcc == "VP8L" || fourcc == "VP8 ") {
                val padded = dataEnd + (size and 1)
                return webp.copyOfRange(offset, minOf(padded, webp.size))
            }
            offset = dataEnd + (size and 1)
        }
        error("no VP8/VP8L bitstream in frame")
    }

    private fun writeChunk(out: ByteArrayOutputStream, fourcc: String, payload: ByteArray) {
        out.write(fourcc.toByteArray(Charsets.US_ASCII))
        writeUInt32(out, payload.size)
        out.write(payload)
        if (payload.size and 1 == 1) out.write(0)
    }

    private fun writeUInt32(out: ByteArrayOutputStream, value: Int) {
        out.write(value and 0xFF)
        out.write((value shr 8) and 0xFF)
        out.write((value shr 16) and 0xFF)
        out.write((value shr 24) and 0xFF)
    }

    private fun ByteArrayOutputStream.writeUInt24(value: Int) {
        write(value and 0xFF)
        write((value shr 8) and 0xFF)
        write((value shr 16) and 0xFF)
    }

    private fun readUInt32(data: ByteArray, offset: Int): Int =
        (data[offset].toInt() and 0xFF) or
            ((data[offset + 1].toInt() and 0xFF) shl 8) or
            ((data[offset + 2].toInt() and 0xFF) shl 16) or
            ((data[offset + 3].toInt() and 0xFF) shl 24)
}
