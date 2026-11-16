package dev.shiko.toolib.logic

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.ByteArrayOutputStream
import java.io.IOException

/** 对称艺术支持的输出格式（AVIF 平台没有编码器，见 README）。 */
enum class ImageFormat(val extension: String, val mimeType: String, val supportsQuality: Boolean) {
    Png("png", "image/png", false),
    Jpeg("jpg", "image/jpeg", true),
    Webp("webp", "image/webp", true),
    Gif("gif", "image/gif", false),
}

/** 图片编码 + 保存（默认 Pictures/Toolib，或用户用 SAF 选的位置）。 */
object ImageExporter {

    private const val AlbumDirectory = "Pictures/Toolib"

    fun encode(bitmap: Bitmap, format: ImageFormat, quality: Int): ByteArray {
        if (format == ImageFormat.Gif) {
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            return GifWriter.encode(pixels, bitmap.width, bitmap.height)
        }
        val output = ByteArrayOutputStream()
        val source = if (format == ImageFormat.Jpeg) flattenOnWhite(bitmap) else bitmap
        val compressFormat = when (format) {
            ImageFormat.Png -> Bitmap.CompressFormat.PNG
            ImageFormat.Jpeg -> Bitmap.CompressFormat.JPEG
            ImageFormat.Webp -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Bitmap.CompressFormat.WEBP_LOSSY
            } else {
                @Suppress("DEPRECATION")
                Bitmap.CompressFormat.WEBP
            }
            ImageFormat.Gif -> error("handled above")
        }
        val q = quality.coerceIn(1, 100)
        if (!source.compress(compressFormat, q, output)) {
            throw IOException("failed to encode ${format.extension}")
        }
        if (source !== bitmap) source.recycle()
        return output.toByteArray()
    }

    /** JPEG 没有透明通道，先把透明像素压到白底上。 */
    private fun flattenOnWhite(bitmap: Bitmap): Bitmap {
        val output = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(output)
        canvas.drawColor(android.graphics.Color.WHITE)
        canvas.drawBitmap(bitmap, 0f, 0f, null)
        return output
    }

    /** 写进系统相册的 Pictures/Toolib。 */
    fun saveToPictures(context: Context, bytes: ByteArray, fileName: String, mimeType: String): Uri {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, "$AlbumDirectory/")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val uri = resolver.insert(collection, values) ?: throw IOException("cannot create image entry")
        try {
            resolver.openOutputStream(uri)?.use { it.write(bytes) } ?: throw IOException("cannot open output")
            val done = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
            resolver.update(uri, done, null, null)
            return uri
        } catch (error: Throwable) {
            runCatching { resolver.delete(uri, null, null) }
            throw error
        }
    }

    /** 写进用户用 SAF 选的文件（ACTION_CREATE_DOCUMENT 的返回）。 */
    fun saveToDocument(context: Context, uri: Uri, bytes: ByteArray) {
        context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
            ?: throw IOException("cannot open document")
    }

    /** 写进用户用 SAF 选的文件夹。 */
    fun saveToTree(context: Context, treeUri: Uri, bytes: ByteArray, fileName: String, mimeType: String): Uri {
        val resolver = context.contentResolver
        val parent = android.provider.DocumentsContract.buildDocumentUriUsingTree(
            treeUri,
            android.provider.DocumentsContract.getTreeDocumentId(treeUri),
        )
        val target = android.provider.DocumentsContract.createDocument(resolver, parent, mimeType, fileName)
            ?: throw IOException("cannot create document")
        resolver.openOutputStream(target)?.use { it.write(bytes) } ?: throw IOException("cannot open output")
        return target
    }

    /** 默认相册目录在界面上的显示文案。 */
    fun defaultDirectoryLabel(): String =
        "/storage/emulated/0/" + Environment.DIRECTORY_PICTURES + "/Toolib"

    fun buildFileName(format: ImageFormat): String =
        "Toolib-${System.currentTimeMillis()}.${format.extension}"
}
