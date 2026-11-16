package dev.shiko.toolib

import dev.shiko.toolib.logic.AnimatedWebpWriter
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class AnimatedWebpWriterTest {

    @Test
    fun wrapsFramesIntoAnimatedWebp() {
        val frames = (1..3).map { index ->
            javaClass.classLoader!!.getResourceAsStream("frame$index.webp")!!.readBytes()
        }

        val bytes = AnimatedWebpWriter.encode(frames, width = 32, height = 24, durationMillis = 70)

        assertTrue("RIFF header", String(bytes, 0, 4, Charsets.US_ASCII) == "RIFF")
        assertTrue("WEBP tag", String(bytes, 8, 4, Charsets.US_ASCII) == "WEBP")
        assertTrue("has content", bytes.size > 200)
        File("/tmp/toolib-anim.webp").writeBytes(bytes)
    }
}
