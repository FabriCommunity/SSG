package community.fabricmc.ssg.markdown

import com.charleskorn.kaml.Yaml
import com.vladsch.flexmark.ext.abbreviation.AbbreviationExtension
import com.vladsch.flexmark.ext.anchorlink.AnchorLinkExtension
import com.vladsch.flexmark.ext.attributes.AttributesExtension
import com.vladsch.flexmark.ext.autolink.AutolinkExtension
import com.vladsch.flexmark.ext.emoji.EmojiExtension
import com.vladsch.flexmark.ext.emoji.EmojiImageType
import com.vladsch.flexmark.ext.gfm.strikethrough.StrikethroughExtension
import com.vladsch.flexmark.ext.media.tags.MediaTagsExtension
import com.vladsch.flexmark.ext.tables.TablesExtension
import com.vladsch.flexmark.ext.toc.TocExtension
import com.vladsch.flexmark.html.HtmlRenderer
import com.vladsch.flexmark.parser.Parser
import com.vladsch.flexmark.util.data.DataSet
import com.vladsch.flexmark.util.data.MutableDataSet
import com.vladsch.flexmark.util.misc.Extension
import community.fabricmc.ssg.SSG
import community.fabricmc.ssg.navigation.Root
import java.io.StringWriter
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.readText

@OptIn(ExperimentalPathApi::class)
public class MarkdownRenderer(private val ssg: SSG) {
    private var settings: DataSet = MutableDataSet()
        .set(HtmlRenderer.GENERATE_HEADER_ID, true)
        .set(Parser.HTML_BLOCK_DEEP_PARSER, true)
        .set(Parser.INDENTED_CODE_BLOCK_PARSER, false)

        .set(EmojiExtension.ATTR_IMAGE_CLASS, "emoji")
        .set(EmojiExtension.ATTR_IMAGE_SIZE, "1rem")
        .set(EmojiExtension.ROOT_IMAGE_PATH, "/static/images/emoji/")
        .set(EmojiExtension.USE_IMAGE_TYPE, EmojiImageType.UNICODE_FALLBACK_TO_IMAGE)

        .set(TablesExtension.CLASS_NAME, "table is-striped is-fullwidth")

        .toImmutable()

    private val yaml: Yaml get() = ssg.yaml

    private var extensions: MutableList<Extension> = mutableListOf(
        AbbreviationExtension.create(),
        AnchorLinkExtension.create(),
        AttributesExtension.create(),
        AutolinkExtension.create(),
        EmojiExtension.create(),
        StrikethroughExtension.create(),
        MediaTagsExtension.create(),
        TablesExtension.create(),
        TocExtension.create()
    )

    public val parser: Parser = Parser.builder(settings).extensions(extensions).build()
    public val renderer: HtmlRenderer = HtmlRenderer.builder(settings).extensions(extensions).build()

    public fun render(path: Path, navigation: Root, modifiedTime: String?): String {
        val text = path.readText(Charsets.UTF_8)

        var lines = text.lines()
        var frontMatter: FrontMatter? = null

        if (lines.first() == "---") {
            var inFrontMatter = true
            lines = lines.drop(1)

            val yamlList: MutableList<String> = mutableListOf()

            lines.toList().forEach {
                if (inFrontMatter) {
                    lines = lines.drop(1)

                    if (it == "---") {
                        inFrontMatter = false
                    } else {
                        yamlList.add(it)
                    }
                }
            }

            @Suppress("TooGenericExceptionCaught")
            try {
                frontMatter = yaml.decodeFromString(FrontMatter.serializer(), yamlList.joinToString("\n"))
            } catch (e: Exception) {
                error("Failed to parse front matter for $path: ${e.message ?: e}")
            }
        }

        frontMatter ?: error("Front matter is required for all pages.")

        val markdownTemplate = ssg.getStringTemplate(lines.joinToString("\n"))
        val markdownWriter = StringWriter()

        val markdownContext: MutableMap<String, Any?> = mutableMapOf(
            "lastCommit" to modifiedTime,
            "meta" to frontMatter,
            "navigation" to navigation
        )

        markdownTemplate.evaluate(markdownWriter, markdownContext)

        val markdown = markdownWriter.toString()
        val rendered = renderer.render(parser.parse(markdown))

        val htmlTemplate = ssg.getTemplate(frontMatter.template ?: ssg.settings.defaultTemplate)
        val htmlWriter = StringWriter()

        val htmlContext: MutableMap<String, Any?> = mutableMapOf(
            "body" to rendered,
            "lastCommit" to modifiedTime,
            "meta" to frontMatter,
            "navigation" to navigation
        )

        htmlTemplate.evaluate(htmlWriter, htmlContext)

        return htmlWriter.toString()
    }
}
