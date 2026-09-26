package kzork.app

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.nio.file.Files
import kotlinx.serialization.Serializable

@Serializable data class CommandRequest(val command: String)
@Serializable data class AutoRequest(val turns: Int = 20)
@Serializable data class Reply(val ok: Boolean = true, val error: String? = null)

fun Application.gameApplication(session: GameSession, token: String) {
    install(ContentNegotiation) { json(json) }
    intercept(ApplicationCallPipeline.Call) {
        val host = call.request.headers[HttpHeaders.Host]?.substringBefore(':')
        if (host !in setOf("127.0.0.1", "localhost")) {
            call.respond(HttpStatusCode.Forbidden); finish()
        }
    }
    routing {
        get("/") {
            val html = object {}.javaClass.getResource("/web/index.html")!!.readText().replace("__TOKEN__", token)
            call.response.header("Cache-Control", "no-store")
            call.response.header("Content-Security-Policy", "default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self'; media-src 'self' blob:; connect-src 'self'; object-src 'none'; frame-ancestors 'none'; base-uri 'none'")
            call.respondText(html, ContentType.Text.Html)
        }
        get("/static/{file}") {
            val file = call.parameters["file"]
            if (file !in setOf("app.js", "style.css")) { call.respond(HttpStatusCode.NotFound); return@get }
            val resource = object {}.javaClass.getResource("/web/$file")!!
            call.respondText(resource.readText(), if (file!!.endsWith("js")) ContentType.Application.JavaScript else ContentType.Text.CSS)
        }
        get("/art/{id}") {
            val id = call.parameters["id"].orEmpty().removeSuffix(".svg")
            if (!Regex("game|cell-[0-9]+-[0-9]+").matches(id)) { call.respond(HttpStatusCode.NotFound); return@get }
            call.respondText(iceArt(id), ContentType.Image.SVG)
        }
        get("/media/{path...}") {
            try {
                val path = call.parameters.getAll("path")!!.joinToString("/")
                require(path.startsWith("media/"))
                val target = session.files.path(path)
                require(Files.isRegularFile(target) && target.fileName.toString().substringAfterLast('.').lowercase() in setOf("png", "jpg", "jpeg", "webp", "svg", "ogg", "mp3", "wav", "m4a"))
                call.respondFile(target.toFile())
            } catch (_: Exception) { call.respond(HttpStatusCode.NotFound) }
        }
        get("/api/state") {
            if (call.request.headers["X-Game-Token"] != token) { call.respond(HttpStatusCode.Forbidden); return@get }
            call.response.header("Cache-Control", "no-store")
            call.respond(session.view(call.request.queryParameters["after"]?.toIntOrNull() ?: 0))
        }
        post("/api/{action}") {
            if (call.request.headers["X-Game-Token"] != token) { call.respond(HttpStatusCode.Forbidden); return@post }
            if ((call.request.headers[HttpHeaders.ContentLength]?.toLongOrNull() ?: 0) > 8192) { call.respond(HttpStatusCode.PayloadTooLarge); return@post }
            try {
                when (call.parameters["action"]) {
                    "begin" -> session.begin()
                    "command" -> session.submit(call.receive<CommandRequest>().command)
                    "auto" -> session.autoplay(call.receive<AutoRequest>().turns)
                    "pause" -> session.pause()
                    "retry" -> session.retry()
                    else -> { call.respond(HttpStatusCode.NotFound); return@post }
                }
                call.respond(Reply())
            } catch (e: Exception) { call.respond(HttpStatusCode.Conflict, Reply(false, e.message?.take(350))) }
        }
    }
}

/** Procedural fallback art is local, deterministic, and does not require an image API. */
fun iceArt(id: String): String {
    val n = id.hashCode().toUInt().toLong()
    val warm = id in setOf("cell-2-0", "cell-2-1", "game")
    val glow = if (warm) "#ffc879" else "#b6f5f1"
    val pillars = (0..10).joinToString("") { i ->
        val x = i * 110; val h = 160 + ((n + i * 47) % 190)
        "<path d='M$x 0 L${x + 98} 0 L${x + 64} $h L${x + 24} ${h - 45}Z' fill='#75afbd' opacity='.${2 + i % 3}'/>"
    }
    return """<svg xmlns="http://www.w3.org/2000/svg" width="1200" height="640" viewBox="0 0 1200 640">
<defs><linearGradient id="bg" x2="0" y2="1"><stop stop-color="#071822"/><stop offset="1" stop-color="#285363"/></linearGradient>
<radialGradient id="light"><stop stop-color="$glow" stop-opacity=".75"/><stop offset="1" stop-color="$glow" stop-opacity="0"/></radialGradient>
<linearGradient id="floor" x2="0" y2="1"><stop stop-color="#183946"/><stop offset="1" stop-color="#091820"/></linearGradient></defs>
<rect width="1200" height="640" fill="url(#bg)"/>$pillars
<path d="M0 0H280L400 150L380 390L290 500H0Z" fill="#112c39"/><path d="M1200 0H955L840 210L870 410L950 510H1200Z" fill="#102c39"/>
<path d="M300 490L450 330L480 160L600 88L760 180L790 480" fill="none" stroke="#73afbe" stroke-width="16" opacity=".5"/>
<path d="M408 462L448 260L600 147L744 260L788 462Z" fill="#081c28"/><ellipse cx="600" cy="365" rx="185" ry="190" fill="url(#light)"/>
<path d="M0 470Q300 415 540 480T1200 450V640H0Z" fill="url(#floor)"/>
<path d="M565 404H634L656 464H542Z" fill="#61828b"/><path d="M600 344L619 366L600 394L580 366Z" fill="$glow"/>
<path d="M591 484L567 640M614 483L675 640" stroke="$glow" stroke-width="2" opacity=".18"/>
<g fill="#dcffff" opacity=".35"><circle cx="170" cy="310" r="2"/><circle cx="850" cy="150" r="2"/><circle cx="985" cy="410" r="3"/><circle cx="460" cy="190" r="2"/></g>
</svg>"""
}
