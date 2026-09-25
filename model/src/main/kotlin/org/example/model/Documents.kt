package org.example.model

/** A path relative to the Markdown page containing this reference, not an entity ID. */
@JvmInline
value class PageReference(val path: String)

@JvmInline
value class EntityId(val value: String)

/** YAML values for game-specific fields, such as frozen, portable, or outcome. */
sealed interface MetadataValue {
    data class Text(val value: String) : MetadataValue
    data class BooleanValue(val value: Boolean) : MetadataValue
    data class IntegerValue(val value: Long) : MetadataValue
    data class DecimalValue(val value: Double) : MetadataValue
    data class ListValue(val values: List<MetadataValue>) : MetadataValue
    data class MapValue(val values: Map<String, MetadataValue>) : MetadataValue
    data object NullValue : MetadataValue
}

/** Extensions represent additional top-level YAML fields; they must not shadow typed fields. */
sealed interface PageMetadata {
    val extensions: Map<String, MetadataValue>
}

/**
 * Header metadata and narrative content stay separate. Sections retain document order,
 * arbitrary headings, and Markdown text. The changelog is rendered last.
 */
data class MarkdownPage<M : PageMetadata>(
    val metadata: M,
    val title: String,
    val preamble: String = "",
    val sections: List<MarkdownSection> = emptyList(),
    val changelog: List<ChangelogEntry> = emptyList(),
)

data class MarkdownSection(val heading: String, val markdown: String)

/** Null step denotes initialization or an editorial change outside a played turn. */
data class ChangelogEntry(
    val description: String,
    val step: PageReference? = null,
    val reason: String? = null,
)

data class MediaAsset(
    val path: String,
    val status: MediaStatus = MediaStatus.NOT_GENERATED,
)

enum class MediaStatus(val metadataValue: String) {
    NOT_GENERATED("not-generated"), GENERATING("generating"), READY("ready"), FAILED("failed"),
}

/** The game template uses music; cell pages and the ice dungeon use ambient_music. */
data class PresentationMedia(
    val coverImage: MediaAsset? = null,
    val music: MediaAsset? = null,
    val ambientMusic: MediaAsset? = null,
    val introSpeech: MediaAsset? = null,
)

typealias Game = MarkdownPage<GameMetadata>
typealias World = MarkdownPage<WorldMetadata>
typealias Cell = MarkdownPage<CellMetadata>
typealias GameObject = MarkdownPage<ObjectMetadata>
typealias Actor = MarkdownPage<ActorMetadata>
typealias GameState = MarkdownPage<GameStateMetadata>
typealias Step = MarkdownPage<StepMetadata>
