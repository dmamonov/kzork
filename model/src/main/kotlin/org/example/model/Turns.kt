package org.example.model

data class GameStateMetadata(
    val game: PageReference,
    val player: PageReference,
    /** Derived from the player's authoritative cell membership. */
    val currentCell: PageReference,
    val loopPhase: LoopPhase = LoopPhase.AWAITING_COMMAND,
    val lastAppliedStep: PageReference? = null,
    val activeStep: PageReference? = null,
    val nextStepNumber: Int = 1,
    val pendingWork: List<PendingWork> = emptyList(),
    override val extensions: Map<String, MetadataValue> = emptyMap(),
) : PageMetadata

enum class LoopPhase(val metadataValue: String) {
    AWAITING_COMMAND("awaiting-command"), RESOLVING("resolving"), APPLYING("applying"),
}

data class PendingWork(val step: PageReference, val description: String)

data class StepMetadata(
    val stepNumber: Int,
    val gameState: PageReference,
    val status: StepStatus = StepStatus.TEMPLATE,
    val previousStep: PageReference? = null,
    val media: List<MediaAsset> = emptyList(),
    override val extensions: Map<String, MetadataValue> = emptyMap(),
) : PageMetadata

enum class StepStatus(val metadataValue: String) {
    TEMPLATE("template"), RESOLVING("resolving"), RESOLVED("resolved"),
    APPLYING("applying"), APPLIED("applied"), FAILED("failed"),
}

/** Semantic turn content, represented by the step page's Markdown sections. */
data class TurnRecord(
    val playerCommand: String,
    val gameMasterResolution: String? = null,
    val actorActions: List<ActorAction> = emptyList(),
    val finalAccount: String? = null,
    val updateInstructions: List<UpdateInstruction> = emptyList(),
    val formalUpdate: FormalUpdateReport? = null,
    val application: ApplicationRecord = ApplicationRecord(),
    val presentation: String? = null,
    val narrationText: String? = null,
)

/** A proposal with no resolution is not an established outcome. */
data class ActorAction(
    val actor: PageReference,
    val invitation: String,
    val proposedAction: String? = null,
    val gameMasterResolution: String? = null,
)

data class UpdateInstruction(
    val target: PageReference,
    val establishedOutcome: String,
    val reason: String,
)

data class ApplicationRecord(
    val appliedChanges: List<AppliedChange> = emptyList(),
    val pendingInstructions: List<UpdateInstruction> = emptyList(),
    val failures: List<String> = emptyList(),
)

data class AppliedChange(val target: PageReference, val description: String)

/** Data for the formal updater's report; this module does not execute the rules. */
data class FormalUpdateReport(
    val validation: ValidationStatus,
    val derivedChanges: List<DerivedFieldChange> = emptyList(),
    val conflicts: List<MembershipConflict> = emptyList(),
)

enum class ValidationStatus { NOT_RUN, PASSED, FAILED }

data class DerivedFieldChange(
    val target: PageReference,
    /** Header field name, e.g. location or current_cell. */
    val field: String,
    val previousValue: MetadataValue,
    val value: MetadataValue,
    val rule: String,
)

data class MembershipConflict(
    val entity: PageReference,
    val holders: List<PageReference>,
    val description: String,
)
