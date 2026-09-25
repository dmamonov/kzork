package kzork.model

data class GameMetadata(
    val id: EntityId,
    val stateFile: PageReference,
    val world: PageReference,
    val player: PageReference,
    val media: PresentationMedia = PresentationMedia(),
    override val extensions: Map<String, MetadataValue> = emptyMap(),
) : PageMetadata

data class Coordinates(val x: Int, val y: Int)

data class WorldMetadata(
    val cells: List<CellIndexEntry>,
    val id: EntityId? = null,
    val width: Int? = null,
    val height: Int? = null,
    /** Null means the world has not yet specified its ordinary movement rule. */
    val movement: String? = null,
    val exceptionalConnections: List<ExceptionalConnection> = emptyList(),
    override val extensions: Map<String, MetadataValue> = emptyMap(),
) : PageMetadata

data class CellIndexEntry(
    val id: EntityId,
    val coordinates: Coordinates,
    val page: PageReference,
)

/** Draft connection structure; conditions remain authored rules for the game master. */
data class ExceptionalConnection(
    val id: EntityId,
    val kind: ConnectionKind,
    val source: PageReference,
    val destination: PageReference,
    val bidirectional: Boolean,
    val description: String,
    val conditions: String = "",
)

enum class ConnectionKind(val metadataValue: String) {
    TUNNEL("tunnel"), WARP("warp"),
}

data class CellMetadata(
    val id: EntityId,
    val coordinates: Coordinates,
    val world: PageReference,
    /** Authoritative direct membership; narrative references do not establish placement. */
    val objects: List<PageReference> = emptyList(),
    val actors: List<PageReference> = emptyList(),
    val media: PresentationMedia = PresentationMedia(),
    override val extensions: Map<String, MetadataValue> = emptyMap(),
) : PageMetadata

data class ObjectMetadata(
    val id: EntityId,
    /** Derived immediate holder: a cell or an actor. Never an independent move instruction. */
    val location: PageReference,
    override val extensions: Map<String, MetadataValue> = emptyMap(),
) : PageMetadata

/** The player uses the same model as other actors. */
data class ActorMetadata(
    val id: EntityId,
    val controlledBy: ActorController,
    /** Derived from the cell's actors list. */
    val location: PageReference,
    /** Authoritative inventory membership under the draft inventory rules. */
    val carriedObjects: List<PageReference> = emptyList(),
    override val extensions: Map<String, MetadataValue> = emptyMap(),
) : PageMetadata

enum class ActorController(val metadataValue: String) {
    PLAYER("player"), ACTOR_AGENT("actor-agent"),
}
