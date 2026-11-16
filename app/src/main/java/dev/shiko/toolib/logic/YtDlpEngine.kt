package dev.shiko.toolib.logic

import android.content.Context
import com.chaquo.python.PyObject
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import org.json.JSONArray

/**
 * 进程内的 yt-dlp 引擎。
 *
 * Chaquopy 把 CPython 作为原生库加载（`System.loadLibrary`），yt-dlp 直接跑在当前进程里，
 * 因此不需要把可执行文件解压到 `nativeLibraryDir`，也就不再需要 `useLegacyPackaging`。
 * 所有后处理（合并 / 转码 / 内嵌）交给 [MediaDownloadManager] 里的 ffmpeg-kit 完成，
 * yt-dlp 本身只负责解析链接和下载原始流。
 */
object YtDlpEngine {
    private const val Module = "toolib_media"

    /** 下载进度回调，由 Python 侧直接调用。 */
    interface ProgressCallback {
        fun onProgress(percent: Float, etaSeconds: Long, speedBytesPerSecond: Long, line: String)
    }

    @Volatile
    private var module: PyObject? = null

    /** 启动 Python（幂等），返回桥接模块。 */
    @Synchronized
    fun module(context: Context): PyObject {
        module?.let { return it }
        if (!Python.isStarted()) {
            Python.start(AndroidPlatform(context.applicationContext))
        }
        return Python.getInstance().getModule(Module).also { module = it }
    }

    /** 引擎自检：返回 yt-dlp 版本。 */
    fun version(context: Context): String = module(context).callAttr("version").toString()

    /** 解析链接，返回 JSON（字段见 toolib_media.inspect）。 */
    fun inspect(context: Context, url: String): String =
        module(context).callAttr("inspect", url).toString()

    /**
     * 按格式选择器依次下载，返回 JSON（字段见 toolib_media.download）。
     * 每个选择器的产物落在 `outDir/<序号>/`，第一个通常是视频轨、第二个是音频轨。
     */
    fun download(
        context: Context,
        url: String,
        selectors: List<String>,
        outDir: String,
        playlistItems: List<Int>,
        callback: ProgressCallback?,
        subtitleLangs: String = "",
    ): String = module(context)
        .callAttr(
            "download",
            url,
            JSONArray(selectors).toString(),
            outDir,
            JSONArray(playlistItems).toString(),
            callback,
            subtitleLangs,
        )
        .toString()
}
