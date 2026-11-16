package dev.shiko.toolib

import dev.shiko.toolib.logic.GifWriter
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class GifWriterTest {

    @Test
    fun writesDecodableGif() {
        val width = 64
        val height = 48
        val pixels = IntArray(width * height) { index ->
            val x = index % width
            val y = index / width
            val r = (x * 4).coerceAtMost(255)
            val g = (y * 5).coerceAtMost(255)
            val b = 128
            (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }

        val bytes = GifWriter.encode(pixels, width, height)

        // 头部与尾部结构
        assertTrue("GIF89a header", String(bytes, 0, 6, Charsets.US_ASCII) == "GIF89a")
        assertTrue("trailer", bytes.last().toInt() == 0x3B)
        assertTrue("non trivial size", bytes.size > 200)

        // 顺手落一份文件，方便用别的工具再校验
        File("/tmp/toolib-gifwriter-test.gif").writeBytes(bytes)
    }

    @Test
    fun writesAnimatedGif() {
        val width = 32
        val height = 24
        val frames = (0 until 6).map { frame ->
            IntArray(width * height) { index ->
                val x = index % width
                val y = index / width
                (0xFF shl 24) or (((x * 8 + frame * 40) and 0xFF) shl 16) or
                    (((y * 10) and 0xFF) shl 8) or ((frame * 45) and 0xFF)
            }
        }

        val bytes = GifWriter.encodeAnimation(frames, width, height, delayCentiseconds = 7)

        assertTrue("GIF89a header", String(bytes, 0, 6, Charsets.US_ASCII) == "GIF89a")
        assertTrue("trailer", bytes.last().toInt() == 0x3B)
        // 6 帧 × 至少 1 个 LZW 块
        assertTrue("has frames", bytes.size > 300)
        File("/tmp/toolib-gifwriter-anim.gif").writeBytes(bytes)
    }
}
