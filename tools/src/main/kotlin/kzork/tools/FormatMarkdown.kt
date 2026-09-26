package kzork.tools

import kzork.model.MarkdownFormatting
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.*

/** Repository maintenance CLI; not an agent tool and never invoked by the game loop. */
fun main(args: Array<String>) {
    val check = "--check" in args
    val root = Path.of(args.firstOrNull { it != "--check" } ?: ".").toRealPath()
    val excluded = setOf(".git", ".gradle", ".idea", "build", "node_modules")
    val paths = Files.walk(root).use { stream -> stream.filter { path ->
        path.extension == "md" && !Files.isSymbolicLink(path) &&
            root.relativize(path).none { it.toString() in excluded }
    }.sorted().toList() }
    // Format every candidate before writing any, so unsupported content fails the whole preflight.
    val updates = paths.mapNotNull { path ->
        val before = path.readText()
        val formatted = try { MarkdownFormatting.format(before) } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException("${root.relativize(path)}: ${e.message}", e)
        }
        if (before == formatted) null else path to formatted
    }
    if (check) {
        check(updates.isEmpty()) { "Unformatted Markdown: ${updates.joinToString { root.relativize(it.first).toString() }}" }
        println("Checked ${paths.size} Markdown files; all conform.")
    } else {
        updates.forEach { (path, content) -> path.writeText(content) }
        println("Formatted ${updates.size} of ${paths.size} Markdown files.")
    }
}
