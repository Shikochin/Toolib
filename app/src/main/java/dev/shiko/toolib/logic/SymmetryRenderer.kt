package dev.shiko.toolib.logic

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint

/** 对称方式：左右镜像、上下镜像，或两者都做（四向对称）。 */
enum class SymmetryMode { LeftRight, TopBottom, Both }

/**
 * 把图片做成对称图：以一条轴为界，保留较大的那一侧，镜像到另一侧。
 * 用 Canvas 的裁剪 + 负缩放实现，不重采样像素，速度只跟图大小有关。
 */
object SymmetryRenderer {

    private val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)

    fun render(source: Bitmap, mode: SymmetryMode, axisX: Float, axisY: Float): Bitmap {
        var current = source
        if (mode == SymmetryMode.LeftRight || mode == SymmetryMode.Both) {
            current = mirrorHorizontal(current, axisX)
        }
        if (mode == SymmetryMode.TopBottom || mode == SymmetryMode.Both) {
            current = mirrorVertical(current, axisY)
        }
        if (current === source) {
            current = source.copy(Bitmap.Config.ARGB_8888, false)
        }
        return current
    }

    private fun mirrorHorizontal(source: Bitmap, axisFraction: Float): Bitmap {
        val width = source.width
        val height = source.height
        val axis = (axisFraction.coerceIn(0.02f, 0.98f) * width)
        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        canvas.drawBitmap(source, 0f, 0f, paint)
        canvas.save()
        // 保留大的那半，把镜像画到小的那半上
        if (axis >= width / 2f) {
            canvas.clipRect(0f, 0f, axis, height.toFloat())
        } else {
            canvas.clipRect(axis, 0f, width.toFloat(), height.toFloat())
        }
        canvas.scale(-1f, 1f, axis, 0f)
        canvas.drawBitmap(source, 0f, 0f, paint)
        canvas.restore()
        return output
    }

    private fun mirrorVertical(source: Bitmap, axisFraction: Float): Bitmap {
        val width = source.width
        val height = source.height
        val axis = (axisFraction.coerceIn(0.02f, 0.98f) * height)
        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        canvas.drawBitmap(source, 0f, 0f, paint)
        canvas.save()
        if (axis >= height / 2f) {
            canvas.clipRect(0f, 0f, width.toFloat(), axis)
        } else {
            canvas.clipRect(0f, axis, width.toFloat(), height.toFloat())
        }
        canvas.scale(1f, -1f, 0f, axis)
        canvas.drawBitmap(source, 0f, 0f, paint)
        canvas.restore()
        return output
    }

    /** 等比缩到长边不超过 [maxSize]，用来做实时预览和二次编辑的底图。 */
    fun scaleDown(source: Bitmap, maxSize: Int): Bitmap {
        val longest = maxOf(source.width, source.height)
        if (longest <= maxSize) return source
        val scale = maxSize.toFloat() / longest
        val width = (source.width * scale).toInt().coerceAtLeast(1)
        val height = (source.height * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(source, width, height, true)
    }
}
