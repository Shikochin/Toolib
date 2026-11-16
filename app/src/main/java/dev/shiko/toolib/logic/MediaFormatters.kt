package dev.shiko.toolib.logic

/** Formats a duration in seconds as m:ss. */
fun formatDuration(seconds: Long): String {
    val minutes = seconds / 60
    val remaining = seconds % 60
    return "%d:%02d".format(minutes, remaining)
}

/** Formats a duration in seconds as hh:mm:ss. */
fun formatTimestamp(seconds: Long): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val remaining = seconds % 60
    return "%02d:%02d:%02d".format(hours, minutes, remaining)
}

/** 把字节/秒格式化成 KB/s、MB/s。 */
fun formatSpeed(bytesPerSecond: Long): String {
    val kilobytes = bytesPerSecond / 1024.0
    return when {
        kilobytes >= 1024 -> String.format(java.util.Locale.US, "%.1f MB/s", kilobytes / 1024)
        kilobytes >= 1 -> String.format(java.util.Locale.US, "%.0f KB/s", kilobytes)
        else -> "$bytesPerSecond B/s"
    }
}
