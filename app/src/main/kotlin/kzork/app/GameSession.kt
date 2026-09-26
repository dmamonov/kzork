package kzork.app

import kotlinx.coroutines.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import kzork.tools.*
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.*
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.io.path.*

@Serializable data class SessionView(
    val session: String, val mode: String, val title: String, val busy: Boolean,
    val status: String, val automatic: Boolean, val remaining: Int,
    val started: Boolean, val outcome: String, val scene: Scene,
    val events: List<ChatEvent>, val pending: Boolean, val error: String?,
)

class GameSession(val directory: Path, val files: GameFiles, private val engine: TurnEngine,
                  private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
                  private val autoplayDelayMillis: Long = 1800) : AutoCloseable {
    private val busy = AtomicBoolean(false)
    private val events = CopyOnWriteArrayList<ChatEvent>()
    @Volatile private var status = "Your adventure awaits"
    @Volatile private var automatic = false
    @Volatile private var remaining = 0
    @Volatile private var error: String? = null
    private val eventDirectory = directory.resolve("events").createDirectories()
    @Volatile private var currentScene: Scene = files.scene()
    @Volatile private var currentOutcome = files.outcome()
    @Volatile private var pending = files.state().metadata.string("active_step").isNotBlank()
    val title = files.read("game.md").title()
    init {
        eventDirectory.listDirectoryEntries("*.md").sorted().forEach { path ->
            val doc = MarkdownDocument.parse(path.readText())
            events.add(json.decodeFromJsonElement<ChatEvent>(JsonObject(doc.metadata + ("text" to JsonPrimitive(doc.section("Message"))))))
        }
        synchronizeAppliedTurn()
    }
    fun view(after: Int = 0) = SessionView(directory.fileName.toString(), engine.mode, title, busy.get(), status,
        automatic, remaining, events.isNotEmpty(), currentOutcome, currentScene, events.drop(after.coerceAtLeast(0)), pending, error)

    @Synchronized fun begin() {
        if (events.isNotEmpty()) return
        val game = files.read("game.md")
        append(ChatEvent("opening", "opening", game.title(), game.section("Intro speech"),
            files.media("game.md", "cover_image") ?: "/art/game.svg", files.media("game.md", "intro_speech")))
        appendScene(0)
        status = "What will you do?"
    }
    fun submit(command: String) {
        require(command.isNotBlank() && command.length <= 500) { "Enter a command of 1–500 characters" }
        require(!automatic) { "Pause agent play before taking control" }
        start { play(command, false) }
    }
    fun retry() { start {
        val active = files.state().metadata.string("active_step")
        require(active.isNotBlank()) { "No pending update; you can submit another command" }
        engine.resume(::setStatus); synchronizeAppliedTurn()
    } }
    fun autoplay(turns: Int) {
        require(turns in 1..100) { "Choose 1–100 turns" }
        require(!pending) { "Retry the pending update first" }
        require(!busy.get()) { "A turn is already in progress" }
        automatic = true
        try { start {
            remaining = turns
            try {
                while (automatic && remaining > 0 && files.outcome() != "won") {
                    setStatus(if (engine.mode == "demo") "The demo guide is choosing a command" else "The player agent is choosing a command")
                    val command = engine.playerCommand(playerObservation())
                    if (!automatic) break
                    require(command.isNotBlank() && command.length <= 500) { "Player agent returned an invalid command" }
                    play(command, true); remaining = (remaining - 1).coerceAtLeast(0)
                    if (automatic && remaining > 0) delay(autoplayDelayMillis)
                }
            } finally { automatic = false }
        } } catch (e: Exception) { automatic = false; throw e }
    }
    fun pause() { automatic = false; remaining = 0; if (busy.get()) status = "Pausing after this turn" }
    private fun start(action: suspend () -> Unit) {
        begin()
        require(currentOutcome != "won") { "The adventure is complete" }
        require(busy.compareAndSet(false, true)) { "A turn is already in progress" }
        error = null
        scope.launch {
            try { action() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                error = e.message?.take(350) ?: "The turn could not be completed"
                append(ChatEvent("error-${UUID.randomUUID()}", "error", "The story is paused", error!!))
            } finally {
                try {
                    synchronizeAppliedTurn()
                    pending = files.state().metadata.string("active_step").isNotBlank()
                    currentOutcome = files.outcome(); currentScene = files.scene()
                } catch (e: Exception) {
                    error = e.message?.take(350) ?: "Saved state needs inspection before continuing"
                } finally {
                    automatic = false; busy.set(false)
                    status = if (error != null) "Paused — see the message below" else if (currentOutcome == "won") "Adventure complete" else "What will you do?"
                }
            }
        }
    }
    private suspend fun play(command: String, agent: Boolean) {
        require(files.state().metadata.string("active_step").isBlank()) { "A previous turn needs its update retried" }
        val number = files.state().metadata.getValue("next_step_number").jsonPrimitive.int
        append(ChatEvent("command-${UUID.randomUUID()}", if (agent) "agent" else "player", text = command, turn = number))
        engine.turn(command, transcript(), ::setStatus)
        synchronizeAppliedTurn()
    }
    private fun setStatus(message: String) { status = message }
    private fun transcript() = events.filter { it.role in setOf("opening", "narrator", "location", "player", "agent") }
        .takeLast(60).joinToString("\n") { "${it.role}: ${it.text}" }.takeLast(24000)
    /** Deliberately excludes hidden files, state flags, actor instructions and walkthroughs. */
    fun playerObservation() = buildJsonObject {
        put("objective", files.read("game.md").section("Description"))
        put("scene", json.encodeToJsonElement(currentScene))
        put("transcript", transcript())
    }.toString()
    @Synchronized private fun synchronizeAppliedTurn() {
        val last = files.state().metadata.string("last_applied_step")
        if (last.isBlank()) return
        val record = files.read(last)
        val number = record.metadata.getValue("step_number").jsonPrimitive.int
        if (events.none { it.id == "turn-$number" }) {
            val narrative = record.section("Final account")
            val effect = when {
                files.outcome() == "won" -> "success"
                narrative.contains("crack", true) || narrative.contains("melt", true) -> "ice"
                narrative.contains("keeper", true) -> "bell"
                else -> null
            }
            append(ChatEvent("turn-$number", "narrator", text = narrative, turn = number, effect = effect))
        }
        val oldCell = events.lastOrNull { it.scene != null }?.scene?.id
        currentScene = files.scene(); currentOutcome = files.outcome()
        if (currentScene.id != oldCell) appendScene(number)
        pending = false
    }
    private fun appendScene(turn: Int) {
        val scene = files.scene()
        append(ChatEvent("scene-$turn", "location", scene.title, scene.intro, scene.image, scene.voice, scene = scene, turn = turn))
        currentScene = scene
    }
    @Synchronized private fun append(event: ChatEvent) {
        if (events.any { it.id == event.id }) return
        val data = json.encodeToJsonElement(event).jsonObject
        val doc = MarkdownDocument(JsonObject(data - "text"), "# Chat event\n\n## Message\n\n${event.text}",
            "- Created: appended to this adventure's transcript.")
        val destination = eventDirectory.resolve("event-${events.size.toString().padStart(6, '0')}.md")
        val temporary = eventDirectory.resolve(".event.tmp")
        val encoded = doc.render()
        temporary.writeText(encoded)
        Files.move(temporary, destination, ATOMIC_MOVE, REPLACE_EXISTING)
        events.add(event.copy(text = MarkdownDocument.parse(encoded).section("Message")))
    }
    override fun close() { automatic = false; scope.cancel() }

    companion object {
        fun create(source: Path, sessions: Path, factory: (GameFiles) -> TurnEngine): GameSession {
            val directory = sessions.resolve("ice-${System.currentTimeMillis()}-${UUID.randomUUID().toString().take(8)}").toAbsolutePath().normalize()
            val world = directory.resolve("world").createDirectories()
            Files.walk(source).use { paths -> paths.forEach { path ->
                require(!Files.isSymbolicLink(path)) { "Game sources cannot contain symlinks" }
                val target = world.resolve(source.relativize(path))
                if (Files.isDirectory(path)) target.createDirectories() else Files.copy(path, target)
            } }
            val files = GameFiles(world)
            checked(GameLoopTools(world, ToolRole.GAME_MASTER).call("validate_state", buildJsonObject {}))
            val engine = factory(files)
            directory.resolve("session.md").writeText(MarkdownDocument(buildJsonObject { put("mode", engine.mode) },
                "# Saved adventure", "- Created: copied the authored world for independent play.").render())
            return GameSession(directory, files, engine)
        }
        fun resume(directory: Path, factory: (GameFiles) -> TurnEngine): GameSession {
            val normalized = directory.toRealPath()
            val files = GameFiles(normalized.resolve("world"))
            val engine = factory(files)
            require(MarkdownDocument.parse(normalized.resolve("session.md").readText()).metadata.string("mode") == engine.mode) {
                "Resume this adventure with its original mode (demo or ai)"
            }
            return GameSession(normalized, files, engine)
        }
    }
}
