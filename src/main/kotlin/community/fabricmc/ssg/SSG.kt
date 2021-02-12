package community.fabricmc.ssg

import com.charleskorn.kaml.Yaml
import com.mitchellbosecke.pebble.PebbleEngine
import com.mitchellbosecke.pebble.loader.StringLoader
import com.mitchellbosecke.pebble.template.PebbleTemplate
import community.fabricmc.ssg.builders.SSGBuilder
import community.fabricmc.ssg.markdown.MarkdownRenderer
import community.fabricmc.ssg.navigation.Root
import java.io.StringWriter
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.*
import kotlin.streams.toList

private val ALLOWED_EXTENSIONS = arrayOf("html", "md", "peb")

@OptIn(ExperimentalPathApi::class)
public class SSG private constructor(public val settings: SSGBuilder) {
    private val pebble = PebbleEngine.Builder()
        .build()

    private val stringPebble = PebbleEngine.Builder()
        .loader(StringLoader())
        .build()

    public val yaml: Yaml = Yaml()
    private val markdown = MarkdownRenderer(this)

    private val templatePath = Path(settings.templatePath).relativeTo(Path("."))

    public fun getTemplate(name: String): PebbleTemplate {
        val path = templatePath / "$name.html.peb"

        return pebble.getTemplate(path.toString())
    }

    public fun getStringTemplate(template: String): PebbleTemplate =
        stringPebble.getTemplate(template)

    public fun getSources(section: String? = null): List<Path> {
        var sourcesRoot = Path(settings.sourcesPath)

        val walk = if (section != null) {
            sourcesRoot = sourcesRoot / section

            Files.walk(sourcesRoot)
        } else {
            Files.walk(sourcesRoot, 1)
        }

        return walk.filter { it.extension in ALLOWED_EXTENSIONS }.toList()
    }

    public fun getNavigation(section: String? = null): Root {
        var sourcesRoot = Path(settings.sourcesPath)

        if (section != null) {
            sourcesRoot = sourcesRoot / section
        }

        val navigationFile = sourcesRoot / "navigation.yml"

        @Suppress("TooGenericExceptionCaught")
        if (navigationFile.exists()) {
            try {
                return yaml.decodeFromString(Root.serializer(), navigationFile.readText(Charsets.UTF_8))
            } catch (e: Exception) {
                val sectionText = if (section != null) {
                    "/$section/"
                } else {
                    "/"
                }
                error("Failed to parse ${sectionText}navigation.yml: ${e.message ?: e}")
            }
        }

        return Root(listOf())
    }

    @Suppress("StringLiteralDuplication")
    public fun render(section: String?) {
        var outputRoot = Path(settings.outputPath)
        var sourcesRoot = Path(settings.sourcesPath)

        val sources = getSources(section)

        if (section != null) {
            outputRoot = outputRoot / section
            sourcesRoot = sourcesRoot / section
        }

        if (!outputRoot.exists()) {
            outputRoot.createDirectory()
        }

        sources.forEach {
            var relativePath = it.relativeTo(sourcesRoot).toString()

            if (relativePath.endsWith(".peb")) {
                relativePath = relativePath.substringBeforeLast(".").substringBeforeLast(".")
            } else {
                relativePath = relativePath.substringBeforeLast(".")
            }

            var outputPath = if (!relativePath.endsWith("index")) {
                (outputRoot / relativePath).createDirectory()

                outputRoot / "$relativePath/index.html"
            } else {
                outputRoot / "$relativePath.html"
            }

            val path = if (relativePath.endsWith("index")) {
                relativePath.substringBeforeLast("index").trim('/')
            } else {
                relativePath.trim('/')
            }

            val slug = "/" + ((section ?: "") + "/$path").trim('/')
            val navigation = getNavigation(section).copy(currentPath = slug)

            val rendered = if (it.toString().endsWith(".html.peb")) {
                outputPath = if (!relativePath.endsWith("index")) {
                    outputRoot / "$relativePath/index.html"
                } else {
                    outputRoot / "$relativePath.html"
                }

                val template = getStringTemplate(it.readText(Charsets.UTF_8))
                val context: MutableMap<String, Any?> = mutableMapOf("body" to null)

                if (navigation != null) {
                    context["navigation"] = navigation
                }

                val writer = StringWriter()

                template.evaluate(writer, context)

                writer.toString()
            } else if (it.extension == "md" || it.toString().endsWith(".md.peb")) {
                markdown.render(it, navigation)
            } else {
                it.readText(Charsets.UTF_8)
            }

            outputPath.writeText(rendered, Charsets.UTF_8)
        }
    }

    public fun render() {
        val outputRoot = Path(settings.outputPath)

        if (outputRoot.exists()) {
            outputRoot.toFile().deleteRecursively()
        }

        outputRoot.createDirectory()

        render(null)

        settings.sections.forEach { render(it) }
    }

    public companion object {
        public operator fun invoke(builder: SSGBuilder.() -> Unit): SSG {
            val settings = SSGBuilder()

            builder(settings)
            settings.validate()

            return SSG(settings)
        }
    }
}
