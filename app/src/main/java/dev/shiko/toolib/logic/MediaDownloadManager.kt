package dev.shiko.toolib.logic

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import dev.shiko.toolib.R
import java.io.File
import java.io.IOException
import java.util.Locale
import java.util.UUID
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest

/** Runs yt-dlp with its bundled Python and FFmpeg runtime. */
class MediaDownloadManager(private val context: Context) {
    enum class Kind { VIDEO, AUDIO }

    data class Options(
        val kind: Kind = Kind.VIDEO,
        val audioFormat: String = "mp3",
        val audioQuality: String = "0",
        val playlist: Boolean = false,
        val embedMetadata: Boolean = true,
        val embedThumbnail: Boolean = false,
        val writeSubtitles: Boolean = false,
        val subtitleLanguages: String = "all",
        val playlistItems: List<Int> = emptyList(),
        val selectedFormat: String? = null,
        val sourceTitle: String = "",
        val sourceUploader: String = "",
        val sourceThumbnail: String = "",
        val sourceDuration: Long = 0L,
        val embedSubtitles: Boolean = false,
        val clipStart: String = "",
        val clipEnd: String = "",
    )

    data class Result(
        val files: List<Uri>,
        val directory: String,
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

    sealed interface Event {
        data object Preparing : Event
        data object Starting : Event
        data class Progress(val progressPercent: Float, val etaSeconds: Long, val line: String) : Event
        data class Output(val line: String) : Event
        data class Completed(val result: Result) : Event
        data class Failed(val error: Throwable) : Event
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
            initialize()
            val outputTemplate = File(staging, "%(title)s.%(ext)s").absolutePath
            val request = buildRequest(url, outputTemplate, options)
            onEvent(Event.Starting)
            val processId = UUID.randomUUID().toString()
            YoutubeDL.getInstance().execute(request, processId) { percent, eta, line ->
                onEvent(Event.Progress(percent, eta, line))
            }

            val files = staging.walkTopDown()
                .filter { file -> file.isFile && file.length() > 0L && !file.name.endsWith(".part") && !file.name.endsWith(".ytdl") }
                .filter { file -> options.kind != Kind.VIDEO || file.extension.lowercase(Locale.US) !in AUDIO_EXTENSIONS }
                .toList()
            if (files.isEmpty()) throw IOException(context.getString(R.string.media_error_no_output))

            val relativePath = if (options.kind == Kind.AUDIO) Environment.DIRECTORY_MUSIC else Environment.DIRECTORY_MOVIES
            val published = files.map { publish(it, relativePath, options.kind) }
            return Result(
                files = published,
                directory = "/storage/emulated/0/$relativePath",
                title = options.sourceTitle,
                url = url,
                thumbnail = options.sourceThumbnail,
                duration = options.sourceDuration,
            ).also { onEvent(Event.Completed(it)) }
        } catch (error: Throwable) {
            onEvent(Event.Failed(error))
            throw error
        } finally {
            staging.deleteRecursively()
        }
    }

    suspend fun inspect(url: String): Inspection {
        require(url.isNotBlank()) { context.getString(R.string.media_error_url_required) }
        initialize()
        val request = YoutubeDLRequest(url).apply {
            addOption("--dump-single-json")
            addOption("--flat-playlist")
            addOption("--skip-download")
            addOption("--no-warnings")
            addOption("--no-cache-dir")
            addOption("--socket-timeout", "15")
        }
        val response = YoutubeDL.getInstance().execute(request)
        return parseInspection(response.out, url)
    }

    private fun initialize() {
        synchronized(INIT_LOCK) {
            if (initialized) return
            YoutubeDL.getInstance().init(context.applicationContext)
            FFmpeg.getInstance().init(context.applicationContext)
            initialized = true
        }
    }

    private fun buildRequest(url: String, outputTemplate: String, options: Options): YoutubeDLRequest {
        val request = YoutubeDLRequest(url)
        request.addOption("--no-cache-dir")
        request.addOption("--newline")
        request.addOption("--no-warnings")
        request.addOption("--no-mtime")
        if (options.playlist && options.playlistItems.isNotEmpty()) {
            request.addOption("--playlist-items", options.playlistItems.joinToString(","))
        } else if (!options.playlist) {
            request.addOption("--no-playlist")
        }
        if (options.embedMetadata) request.addOption("--embed-metadata")
        if (options.embedThumbnail) request.addOption("--embed-thumbnail")
        if (options.writeSubtitles) {
            request.addOption("--write-subs")
            request.addOption("--sub-langs", options.subtitleLanguages.ifBlank { "all" })
            if (options.embedSubtitles) request.addOption("--embed-subs")
        }
        if (options.clipStart.isNotBlank() && options.clipEnd.isNotBlank()) {
            request.addOption("--download-sections", "*${options.clipStart}-${options.clipEnd}")
            request.addOption("--force-keyframes-at-cuts")
        }

        if (options.kind == Kind.AUDIO) {
            options.selectedFormat?.takeIf { it.isNotBlank() }?.let { request.addOption("-f", it) }
            request.addOption("-x")
            request.addOption("--audio-format", options.audioFormat)
            request.addOption("--audio-quality", options.audioQuality)
        } else {
            request.addOption("-f", options.selectedFormat?.takeIf { it.isNotBlank() } ?: DEFAULT_VIDEO_FORMAT)
            request.addOption("--merge-output-format", "mp4")
        }
        request.addOption("-o", outputTemplate)
        return request
    }

    private fun parseInspection(raw: String, sourceUrl: String): Inspection {
        val trimmed = raw.trim()
        val start = trimmed.indexOf('{')
        val end = trimmed.lastIndexOf('}')
        if (start < 0 || end <= start) throw IOException(context.getString(R.string.media_error_invalid_info))
        val json = org.json.JSONObject(trimmed.substring(start, end + 1))
        val type = json.optString("_type")
        val entries = if (type == "playlist" || json.has("entries")) {
            val array = json.optJSONArray("entries")
            buildList {
                if (array != null) for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val itemUrl = item.optString("webpage_url").ifBlank { item.optString("url") }
                    add(
                        PlaylistEntry(
                            index = item.optInt("playlist_index", index + 1),
                            title = item.optString("title").ifBlank {
                                context.getString(R.string.media_playlist_item_fallback, index + 1)
                            },
                            url = itemUrl,
                            duration = item.optLong("duration"),
                            thumbnail = item.optString("thumbnail"),
                        )
                    )
                }
            }
        } else emptyList()
        val formats = json.optJSONArray("formats")?.let { array ->
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val id = item.optString("format_id")
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
                            bitrate = item.optDouble("tbr").toInt().coerceAtLeast(item.optInt("abr")),
                            fileSize = item.optLong("filesize").takeIf { it > 0 } ?: item.optLong("filesize_approx"),
                            note = item.optString("format_note"),
                        )
                    )
                }
            }
        }.orEmpty()
        return Inspection(
            title = json.optString("title").ifBlank { sourceUrl },
            uploader = json.optString("uploader").ifBlank { json.optString("channel") },
            thumbnail = json.optString("thumbnail"),
            duration = json.optLong("duration"),
            formats = formats
                .distinctBy { it.id }
                .sortedWith(compareByDescending<FormatInfo> { it.hasVideo }.thenByDescending { it.height }.thenByDescending { it.bitrate }),
            playlist = entries,
            webpageUrl = json.optString("webpage_url").ifBlank { sourceUrl },
        )
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
        private val INIT_LOCK = Any()

        @Volatile
        private var initialized = false

        private val AUDIO_EXTENSIONS = setOf("aac", "flac", "m4a", "mka", "mp3", "ogg", "opus", "wav")
        private const val DEFAULT_VIDEO_FORMAT = "bestvideo[ext=mp4]+bestaudio[ext=m4a]/best[ext=mp4]/best"
    }
}
