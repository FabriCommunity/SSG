package community.fabricmc.ssg.navigation

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
public sealed class Node {
    @Transient public open val type: String = "unknown"
}

@Serializable
@SerialName("node")
public class NavigationNode(
    public val path: String,
    public val title: String,
    public val icon: String,

    public val children: List<Node> = listOf(),
    public val description: String? = null,
) : Node()  {
    @Transient public override val type: String = "node"

    override fun toString(): String =
        "NavigationNode(path = \"$path\", title = \"$title\", icon = \"$icon\", description = \"$description\", " +
                "children = [${children.joinToString(", ")}])"
}

@Serializable
@SerialName("spacer")
public class Spacer : Node()  {
    @Transient public override val type: String = "spacer",
    public val title: String,

    override fun toString(): String =
        "Spacer()"
}
