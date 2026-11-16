package dev.shiko.toolib.logic

import android.content.ContentValues
import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import dev.shiko.toolib.R
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.util.Locale
import java.util.UUID
import org.json.JSONObject

/**
 * 媒体下载：yt-dlp（Chaquopy，进程内 Python）负责解析和下载原始流，
 * 合并 / 裁剪用系统 [MediaMuxer]，全程没有可执行二进制，
 * 因此不需要 `useLegacyPackaging`，也不需要任何原生 payload。
 */
class MediaDownloadManager(private val context: Context) {
    enum class Kind { VIDEO, AUDIO }

    data class Options(
        val kind: Kind = Kind.VIDEO,
        val selectedFormat: String? = null,
        /** 选中的格式是否自带音轨；不带音轨时会再下一条音频轨然后合并。 */
        val selectedHasAudio: Boolean = true,
        val playlist: Boolean = false,
        val playlistItems: List<Int> = emptyList(),
        val clipStart: String = "",
        val clipEnd: String = "",
        /** 用户临时选中的文件夹（SAF tree URI）；为空时写入默认相册 / 音乐目录。 */
        val saveTreeUri: String? = null,
        val sourceTitle: String = "",
        val sourceUploader: String = "",
        val sourceThumbnail: String = "",
        val sourceDuration: Long = 0L,
    )

    data class Result(
        val files: List<Uri>,
        val directory: String,
        val mimeType: String = "",
        val title: String = "",
        val url: String = "",
        val thumbnail: String = "",
        val duration: Long = 0L,
    )

    data class FormatInfo(
        val id: String,
        val extension: String,
        val width: Int,
        val height: Int,
        val fps: Int,
        val videoCodec: String,
        val audioCodec: String,
        val bitrate: Int,
        val fileSize: Long,
        val note: String,
    ) {
        val hasVideo: Boolean get() = videoCodec.isNotBlank() && videoCodec != "none"
        val hasAudio: Boolean get() = audioCodec.isNotBlank() && audioCodec != "none"

        /** Human readable label; the caller supplies the localized "video"/"audio" words. */
        fun label(videoLabel: String, audioLabel: String): String = buildString {
            append(if (hasVideo) (if (height > 0) "${height}p" else videoLabel) else audioLabel)
            if (extension.isNotBlank()) append(" · ").append(extension)
            if (bitrate > 0) append(" · ").append(bitrate).append(" kbps")
            if (fileSize > 0) append(" · ").append(fileSize / 1024 / 1024).append(" MB")
            if (note.isNotBlank()) append(" · ").append(note)
        }
    }

    data class PlaylistEntry(
        val index: Int,
        val title: String,
        val url: String,
        val duration: Long,
        val thumbnail: String,
    )

    data class Inspection(
        val title: String,
        val uploader: String,
        val thumbnail: String,
        val duration: Long,
        val formats: List<FormatInfo>,
        val playlist: List<PlaylistEntry>,
        val webpageUrl: String,
    ) {
        val isPlaylist: Boolean get() = playlist.isNotEmpty()
    }

    /** 下载之外的后处理阶段，UI 会把它显示在下载按钮上。 */
    enum class Stage { Muxing, Trimming }

    sealed interface Event {
        data object Preparing : Event
        data object Starting : Event
        data class Progress(
            val progressPercent: Float,
            val etaSeconds: Long,
            val speedBytesPerSecond: Long,
            val line: String,
        ) : Event
        data class StageChanged(val stage: Stage) : Event
        data class Output(val line: String) : Event
        data class Completed(val result: Result) : Event
        data class Failed(val error: Throwable) : Event
    }

    suspend fun inspect(url: String): Inspection {
        require(url.isNotBlank()) { context.getString(R.string.media_error_url_required) }
        val raw = try {
            YtDlpEngine.inspect(context, url)
        } catch (error: Throwable) {
            throw IOException(error.message ?: context.getString(R.string.media_error_invalid_info), error)
        }
        return parseInspection(JSONObject(raw), url)
    }

    suspend fun download(
        url: String,
        options: Options = Options(),
        onEvent: (Event) -> Unit = {},
    ): Result {
        require(url.isNotBlank()) { context.getString(R.string.media_error_url_required) }
        onEvent(Event.Preparing)
        val staging = File(context.cacheDir, "toolib-media-${UUID.randomUUID()}").apply {
            if (!mkdirs()) throw IOException(context.getString(R.string.media_error_staging_directory))
        }
        try {
            onEvent(Event.Starting)
            val selectors = buildSelectors(options)
            val payload = JSONObject(
                YtDlpEngine.download(
                    context = context,
                    url = url,
                    selectors = selectors,
                    outDir = File(staging, "src").absolutePath,
                    playlistItems = if (options.playlist) options.playlistItems else emptyList(),
                    callback = object : YtDlpEngine.ProgressCallback {
                        override fun onProgress(
                            percent: Float,
                            etaSeconds: Long,
                            speedBytesPerSecond: Long,
                            line: String,
                        ) {
                            onEvent(Event.Progress(percent, etaSeconds, speedBytesPerSecond, line))
                        }
                    },
                ),
            )
            val groups = parseGroups(payload)
            if (groups.all { it.isEmpty() }) throw IOException(context.getString(R.string.media_error_no_output))

            val outputs = process(groups, options, staging, onEvent)
            val mediaTitle = payload.optString("title").ifBlank { options.sourceTitle }
            val named = outputs.mapIndexed { index, file -> renameToTitle(file, mediaTitle, index) }
            val treeUri = options.saveTreeUri?.takeIf { it.isNotBlank() }?.let(Uri::parse)
            val relativePath = normalizeSaveLocation(null, options.kind)
            val published = if (treeUri != null) {
                named.map { publishToTree(treeUri, it) }
            } else {
                named.map { publish(it, relativePath, options.kind) }
            }
            return Result(
                files = published,
                directory = if (treeUri != null) SafPaths.treeDisplayPath(treeUri) else "/storage/emulated/0/$relativePath",
                mimeType = named.firstOrNull()?.let { mimeType(it.extension, options.kind) } ?: "",
                title = payload.optString("title").ifBlank { options.sourceTitle },
                url = url,
                thumbnail = options.sourceThumbnail,
                duration = payload.optLong("duration").takeIf { it > 0 } ?: options.sourceDuration,
            ).also { onEvent(Event.Completed(it)) }
        } catch (error: Throwable) {
            onEvent(Event.Failed(error))
            throw error
        } finally {
            staging.deleteRecursively()
        }
    }

    /** 视频要一条视频轨 + 一条音频轨再合并；音频只下一条最佳音轨。 */
    private fun buildSelectors(options: Options): List<String> {
        val picked = options.selectedFormat?.takeIf { it.isNotBlank() }
        return when (options.kind) {
            Kind.AUDIO -> listOf(picked ?: "ba[ext=m4a]/ba/b")
            Kind.VIDEO -> when {
                picked != null && options.selectedHasAudio -> listOf(picked)
                picked != null -> listOf(picked, BestAudio)
                else -> listOf(BestVideo, BestAudio)
            }
        }
    }

    private fun parseGroups(payload: JSONObject): List<List<File>> {
        val groups = payload.optJSONArray("groups") ?: return emptyList()
        return (0 until groups.length()).map { index ->
            val array = groups.optJSONArray(index) ?: return@map emptyList()
            (0 until array.length())
                .mapNotNull { array.optString(it).takeIf { path -> path.isNotBlank() } }
                .map { File(it) }
                .filter { it.isFile && it.length() > 0 }
        }
    }

    /** 合并 / 裁剪，返回最终要入库的文件。 */
    private fun process(
        groups: List<List<File>>,
        options: Options,
        staging: File,
        onEvent: (Event) -> Unit,
    ): List<File> {
        var current = groups.firstOrNull()?.firstOrNull()
            ?: throw IOException(context.getString(R.string.media_error_no_output))

        val audioTrack = groups.getOrNull(1)?.firstOrNull()
        if (options.kind == Kind.VIDEO && audioTrack != null) {
            onEvent(Event.StageChanged(Stage.Muxing))
            val merged = File(staging, "merged.mp4")
            mux(current, audioTrack, merged)
            current = merged
        }

        if (options.clipStart.isNotBlank() && options.clipEnd.isNotBlank()) {
            onEvent(Event.StageChanged(Stage.Trimming))
            val clipped = File(staging, "clip.${current.extension.ifBlank { "mp4" }}")
            trim(current, clipped, timecodeToMicros(options.clipStart), timecodeToMicros(options.clipEnd))
            current = clipped
        }
        return listOf(current)
    }

    /**
     * 把视频轨和音频轨原样写进同一个 mp4（不重编码）。
     * 输入必须是 MP4/M4A 这类 ISO BMFF 容器，[BestVideo] 已经限制了这一点。
     */
    private fun mux(video: File, audio: File, output: File) {
        val videoExtractor = MediaExtractor()
        val audioExtractor = MediaExtractor()
        var muxer: MediaMuxer? = null
        try {
            videoExtractor.setDataSource(video.absolutePath)
            audioExtractor.setDataSource(audio.absolutePath)
            muxer = MediaMuxer(output.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

            val videoSource = findTrack(videoExtractor, "video/")
                ?: throw IOException("no video track in ${video.name}")
            val audioSource = findTrack(audioExtractor, "audio/")
                ?: throw IOException("no audio track in ${audio.name}")

            videoExtractor.selectTrack(videoSource)
            audioExtractor.selectTrack(audioSource)
            val videoTarget = muxer.addTrack(videoExtractor.getTrackFormat(videoSource))
            val audioTarget = muxer.addTrack(audioExtractor.getTrackFormat(audioSource))

            muxer.start()
            copySamples(videoExtractor, videoTarget, muxer, maxInputSize(videoExtractor, videoSource), Long.MAX_VALUE, 0L)
            copySamples(audioExtractor, audioTarget, muxer, maxInputSize(audioExtractor, audioSource), Long.MAX_VALUE, 0L)
            muxer.stop()
        } finally {
            runCatching { muxer?.release() }
            runCatching { videoExtractor.release() }
            runCatching { audioExtractor.release() }
        }
    }

    /** 按时间区间裁一段（关键帧对齐，不重编码）。 */
    private fun trim(input: File, output: File, startUs: Long, endUs: Long) {
        val extractor = MediaExtractor()
        var muxer: MediaMuxer? = null
        try {
            extractor.setDataSource(input.absolutePath)
            muxer = MediaMuxer(output.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val targets = HashMap<Int, Int>()
            for (index in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(index)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
                if (!mime.startsWith("audio/") && !mime.startsWith("video/")) continue
                targets[index] = muxer.addTrack(format)
            }
            if (targets.isEmpty()) throw IOException("no media track in ${input.name}")
            muxer.start()
            for ((source, target) in targets) {
                extractor.selectTrack(source)
                extractor.seekTo(startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
                copySamples(extractor, target, muxer, maxInputSize(extractor, source), endUs, startUs)
                extractor.unselectTrack(source)
            }
            muxer.stop()
        } finally {
            runCatching { muxer?.release() }
            runCatching { extractor.release() }
        }
    }

    private fun copySamples(
        extractor: MediaExtractor,
        targetTrack: Int,
        muxer: MediaMuxer,
        bufferSize: Int,
        endUs: Long,
        startUs: Long,
    ) {
        val buffer = ByteBuffer.allocateDirect(bufferSize)
        val info = MediaCodec.BufferInfo()
        while (true) {
            val size = extractor.readSampleData(buffer, 0)
            if (size < 0) break
            val time = extractor.sampleTime
            if (time >= endUs) break
            if (time < startUs) {
                extractor.advance()
                continue
            }
            info.offset = 0
            info.size = size
            info.presentationTimeUs = time - startUs
            info.flags = if (extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) {
                MediaCodec.BUFFER_FLAG_KEY_FRAME
            } else {
                0
            }
            muxer.writeSampleData(targetTrack, buffer, info)
            extractor.advance()
        }
    }

    private fun findTrack(extractor: MediaExtractor, prefix: String): Int? {
        for (index in 0 until extractor.trackCount) {
            val mime = extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME) ?: continue
            if (mime.startsWith(prefix)) return index
        }
        return null
    }

    private fun maxInputSize(extractor: MediaExtractor, track: Int): Int {
        val format = extractor.getTrackFormat(track)
        val declared = if (format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
            format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE)
        } else {
            0
        }
        return declared.coerceIn(64 * 1024, 8 * 1024 * 1024)
    }

    private fun timecodeToMicros(value: String): Long {
        val parts = value.split(':')
        var seconds = 0L
        for (part in parts) seconds = seconds * 60 + (part.toLongOrNull() ?: 0L)
        return seconds * 1_000_000L
    }

    /** 产物统一按媒体标题命名（合并 / 裁剪出来的临时文件名不带出去）。 */
    private fun renameToTitle(file: File, title: String, index: Int): File {
        val base = sanitizeFileName(title)
        val extension = file.extension.ifBlank { "mp4" }
        val suffix = if (index == 0) "" else " (${index + 1})"
        val target = File(file.parentFile, "$base$suffix.$extension")
        if (target == file) return file
        return if (file.renameTo(target)) target else file
    }

    private fun sanitizeFileName(raw: String): String {
        val cleaned = raw
            .replace(Regex("[\\\\/:*?\"<>|]"), " ")
            .replace(Regex("[\\p{Cntrl}]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .trim('.')
        return cleaned.take(120).ifBlank { "media" }
    }

    private fun parseInspection(json: JSONObject, sourceUrl: String): Inspection {
        val formatsJson = json.optJSONArray("formats")
        val formats = buildList {
            if (formatsJson != null) {
                for (index in 0 until formatsJson.length()) {
                    val item = formatsJson.optJSONObject(index) ?: continue
                    val id = item.optString("id")
                    if (id.isBlank()) continue
                    add(
                        FormatInfo(
                            id = id,
                            extension = item.optString("ext"),
                            width = item.optInt("width"),
                            height = item.optInt("height"),
                            fps = item.optInt("fps"),
                            videoCodec = item.optString("vcodec"),
                            audioCodec = item.optString("acodec"),
                            bitrate = item.optDouble("tbr").toInt(),
                            fileSize = item.optLong("filesize"),
                            note = item.optString("note"),
                        ),
                    )
                }
            }
        }
        val entriesJson = json.optJSONArray("entries")
        val playlist = buildList {
            if (entriesJson != null) {
                for (index in 0 until entriesJson.length()) {
                    val item = entriesJson.optJSONObject(index) ?: continue
                    add(
                        PlaylistEntry(
                            index = item.optInt("index", index + 1),
                            title = item.optString("title"),
                            url = item.optString("url"),
                            duration = item.optLong("duration"),
                            thumbnail = item.optString("thumbnail"),
                        ),
                    )
                }
            }
        }
        return Inspection(
            title = json.optString("title").ifBlank { sourceUrl },
            uploader = json.optString("uploader"),
            thumbnail = json.optString("thumbnail"),
            duration = json.optLong("duration"),
            formats = formats
                .distinctBy { it.id }
                .sortedWith(
                    compareByDescending<FormatInfo> { it.hasVideo }
                        .thenByDescending { it.height }
                        .thenByDescending { it.bitrate },
                ),
            playlist = playlist,
            webpageUrl = json.optString("webpageUrl").ifBlank { sourceUrl },
        )
    }

    /** 写进用户用系统选择器挑的文件夹（SAF）。 */
    private fun publishToTree(treeUri: Uri, file: File): Uri {
        val resolver = context.contentResolver
        val parent = DocumentsContract.buildDocumentUriUsingTree(
            treeUri,
            DocumentsContract.getTreeDocumentId(treeUri),
        )
        val target = DocumentsContract.createDocument(
            resolver,
            parent,
            mimeType(file.extension, Kind.VIDEO),
            file.name,
        ) ?: throw IOException(context.getString(R.string.media_error_create_file))
        try {
            resolver.openOutputStream(target)?.use { output ->
                file.inputStream().use { input -> input.copyTo(output) }
            } ?: throw IOException(context.getString(R.string.media_error_open_file))
            return target
        } catch (error: Throwable) {
            runCatching { DocumentsContract.deleteDocument(resolver, target) }
            throw error
        }
    }

    private fun publish(file: File, directory: String, kind: Kind): Uri {
        val mimeType = mimeType(file.extension, kind)
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, file.name)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, "$directory/")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val collection = when (kind) {
            Kind.AUDIO -> MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            Kind.VIDEO -> MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        }
        val uri = context.contentResolver.insert(collection, values)
            ?: throw IOException(context.getString(R.string.media_error_create_file))
        try {
            context.contentResolver.openOutputStream(uri, "w")?.use { output ->
                file.inputStream().use { input -> input.copyTo(output) }
            } ?: throw IOException(context.getString(R.string.media_error_open_file))
            val done = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
            context.contentResolver.update(uri, done, null, null)
            return uri
        } catch (error: Throwable) {
            context.contentResolver.delete(uri, null, null)
            throw error
        }
    }

    private fun mimeType(extension: String, kind: Kind): String = when (extension.lowercase(Locale.US)) {
        "mp3" -> "audio/mpeg"
        "m4a" -> if (kind == Kind.AUDIO) "audio/mp4" else "video/mp4"
        "aac" -> "audio/aac"
        "flac" -> "audio/flac"
        "ogg" -> "audio/ogg"
        "opus" -> "audio/opus"
        "wav" -> "audio/wav"
        "webm" -> if (kind == Kind.AUDIO) "audio/webm" else "video/webm"
        "mkv" -> "video/x-matroska"
        "3gp" -> "video/3gpp"
        "mov" -> "video/quicktime"
        "mp4" -> if (kind == Kind.AUDIO) "audio/mp4" else "video/mp4"
        else -> if (kind == Kind.AUDIO) "audio/*" else "video/mp4"
    }

    companion object {
        /** 视频优先选 H.264 + AAC 的 mp4：MediaMuxer 只能把这类流原样写进 mp4。 */
        private const val BestVideo = "bv*[ext=mp4][vcodec^=avc1]/bv*[ext=mp4]/bv*/b"
        private const val BestAudio = "ba[ext=m4a]/ba/b"

        /** 默认保存目录（相对外部存储根）。 */
        fun defaultSaveLocation(kind: Kind): String =
            if (kind == Kind.AUDIO) Environment.DIRECTORY_MUSIC else Environment.DIRECTORY_MOVIES

        /**
         * 把用户填的位置规范成 MediaStore 能接受的相对路径：
         * 去掉 `/storage/emulated/0/` 前缀；首段不是该类型的合法目录时补上默认目录。
         */
        fun normalizeSaveLocation(raw: String?, kind: Kind): String {
            val root = defaultSaveLocation(kind)
            val cleaned = (raw ?: "")
                .replace('\\', '/')
                .trim()
                .removePrefix("/storage/emulated/0/")
                .trim('/')
            if (cleaned.isBlank()) return root
            val allowed = if (kind == Kind.AUDIO) {
                setOf(Environment.DIRECTORY_MUSIC, Environment.DIRECTORY_PODCASTS, Environment.DIRECTORY_DOWNLOADS)
            } else {
                setOf(Environment.DIRECTORY_MOVIES, Environment.DIRECTORY_DCIM, Environment.DIRECTORY_DOWNLOADS)
            }
            val first = cleaned.substringBefore('/')
            return if (first in allowed) cleaned else "$root/$cleaned"
        }

        /** 给 UI 显示的完整路径。 */
        fun displaySaveLocation(raw: String?, kind: Kind): String =
            "/storage/emulated/0/" + normalizeSaveLocation(raw, kind)
    }
}
