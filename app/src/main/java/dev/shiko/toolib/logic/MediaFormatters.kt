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
