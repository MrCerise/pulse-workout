package com.pulse.engine

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * PULSE interval engine — pure Kotlin, no Android dependencies.
 *
 * Everything in this module is deterministic and unit-testable with a fake monotonic clock.
 * The Android app layer owns persistence, audio, notifications and UI; this module only owns
 * workout structure, expansion (flattening) and the timer state machine.
 */

// ---------------------------------------------------------------------------------------------
// Phases
// ---------------------------------------------------------------------------------------------

/** Semantic phase kinds. Colour is a UI concern; kind is a coaching concern. */
enum class PhaseKind {
    PREPARE,
    WARM_UP,
    WORK,
    REST,
    TRANSITION,
    COOLDOWN,
    CUSTOM;

    val isEffort: Boolean get() = this == WORK || this == WARM_UP
    val isRecovery: Boolean get() = this == REST || this == TRANSITION || this == COOLDOWN

    companion object {
        /** Display/announcement label used when an interval has no explicit name. */
        fun defaultLabel(kind: PhaseKind): String = when (kind) {
            PREPARE -> "Get ready"
            WARM_UP -> "Warm-up"
            WORK -> "Work"
            REST -> "Rest"
            TRANSITION -> "Transition"
            COOLDOWN -> "Cool-down"
            CUSTOM -> "Interval"
        }
    }
}

/** Sound cue baked into an interval. Rendered by the app's audio layer. */
enum class SoundCue { NONE, TICK, BEEP, DOUBLE_BEEP, BELL, WHISTLE, CHIME, BUZZ }

/** Vibration behaviour baked into an interval. */
enum class HapticCue { NONE, LIGHT, DOUBLE, STRONG, PATTERN_321 }

/** Workout family. Drives builder defaults, template filtering and player copy. */
enum class WorkoutType {
    HIIT,
    TABATA,
    CIRCUIT,
    BOXING,
    EMOM,
    E2MOM,
    AMRAP,
    STRENGTH,
    RUN_WALK,
    STRETCHING,
    BREATHING,
    FOCUS,
    CUSTOM_SEQUENCE,
    COMPOUND,
    STOPWATCH,
    CUE_TIMER;

    val displayName: String
        get() = when (this) {
            HIIT -> "HIIT"
            TABATA -> "Tabata"
            CIRCUIT -> "Circuit"
            BOXING -> "Boxing / MMA"
            EMOM -> "EMOM"
            E2MOM -> "E2MOM"
            AMRAP -> "AMRAP"
            STRENGTH -> "Strength"
            RUN_WALK -> "Run / Walk"
            STRETCHING -> "Stretching"
            BREATHING -> "Breathing"
            FOCUS -> "Focus / Pomodoro"
            CUSTOM_SEQUENCE -> "Custom sequence"
            COMPOUND -> "Compound"
            STOPWATCH -> "Stopwatch"
            CUE_TIMER -> "Repeating cue"
        }

    /** Open-ended types never report a known finish time. */
    val isOpenEnded: Boolean get() = this == STOPWATCH
}

// ---------------------------------------------------------------------------------------------
// Structure
// ---------------------------------------------------------------------------------------------

/**
 * A single leaf interval.
 *
 * @param durationMillis timed length in ms; 0 for manual/indefinite intervals
 * @param manualCompletion when true the interval waits for the user instead of expiring
 */
@Serializable
data class IntervalSpec(
    val id: String,
    val name: String = "",
    val kind: PhaseKind = PhaseKind.WORK,
    val durationMillis: Long = 0L,
    val manualCompletion: Boolean = false,
    val notes: String? = null,
    val speechText: String? = null,
    val colorArgb: Long? = null,
    val sound: SoundCue = SoundCue.TICK,
    val haptic: HapticCue = HapticCue.LIGHT,
    val reps: Int? = null,
    val equipment: String? = null,
    /** "Left" / "Right" for alternating intervals. */
    val sideLabel: String? = null,
    /** User-picked exercise image (content:// uri string). */
    val imageUri: String? = null,
    /** When true the interval is skipped by the expander (used for "omit final rest"). */
    val omitted: Boolean = false,
) {
    val isIndefinite: Boolean get() = manualCompletion || durationMillis <= 0L

    fun displayName(): String = name.ifBlank { PhaseKind.defaultLabel(kind) }
}

/**
 * Orderable structure node.
 *
 * Marked `@Serializable` so kotlinx.serialization generates the closed-polymorphic serializer for
 * the hierarchy: without it every persisted plan fails with "serializer for subclass … not found".
 */
@Serializable
sealed interface Node {
    val nodeId: String
}

@Serializable
@SerialName("interval")
data class IntervalNode(
    override val nodeId: String,
    val interval: IntervalSpec,
) : Node

/**
 * A repeated block. Groups can nest; [TimelineExpander] enforces the documented depth limit.
 *
 * @param restAfterGroupMillis rest inserted after each repetition of the whole group (0 = none)
 * @param ladder optional progressive duration change applied per group repetition
 */
@Serializable
@SerialName("group")
data class RepeatGroup(
    override val nodeId: String,
    val name: String = "Block",
    val repeat: Int = 1,
    val children: List<Node> = emptyList(),
    val restAfterGroupMillis: Long = 0L,
    val restAfterGroupName: String = "Rest between blocks",
    val ladder: Ladder? = null,
) : Node

/**
 * Progressive ladder: adjusts the duration of matching intervals on each repetition.
 * Positive [deltaMillis] lengthens (build-up), negative shortens (taper).
 */
@Serializable
data class Ladder(
    val target: TargetKind = TargetKind.WORK,
    val deltaMillis: Long = 0L,
    val minMillis: Long = 5_000L,
    val maxMillis: Long = 3_600_000L,
) {
    enum class TargetKind { WORK, REST, ALL }
}

/** A complete, runnable workout definition. */
@Serializable
data class WorkoutPlan(
    val id: String,
    val name: String,
    val description: String = "",
    val type: WorkoutType = WorkoutType.HIIT,
    val nodes: List<Node> = emptyList(),
    /** When false the trailing rest of the last repetition is dropped. */
    val includeFinalRest: Boolean = true,
    /** Shuffle effort intervals once at session start (order is frozen after expansion). */
    val shuffleEager: Boolean = false,
    val equipment: String? = null,
    val voiceProfileId: String? = null,
    val folderId: String? = null,
    val tags: List<String> = emptyList(),
    val iconKey: String = "bolt",
    val colorArgb: Long? = null,
    val isFavorite: Boolean = false,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    /** Incremented on every save — used by sessions to detect post-hoc edits. */
    val revision: Int = 1,
    /**
     * Repeating-cue timer support: when set, the engine emits a cue every N milliseconds of
     * session time while running (used by "repeating cue timer" workouts).
     */
    val periodicCueMillis: Long? = null,
) {
    fun allIntervals(): List<IntervalSpec> = flattenIntervals(nodes)

    companion object {
        private fun flattenIntervals(nodes: List<Node>): List<IntervalSpec> = nodes.flatMap { node ->
            when (node) {
                is IntervalNode -> listOf(node.interval)
                is RepeatGroup -> flattenIntervals(node.children)
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Limits & validation
// ---------------------------------------------------------------------------------------------

/**
 * Documented safety bounds. Expansion refuses to exceed them instead of hanging the device.
 */
data class EngineLimits(
    val maxExpandedIntervals: Int = 10_000,
    val maxNestingDepth: Int = 8,
    val maxTotalTimedMillis: Long = 24L * 60L * 60L * 1000L, // 24 h
    val maxRepeatCount: Int = 1_000,
)

/** Formats a count/duration with thousands separators for user-facing limit messages. */
fun formatCount(value: Number): String = String.format(java.util.Locale.US, "%,d", value.toLong())

object Validation {
    /** Returns human-readable problems; empty list means the plan may be saved and run. */
    fun validate(plan: WorkoutPlan, limits: EngineLimits = EngineLimits()): List<String> {
        val problems = mutableListOf<String>()
        if (plan.name.isBlank()) problems += "Give the workout a name."
        if (plan.nodes.isEmpty()) problems += "Add at least one interval before running this workout."
        if (plan.nodes.all { isEffectivelyEmpty(it, emptyList()) }) {
            problems += "Every interval is empty or zero-length — this workout would do nothing."
        }
        walk(plan.nodes, 1, limits, problems, path = plan.name)
        val expansion = try {
            TimelineExpander.expand(plan, limits)
        } catch (e: ExpansionException) {
            problems += e.message ?: "Workout could not be expanded."
            return problems
        }
        if (expansion.hasOpenEnded && !plan.type.isOpenEnded) {
            // Indefinite segments are legal but must be declared, so the user is never misled
            // about the total duration. Surfaced as a note by the UI, not an error.
        }
        return problems
    }

    private fun isEffectivelyEmpty(node: Node, seenGroups: List<String>): Boolean = when (node) {
        is IntervalNode -> node.interval.omitted ||
            (!node.interval.manualCompletion && node.interval.durationMillis <= 0L)
        is RepeatGroup ->
            node.repeat <= 0 || node.children.isEmpty() || node.children.all { isEffectivelyEmpty(it, seenGroups) }
    }

    private fun walk(
        nodes: List<Node>,
        depth: Int,
        limits: EngineLimits,
        problems: MutableList<String>,
        path: String,
    ) {
        if (depth > limits.maxNestingDepth) {
            problems += "Groups are nested more than ${limits.maxNestingDepth} levels deep inside \"$path\"."
            return
        }
        for (node in nodes) {
            when (node) {
                is IntervalNode -> validateInterval(node.interval, problems, path)
                is RepeatGroup -> {
                    if (node.repeat < 1) {
                        problems += "Block \"${node.name}\" repeats ${node.repeat} times — must be 1 or more."
                    }
                    if (node.repeat > limits.maxRepeatCount) {
                        problems += "Block \"${node.name}\" repeats ${node.repeat} times — the limit is ${limits.maxRepeatCount}."
                    }
                    if (node.restAfterGroupMillis < 0) {
                        problems += "Rest after \"${node.name}\" cannot be negative."
                    }
                    if (node.ladder != null && node.ladder!!.deltaMillis != 0L && node.ladder!!.minMillis < 0) {
                        problems += "Ladder floor for \"${node.name}\" cannot be negative."
                    }
                    walk(node.children, depth + 1, limits, problems, "${node.name} → $path")
                }
            }
        }
    }

    private fun validateInterval(interval: IntervalSpec, problems: MutableList<String>, path: String) {
        val label = interval.displayName()
        if (interval.omitted) return
        if (interval.durationMillis < 0) {
            problems += "\"$label\" has a negative duration."
        }
        if (!interval.manualCompletion && interval.durationMillis <= 0L) {
            if (interval.kind == PhaseKind.WORK || interval.kind == PhaseKind.WARM_UP) {
                problems += "\"$label\" needs a positive duration (or mark it manual)."
            }
        }
        interval.reps?.let { if (it < 1) problems += "\"$label\" has an invalid repetition target ($it)." }
    }
}

class ExpansionException(message: String) : IllegalStateException(message)

// ---------------------------------------------------------------------------------------------
// Expanded timeline
// ---------------------------------------------------------------------------------------------

/** One concrete, playable step after expansion. */
data class TimelineStep(
    val index: Int,
    val name: String,
    val kind: PhaseKind,
    val durationMillis: Long,
    val manualCompletion: Boolean,
    val notes: String?,
    val speechText: String?,
    val colorArgb: Long?,
    val sound: SoundCue,
    val haptic: HapticCue,
    val reps: Int?,
    val equipment: String?,
    val sideLabel: String?,
    val imageUri: String?,
    /** 1-based round within the owning group, or null for top-level intervals. */
    val roundInGroup: Int?,
    val roundsInGroup: Int?,
    val groupId: String?,
    val groupName: String?,
    /** Nesting path from outermost group to innermost, used for "Round 2 of 3" copy. */
    val groupBreadcrumb: List<String>,
    /** Source node id, kept for edit-back and history attribution. */
    val sourceNodeId: String,
) {
    val isIndefinite: Boolean get() = manualCompletion || durationMillis <= 0L
}

data class ExpandedTimeline(
    val steps: List<TimelineStep>,
    /** Sum of timed steps. Indefinite steps contribute 0. */
    val knownMillis: Long,
    val openEndedSteps: Int,
    val warnings: List<String>,
) {
    val hasOpenEnded: Boolean get() = openEndedSteps > 0
    val isEmpty: Boolean get() = steps.isEmpty()
    /** Only meaningful when [hasOpenEnded] is false. */
    val totalMillis: Long? get() = if (hasOpenEnded) null else knownMillis
}

/**
 * Flattens a [WorkoutPlan] into a linear timeline.
 *
 * Rules (documented so behaviour is predictable):
 *  - zero/negative-duration timed intervals are omitted, but manual intervals are kept;
 *    omitted phases therefore simply "disappear" (e.g. preparation set to 0);
 *  - a group's trailing rest is dropped on the final repetition when [WorkoutPlan.includeFinalRest]
 *    is false, recursively for the outer-most group only;
 *  - ladders adjust WORK/REST durations by `delta * repetitionIndex`, clamped to the ladder bounds;
 *  - eager shuffling reorders effort intervals inside each group once, deterministically seeded by
 *    the plan id, so the order is frozen for the whole session.
 */
object TimelineExpander {

    fun expand(plan: WorkoutPlan, limits: EngineLimits = EngineLimits()): ExpandedTimeline {
        val warnings = mutableListOf<String>()
        val steps = mutableListOf<TimelineStep>()
        val random = if (plan.shuffleEager) kotlin.random.Random(plan.id.hashCode()) else null
        expandNodes(
            nodes = plan.nodes,
            depth = 1,
            limits = limits,
            steps = steps,
            warnings = warnings,
            breadcrumb = emptyList(),
            context = null,
            random = random,
        )
        if (!plan.includeFinalRest) trimTrailingRecovery(steps)
        if (steps.size > limits.maxExpandedIntervals) {
            throw ExpansionException(
                "This workout expands to ${formatCount(steps.size)} intervals, above the ${formatCount(limits.maxExpandedIntervals)} limit. " +
                    "Reduce repeats or split it into several workouts."
            )
        }
        val known = steps.filter { !it.isIndefinite }.sumOf { it.durationMillis }
        if (known > limits.maxTotalTimedMillis) {
            throw ExpansionException(
                "Timed content is ${formatDuration(known)}, above the ${formatDuration(limits.maxTotalTimedMillis)} limit."
            )
        }
        val openEnded = steps.count { it.isIndefinite }
        if (openEnded > 0) {
            warnings += "$openEnded interval(s) wait for you or run open-ended, so the total time is a minimum."
        }
        return ExpandedTimeline(steps, known, openEnded, warnings)
    }

    /** Sum of timed duration only — used by the UI while a plan is still being edited. */
    fun knownDurationMillis(plan: WorkoutPlan, limits: EngineLimits = EngineLimits()): Long =
        try {
            expand(plan, limits).knownMillis
        } catch (e: ExpansionException) {
            0L
        }

    /**
     * "Omit final rest" only removes trailing recovery (rest / transition) steps — a trailing
     * cool-down is deliberate content and is never dropped.
     */
    private fun trimTrailingRecovery(steps: MutableList<TimelineStep>) {
        while (steps.isNotEmpty()) {
            val last = steps.last()
            val isRecovery = last.kind == PhaseKind.REST || last.kind == PhaseKind.TRANSITION
            if (isRecovery && !last.manualCompletion) steps.removeAt(steps.lastIndex) else break
        }
    }

    /** Round/group metadata carried into the children of a repeating block. */
    private data class GroupContext(
        val groupId: String,
        val groupName: String,
        val round: Int,
        val rounds: Int,
        val breadcrumb: List<String>,
    )

    private fun expandNodes(
        nodes: List<Node>,
        depth: Int,
        limits: EngineLimits,
        steps: MutableList<TimelineStep>,
        warnings: MutableList<String>,
        breadcrumb: List<String>,
        context: GroupContext?,
        random: kotlin.random.Random?,
    ) {
        if (depth > limits.maxNestingDepth) {
            throw ExpansionException("Groups nested deeper than ${limits.maxNestingDepth} levels are not supported.")
        }
        val prepared: List<Node> = if (random != null && nodes.size > 1) shuffleEffortRuns(nodes, random) else nodes
        prepared.forEach { node ->
            when (node) {
                is IntervalNode -> appendInterval(
                    spec = node.interval,
                    steps = steps,
                    breadcrumb = breadcrumb,
                    context = context,
                    sourceNodeId = node.nodeId,
                )
                is RepeatGroup -> {
                    if (node.repeat < 1) throw ExpansionException("Block \"${node.name}\" must repeat at least once.")
                    if (node.repeat > limits.maxRepeatCount) {
                        throw ExpansionException(
                            "Block \"${node.name}\" repeats ${formatCount(node.repeat)} times, above the ${formatCount(limits.maxRepeatCount)} limit."
                        )
                    }
                    val childBreadcrumb = breadcrumb + node.name
                    for (round in 1..node.repeat) {
                        expandNodes(
                            nodes = node.children,
                            depth = depth + 1,
                            limits = limits,
                            steps = steps,
                            warnings = warnings,
                            breadcrumb = childBreadcrumb,
                            context = GroupContext(node.nodeId, node.name, round, node.repeat, childBreadcrumb),
                            random = null,
                        )
                        if (node.restAfterGroupMillis > 0L) {
                            appendInterval(
                                spec = IntervalSpec(
                                    id = "${node.nodeId}#rest",
                                    name = node.restAfterGroupName,
                                    kind = PhaseKind.REST,
                                    durationMillis = node.restAfterGroupMillis,
                                ),
                                steps = steps,
                                breadcrumb = childBreadcrumb,
                                context = GroupContext(node.nodeId, node.name, round, node.repeat, childBreadcrumb),
                                sourceNodeId = "${node.nodeId}#rest",
                            )
                        }
                    }
                }
            }
        }
    }

    private fun appendInterval(
        spec: IntervalSpec,
        steps: MutableList<TimelineStep>,
        breadcrumb: List<String>,
        context: GroupContext?,
        sourceNodeId: String,
    ) {
        if (spec.omitted) return
        if (!spec.manualCompletion && spec.durationMillis <= 0L) return // zero-length phases are omitted
        steps += TimelineStep(
            index = steps.size,
            name = spec.displayName(),
            kind = spec.kind,
            durationMillis = spec.durationMillis,
            manualCompletion = spec.manualCompletion,
            notes = spec.notes,
            speechText = spec.speechText,
            colorArgb = spec.colorArgb,
            sound = spec.sound,
            haptic = spec.haptic,
            reps = spec.reps,
            equipment = spec.equipment,
            sideLabel = spec.sideLabel,
            imageUri = spec.imageUri,
            roundInGroup = context?.round,
            roundsInGroup = context?.rounds,
            groupId = context?.groupId,
            groupName = context?.groupName,
            groupBreadcrumb = breadcrumb,
            sourceNodeId = sourceNodeId,
        )
    }

    /** Applies a ladder to a duration for a given 0-based repetition index. */
    fun applyLadder(base: Long, ladder: Ladder?, repetitionIndex: Int): Long {
        if (ladder == null || ladder.deltaMillis == 0L) return base
        val candidate = base + ladder.deltaMillis * repetitionIndex
        return candidate.coerceIn(ladder.minMillis, ladder.maxMillis)
    }

    private fun shuffleEffortRuns(nodes: List<Node>, random: kotlin.random.Random): List<Node> {
        val result = mutableListOf<Node>()
        var run = mutableListOf<Node>()
        fun flush() {
            if (run.size > 1) run.shuffle(random)
            result += run
            run = mutableListOf()
        }
        nodes.forEach { node ->
            val isEffortInterval = node is IntervalNode && node.interval.kind == PhaseKind.WORK && !node.interval.omitted
            if (isEffortInterval) run += node else flush().also { result += node }
        }
        flush()
        return result
    }
}

// ---------------------------------------------------------------------------------------------
// Formatting helpers (shared by UI + engine tests)
// ---------------------------------------------------------------------------------------------

fun formatDuration(millis: Long): String {
    val totalSeconds = (millis + 999) / 1000
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

fun formatDurationSpoken(millis: Long): String {
    val totalSeconds = (millis + 999) / 1000
    val m = totalSeconds / 60
    val s = totalSeconds % 60
    return buildString {
        if (m > 0) append("$m minute${if (m == 1L) "" else "s"}")
        if (m > 0 && s > 0) append(" ")
        if (s > 0 || m == 0L) append("$s second${if (s == 1L) "" else "s"}")
    }
}
