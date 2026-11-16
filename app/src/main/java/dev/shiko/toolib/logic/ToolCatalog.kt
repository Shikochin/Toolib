package dev.shiko.toolib.logic

import dev.shiko.toolib.R

/** One entry of the home grid. Titles and descriptions are resource ids so they follow the UI language. */
data class ToolDefinition(
    val id: String,
    val titleRes: Int,
    val descriptionRes: Int,
    val icon: String,
    val permission: PermissionLevel,
)

/** Registry of every tool the app exposes on the home page. */
object ToolCatalog {
    const val MEDIA_DOWNLOAD_ID = "media-download"

    val tools: List<ToolDefinition> = listOf(
        ToolDefinition(
            id = MEDIA_DOWNLOAD_ID,
            titleRes = R.string.tool_media_download_title,
            descriptionRes = R.string.tool_media_download_description,
            icon = "download",
            permission = PermissionLevel.NONE,
        ),
    )

    fun find(id: String): ToolDefinition? = tools.firstOrNull { it.id == id }

    /** Stable key used for shared element navigation from a tool card. */
    fun source(tool: ToolDefinition): String = "tool.card.${tool.id}"
}
