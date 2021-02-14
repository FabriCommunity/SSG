package community.fabricmc.ssg.navigation

import kotlinx.serialization.Serializable

@Serializable
public data class Node(
    val path: String,
    val title: String,
    val icon: String,
    val spacer: Boolean = false,
    
    val children: List<Node> = listOf(),
    val description: String? = null
) {
    override fun toString(): String =
        "Node(path = \"$path\", title = \"$title\", icon = \"$icon\", children = [${children.joinToString(", ")}], spacer = \"$spacer\")"
}
