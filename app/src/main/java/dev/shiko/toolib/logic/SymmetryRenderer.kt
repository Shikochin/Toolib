package dev.shiko.toolib.logic

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import kotlin.math.max
import kotlin.math.roundToInt

/** 对称方式：保留哪一侧，并把这一侧镜像到另一侧。 */
enum class SymmetryMode { Left, Right, Top, Bottom, Both }

/**
 * 把图片做成对称图：以反色线为界，取指定的一侧，再把这一侧镜像出来，
 * 输出只包含"保留的部分 + 它的镜像"，不保留原图其余部分。
 *
 * 例：左对称、线在 30% 处 → 输出宽度是原图的 60%，左半是原图左 30%，右半是它的镜像。
 */
object SymmetryRenderer {

    private val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)

    fun render(source: Bitmap, mode: SymmetryMode, axisX: Float, axisY: Float): Bitmap {
        var current = source
        when (mode) {
            SymmetryMode.Left -> current = mirrorHorizontal(current, axisX, keepLeft = true)
            SymmetryMode.Right -> current = mirrorHorizontal(current, axisX, keepLeft = false)
            SymmetryMode.Top -> current = mirrorVertical(current, axisY, keepTop = true)
            SymmetryMode.Bottom -> current = mirrorVertical(current, axisY, keepTop = false)
            SymmetryMode.Both -> {
                current = mirrorHorizontal(current, axisX, keepLeft = true)
                current = mirrorVertical(current, axisY, keepTop = true)
            }
        }
        if (current === source) {
            current = source.copy(Bitmap.Config.ARGB_8888, false)
        }
        return current
    }

    private fun mirrorHorizontal(source: Bitmap, axisFraction: Float, keepLeft: Boolean): Bitmap {
        val width = source.width
        val height = source.height
        val axis = (axisFraction.coerceIn(0.02f, 0.98f) * width).roundToInt()
        val keepWidth = if (keepLeft) axis else width - axis
        if (keepWidth <= 0) return source.copy(Bitmap.Config.ARGB_8888, false)
        val output = Bitmap.createBitmap(keepWidth * 2, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val src = if (keepLeft) Rect(0, 0, axis, height) else Rect(axis, 0, width, height)
        val dst = if (keepLeft) {
            RectF(0f, 0f, keepWidth.toFloat(), height.toFloat())
        } else {
            RectF(keepWidth.toFloat(), 0f, (keepWidth * 2).toFloat(), height.toFloat())
        }
        canvas.drawBitmap(source, src, dst, paint)
        canvas.save()
        // 以拼接缝为轴翻转，把保留的部分镜像到另一半
        canvas.scale(-1f, 1f, keepWidth.toFloat(), 0f)
        canvas.drawBitmap(source, src, dst, paint)
        canvas.restore()
        return output
    }

    private fun mirrorVertical(source: Bitmap, axisFraction: Float, keepTop: Boolean): Bitmap {
        val width = source.width
        val height = source.height
        val axis = (axisFraction.coerceIn(0.02f, 0.98f) * height).roundToInt()
        val keepHeight = if (keepTop) axis else height - axis
        if (keepHeight <= 0) return source.copy(Bitmap.Config.ARGB_8888, false)
        val output = Bitmap.createBitmap(width, keepHeight * 2, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val src = if (keepTop) Rect(0, 0, width, axis) else Rect(0, axis, width, height)
        val dst = if (keepTop) {
            RectF(0f, 0f, width.toFloat(), keepHeight.toFloat())
        } else {
            RectF(0f, keepHeight.toFloat(), width.toFloat(), (keepHeight * 2).toFloat())
        }
        canvas.drawBitmap(source, src, dst, paint)
        canvas.save()
        canvas.scale(1f, -1f, 0f, keepHeight.toFloat())
        canvas.drawBitmap(source, src, dst, paint)
        canvas.restore()
        return output
    }

    /** 等比缩到长边不超过 [maxSize]，用来做实时预览和二次编辑的底图。 */
    fun scaleDown(source: Bitmap, maxSize: Int): Bitmap {
        val longest = maxOf(source.width, source.height)
        if (longest <= maxSize) return source
        val scale = maxSize.toFloat() / longest
        val width = max(1, (source.width * scale).toInt())
        val height = max(1, (source.height * scale).toInt())
        return Bitmap.createScaledBitmap(source, width, height, true)
    }
}
