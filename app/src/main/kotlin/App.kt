package kzork.app

import io.ktor.server.engine.*
import io.ktor.server.cio.*
import java.awt.Desktop
import java.net.URI
import java.nio.file.Path
import java.util.UUID
import kotlin.io.path.*

fun main(args: Array<String>) {
    fun option(name: String): String? = args.indexOf(name).takeIf { it >= 0 }?.let {
        require(it + 1 < args.size) { "Missing value for $name" }; args[it + 1]
    }
    if ("--help" in args) {
        println("Kzork: --demo | --ai, --game PATH, --resume SESSION, --port N, --no-browser, --max-requests N")
        return
    }
    val env = loadEnvironment(Path.of(".env"))
    val key = env["OPENAI_API_KEY"].orEmpty()
    val demo = "--demo" in args || ("--ai" !in args && key.isBlank())
    require(demo || key.isNotBlank()) { "AI mode requires OPENAI_API_KEY in the environment or .env" }
    val model = env["OPENAI_MODEL"] ?: "gpt-4.1-mini"
    val client = if (demo) null else ResponsesClient(key, model, option("--max-requests")?.toInt() ?: 300)
    val factory: (GameFiles) -> TurnEngine = { files -> if (demo) DemoTurnEngine(files) else AiTurnEngine(files, client!!) }
    val session = option("--resume")?.let { GameSession.resume(Path.of(it), factory) }
        ?: GameSession.create(Path.of(option("--game") ?: "games/ice_dungeon").toAbsolutePath(), Path.of(".sessions"), factory)
    val port = option("--port")?.toInt() ?: 8080
    val server = embeddedServer(CIO, host = "127.0.0.1", port = port) { gameApplication(session, UUID.randomUUID().toString()) }
    Runtime.getRuntime().addShutdownHook(Thread { session.close(); client?.close(); server.stop(500, 2000) })
    server.start(wait = false)
    val url = "http://127.0.0.1:$port"
    println("Kzork is ready: $url (${if (demo) "offline demo" else "AI mode: $model"})")
    println("Saved adventure: ${session.directory}")
    if ("--no-browser" !in args) openBrowser(url)
    Thread.currentThread().join()
}

internal fun loadEnvironment(file: Path): Map<String, String> {
    val values = if (!file.exists()) emptyMap() else file.readLines().mapNotNull { line ->
        val clean = line.trim().removePrefix("export ")
        if (clean.startsWith('#') || '=' !in clean) null else {
            val (key, value) = clean.split('=', limit = 2)
            key.trim() to value.trim().removeSurrounding("\"").removeSurrounding("'")
        }
    }.toMap()
    return values + System.getenv()
}
private fun openBrowser(url: String) {
    try {
        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) Desktop.getDesktop().browse(URI(url))
        else {
            val command = when {
                System.getProperty("os.name").startsWith("Mac") -> listOf("open", url)
                System.getProperty("os.name").startsWith("Windows") -> listOf("rundll32", "url.dll,FileProtocolHandler", url)
                else -> listOf("xdg-open", url)
            }
            ProcessBuilder(command).start()
        }
    } catch (_: Exception) { println("Open $url in your browser to play.") }
}
