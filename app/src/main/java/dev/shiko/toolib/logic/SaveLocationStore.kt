package dev.shiko.toolib.logic

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract

/** 记住每个工具上次用系统选择器挑的保存文件夹（SAF tree URI）。 */
class SaveLocationStore(context: Context) {
    private val preferences = context.getSharedPreferences("save_locations", Context.MODE_PRIVATE)

    fun load(toolId: String): String? = preferences.getString(toolId, null)

    fun save(toolId: String, treeUri: String?) {
        preferences.edit().apply {
            if (treeUri == null) remove(toolId) else putString(toolId, treeUri)
        }.apply()
    }

    companion object {
        const val SymmetryTool = "symmetry"
    }
}

/** SAF 路径在界面上的显示文案。 */
object SafPaths {

    /** tree URI 显示成 "Download/Toolib" 这种形式。 */
    fun treeDisplayPath(treeUri: Uri): String = runCatching {
        val id = DocumentsContract.getTreeDocumentId(treeUri)
        id.substringAfter(':').ifBlank { id }
    }.getOrDefault(treeUri.toString())
}
