package dev.shiko.toolib.logic

import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import dev.shiko.toolib.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku

/** The three capability tiers understood by tools in Toolib. */
enum class PermissionLevel(
    val titleRes: Int,
    val explanationRes: Int,
) {
    NONE(R.string.permission_none_title, R.string.permission_none_explanation),
    SHELL(R.string.permission_shell_title, R.string.permission_shell_explanation),
    ROOT(R.string.permission_root_title, R.string.permission_root_explanation),
}

data class PermissionSnapshot(
    val shellGranted: Boolean = false,
    val rootGranted: Boolean = false,
)

fun PermissionLevel.isAvailable(snapshot: PermissionSnapshot): Boolean = when (this) {
    PermissionLevel.NONE -> true
    PermissionLevel.SHELL -> snapshot.shellGranted
    PermissionLevel.ROOT -> snapshot.rootGranted
}

/** Lightweight capability probe. Root probing is kept off the main thread by the caller. */
object PermissionProbe {
    fun snapshot(): PermissionSnapshot = PermissionSnapshot(
        shellGranted = isShizukuGranted(),
        rootGranted = isRootGranted(),
    )

    private fun isShizukuGranted(): Boolean = try {
        Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (_: Throwable) {
        false
    }

    private fun isRootGranted(): Boolean {
        try {
            if (isShizukuGranted() && Shizuku.getUid() == 0) return true
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "id"))
            val completed = process.waitFor()
            return completed == 0 && process.inputStream.bufferedReader().use { it.readText() }.contains("uid=0")
        } catch (_: Throwable) {
            return false
        }
    }
}

object ShizukuPermissionRequest {
    private const val RequestCode = 1101

    fun request(onResult: (Boolean) -> Unit) {
        val finish: (Boolean) -> Unit = { granted ->
            Handler(Looper.getMainLooper()).post { onResult(granted) }
        }
        try {
            if (!Shizuku.pingBinder()) {
                finish(false)
                return
            }
            if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                finish(true)
                return
            }
            lateinit var listener: Shizuku.OnRequestPermissionResultListener
            listener = Shizuku.OnRequestPermissionResultListener { _, result ->
                runCatching { Shizuku.removeRequestPermissionResultListener(listener) }
                finish(result == PackageManager.PERMISSION_GRANTED)
            }
            Shizuku.addRequestPermissionResultListener(listener)
            Shizuku.requestPermission(RequestCode)
        } catch (_: Throwable) {
            finish(false)
        }
    }
}

suspend fun pollPermissionSnapshot(onSnapshot: (PermissionSnapshot) -> Unit) {
    while (currentCoroutineContext().isActive) {
        val snapshot = withContext(Dispatchers.IO) { PermissionProbe.snapshot() }
        onSnapshot(snapshot)
        delay(2_500)
    }
}
