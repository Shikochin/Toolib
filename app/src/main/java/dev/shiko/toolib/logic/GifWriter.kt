package dev.shiko.toolib.logic

/**
 * 单帧 GIF 编码器（纯 Kotlin，不依赖 Android）。
 *
 * GIF 只有 256 色调色板，所以先把图片用中位切分量化到 256 色，再走标准 LZW 压缩。
 * 只输出静态图：Android 平台没有公开的动图编码 API，动图输入取第一帧。
 */
object GifWriter {

    /** @param pixels 行优先的 ARGB 像素 */
    fun encode(pixels: IntArray, width: Int, height: Int): ByteArray {
        require(width > 0 && height > 0) { "empty image" }
        require(pixels.size >= width * height) { "pixel buffer too small" }

        val palette = quantize(pixels, width * height, MaxColors)
        val indices = ByteArray(width * height)
        val lookup = HashMap<Int, Int>()
        for (i in 0 until width * height) {
            val argb = pixels[i]
            val key = ((argb shr 19) and 0x1F shl 10) or ((argb shr 11) and 0x1F shl 5) or ((argb shr 3) and 0x1F)
            val index = lookup.getOrPut(key) { nearestColor(palette, argb) }
            indices[i] = index.toByte()
        }

        val out = ByteSink()
        out.ascii("GIF89a")
        out.u16(width)
        out.u16(height)
        out.byte(0xF7) // 全局调色板存在 | 256 色
        out.byte(0)    // 背景色索引
        out.byte(0)    // 像素宽高比
        for (i in 0 until MaxColors) {
            val color = if (i < palette.size) palette[i] else 0
            out.byte((color shr 16) and 0xFF)
            out.byte((color shr 8) and 0xFF)
            out.byte(color and 0xFF)
        }
        // 图像描述符
        out.byte(0x2C)
        out.u16(0)
        out.u16(0)
        out.u16(width)
        out.u16(height)
        out.byte(0)
        // LZW 数据
        val compressed = lzw(indices, 8)
        out.byte(8)
        var offset = 0
        while (offset < compressed.size) {
            val size = minOf(255, compressed.size - offset)
            out.byte(size)
            out.bytes(compressed, offset, size)
            offset += size
        }
        out.byte(0)
        out.byte(0x3B)
        return out.toByteArray()
    }

    private const val MaxColors = 256

    /** 中位切分量化：采样 → 反复按最长通道切分 → 每箱取平均色。 */
    private fun quantize(pixels: IntArray, count: Int, maxColors: Int): IntArray {
        val step = maxOf(1, count / 40000)
        val samples = ArrayList<Int>(count / step + 1)
        var i = 0
        while (i < count) {
            val argb = pixels[i]
            if ((argb ushr 24) != 0) samples.add(argb and 0xFFFFFF)
            i += step
        }
        if (samples.isEmpty()) return intArrayOf(0)
        var boxes = listOf(samples.toIntArray())
        while (boxes.size < maxColors) {
            // 每次切“颜色跨度最大”的那个箱子，调色板才不会挤在局部
            var splitIndex = -1
            var widest = -1
            boxes.forEachIndexed { index, box ->
                if (box.size < 2) return@forEachIndexed
                val range = widestRange(box)
                if (range > widest) {
                    widest = range
                    splitIndex = index
                }
            }
            if (splitIndex < 0 || widest <= 0) break
            val box = boxes[splitIndex]
            val channel = widestChannel(box)
            val sorted = box.sortedBy { (it shr shiftFor(channel)) and 0xFF }.toIntArray()
            val middle = sorted.size / 2
            if (middle == 0 || middle >= sorted.size) break
            boxes = boxes.toMutableList().apply {
                removeAt(splitIndex)
                add(sorted.copyOfRange(0, middle))
                add(sorted.copyOfRange(middle, sorted.size))
            }
        }
        return IntArray(boxes.size) { index ->
            val box = boxes[index]
            var r = 0L
            var g = 0L
            var b = 0L
            for (color in box) {
                r += (color shr 16) and 0xFF
                g += (color shr 8) and 0xFF
                b += color and 0xFF
            }
            val n = box.size.coerceAtLeast(1)
            (((r / n).toInt() and 0xFF) shl 16) or (((g / n).toInt() and 0xFF) shl 8) or ((b / n).toInt() and 0xFF)
        }
    }

    private fun shiftFor(channel: Int): Int = when (channel) {
        0 -> 16
        1 -> 8
        else -> 0
    }

    private fun widestRange(box: IntArray): Int {
        var minR = 255; var maxR = 0
        var minG = 255; var maxG = 0
        var minB = 255; var maxB = 0
        for (color in box) {
            val r = (color shr 16) and 0xFF
            val g = (color shr 8) and 0xFF
            val b = color and 0xFF
            if (r < minR) minR = r; if (r > maxR) maxR = r
            if (g < minG) minG = g; if (g > maxG) maxG = g
            if (b < minB) minB = b; if (b > maxB) maxB = b
        }
        return maxOf(maxR - minR, maxG - minG, maxB - minB)
    }

    private fun widestChannel(box: IntArray): Int {
        var minR = 255; var maxR = 0
        var minG = 255; var maxG = 0
        var minB = 255; var maxB = 0
        for (color in box) {
            val r = (color shr 16) and 0xFF
            val g = (color shr 8) and 0xFF
            val b = color and 0xFF
            if (r < minR) minR = r; if (r > maxR) maxR = r
            if (g < minG) minG = g; if (g > maxG) maxG = g
            if (b < minB) minB = b; if (b > maxB) maxB = b
        }
        val dr = maxR - minR
        val dg = maxG - minG
        val db = maxB - minB
        return when {
            dr >= dg && dr >= db -> 0
            dg >= db -> 1
            else -> 2
        }
    }

    private fun nearestColor(palette: IntArray, argb: Int): Int {
        val r = (argb shr 16) and 0xFF
        val g = (argb shr 8) and 0xFF
        val b = argb and 0xFF
        var best = 0
        var bestDistance = Int.MAX_VALUE
        for (i in palette.indices) {
            val color = palette[i]
            val dr = r - ((color shr 16) and 0xFF)
            val dg = g - ((color shr 8) and 0xFF)
            val db = b - (color and 0xFF)
            val distance = dr * dr + dg * dg + db * db
            if (distance < bestDistance) {
                bestDistance = distance
                best = i
                if (distance == 0) break
            }
        }
        return best
    }

    /** GIF 的 LZW：9 位起始码，满 4096 发 clear。 */
    private fun lzw(indices: ByteArray, minCodeSize: Int): ByteArray {
        val clearCode = 1 shl minCodeSize
        val endCode = clearCode + 1
        val bits = BitWriter()
        var codeSize = minCodeSize + 1
        var next = endCode + 1
        var dictionary = HashMap<Int, Int>()
        bits.write(clearCode, codeSize)
        var prefix = -1
        for (index in indices) {
            val current = index.toInt() and 0xFF
            if (prefix < 0) {
                prefix = current
                continue
            }
            val key = (prefix shl 8) or current
            val known = dictionary[key]
            if (known != null) {
                prefix = known
                continue
            }
            bits.write(prefix, codeSize)
            if (next < 4096) {
                dictionary[key] = next
                next++
                // 解码端比编码端晚一步建表，所以这里要严格大于（写 >= 会错位一个码）
                if (next > (1 shl codeSize) && codeSize < 12) codeSize++
            } else {
                bits.write(clearCode, codeSize)
                dictionary = HashMap()
                next = endCode + 1
                codeSize = minCodeSize + 1
            }
            prefix = current
        }
        if (prefix >= 0) bits.write(prefix, codeSize)
        bits.write(endCode, codeSize)
        return bits.toByteArray()
    }

    /** GIF 的位流是 LSB 优先。 */
    private class BitWriter {
        private val out = ByteSink()
        private var accumulator = 0
        private var bitCount = 0

        fun write(code: Int, size: Int) {
            accumulator = accumulator or (code shl bitCount)
            bitCount += size
            while (bitCount >= 8) {
                out.byte(accumulator and 0xFF)
                accumulator = accumulator ushr 8
                bitCount -= 8
            }
        }

        fun toByteArray(): ByteArray {
            if (bitCount > 0) {
                out.byte(accumulator and 0xFF)
                accumulator = 0
                bitCount = 0
            }
            return out.toByteArray()
        }
    }

    private class ByteSink {
        private var buffer = ByteArray(1024)
        private var size = 0

        private fun ensure(extra: Int) {
            if (size + extra <= buffer.size) return
            var capacity = buffer.size
            while (capacity < size + extra) capacity *= 2
            buffer = buffer.copyOf(capacity)
        }

        fun byte(value: Int) {
            ensure(1)
            buffer[size++] = value.toByte()
        }

        fun u16(value: Int) {
            byte(value and 0xFF)
            byte((value shr 8) and 0xFF)
        }

        fun ascii(text: String) {
            for (char in text) byte(char.code)
        }

        fun bytes(source: ByteArray, offset: Int, length: Int) {
            ensure(length)
            System.arraycopy(source, offset, buffer, size, length)
            size += length
        }

        fun toByteArray(): ByteArray = buffer.copyOf(size)
    }
}
