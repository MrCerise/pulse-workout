package com.pulse.intervalcoach.parser

import com.pulse.engine.IntervalNode
import com.pulse.engine.IntervalSpec
import com.pulse.engine.Node
import com.pulse.engine.PhaseKind
import com.pulse.engine.RepeatGroup
import com.pulse.engine.SoundCue
import com.pulse.engine.TimelineExpander
import com.pulse.engine.WorkoutPlan
import com.pulse.engine.WorkoutType
import java.util.UUID

/**
 * Deterministic text-to-workout parser.
 *
 * This is a *pattern parser*, not an AI assistant: it recognises explicit phrasings it knows and
 * reports exactly what it could not understand instead of inventing an interpretation. Everything
 * it produces is shown as an editable preview before saving or starting.
 *
 * Supported vocabulary (case-insensitive, commas/semicolons/"and" separate phrases):
 *   "6 rounds" / "6 sets" / "6x"        repetition blocks
 *   "40 seconds work" / "work 40s"      timed work intervals
 *   "20 seconds rest" / "20s break"     timed recovery
 *   "1 minute warm-up" / "warm up 1m"   warm-up phase
 *   "cool down 3 minutes"               cool-down phase
 *   "get ready 10 seconds"              preparation phase
 *   durations: "90 seconds", "1 minute 30 seconds", "1:30", "45s", "2m", "1 min"
 *   keywords: "tabata" (8 × 20 s / 10 s), "emom 10 minutes", "amrap 12 minutes", "box breathing"
 */
object WorkoutTextParser {

    data class ParseResult(
        val plan: WorkoutPlan?,
        val recognised: List<String>,
        val unmatched: List<String>,
        val message: String,
    ) {
        val hasStructure: Boolean get() = plan != null
    }

    fun parse(input: String, name: String = "Parsed workout"): ParseResult {
        val phrases = input
            .split(',', ';', '\n', '·')
            .flatMap { it.split(Regex("\\band\\b", RegexOption.IGNORE_CASE)) }
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        if (phrases.isEmpty()) {
            return ParseResult(null, emptyList(), emptyList(), "Nothing to parse.")
        }

        var rounds: Int? = null
        var workMillis: Long? = null
        var restMillis: Long? = null
        var warmUpMillis: Long? = null
        var coolDownMillis: Long? = null
        var prepareMillis: Long? = null
        var type: WorkoutType? = null
        var intervalMinutes: Int? = null
        val recognised = mutableListOf<String>()
        val unmatched = mutableListOf<String>()

        phrases.forEach { phrase ->
            val lower = phrase.lowercase()
            when {
                Regex("^(\\d+)\\s*(rounds?|sets?|x)\\b").find(lower) != null -> {
                    val value = Regex("^(\\d+)").find(lower)!!.groupValues[1].toInt()
                    rounds = value
                    recognised += phrase
                }
                lower.contains("tabata") -> {
                    type = WorkoutType.TABATA
                    rounds = rounds ?: 8
                    workMillis = workMillis ?: 20_000
                    restMillis = restMillis ?: 10_000
                    recognised += phrase
                }
                lower.startsWith("emom") || lower.startsWith("e2mom") -> {
                    type = if (lower.startsWith("emom")) WorkoutType.EMOM else WorkoutType.E2MOM
                    intervalMinutes = parseDuration(lower)?.let { (it / 60_000).toInt() } ?: 10
                    recognised += phrase
                }
                lower.startsWith("amrap") -> {
                    type = WorkoutType.AMRAP
                    intervalMinutes = parseDuration(lower)?.let { (it / 60_000).toInt() } ?: 12
                    recognised += phrase
                }
                lower.contains("box breathing") || lower.contains("breathing") -> {
                    type = WorkoutType.BREATHING
                    recognised += phrase
                }
                lower.contains("warm") -> {
                    parseDuration(lower)?.let { warmUpMillis = it; recognised += phrase } ?: unmatched.add(phrase)
                }
                lower.contains("cool") -> {
                    parseDuration(lower)?.let { coolDownMillis = it; recognised += phrase } ?: unmatched.add(phrase)
                }
                lower.contains("ready") || lower.contains("prepar") -> {
                    parseDuration(lower)?.let { prepareMillis = it; recognised += phrase } ?: unmatched.add(phrase)
                }
                lower.contains("rest") || lower.contains("break") || lower.contains("recover") -> {
                    parseDuration(lower)?.let { restMillis = it; recognised += phrase } ?: unmatched.add(phrase)
                }
                lower.contains("work") || lower.contains("effort") -> {
                    parseDuration(lower)?.let { workMillis = it; recognised += phrase } ?: unmatched.add(phrase)
                }
                else -> {
                    val duration = parseDuration(lower)
                    if (duration != null && workMillis == null) {
                        workMillis = duration
                        recognised += phrase
                    } else {
                        unmatched += phrase
                    }
                }
            }
        }

        val nodes = mutableListOf<Node>()
        prepareMillis?.let { nodes += phase("Get ready", PhaseKind.PREPARE, it, SoundCue.DOUBLE_BEEP) }
        warmUpMillis?.let { nodes += phase("Warm-up", PhaseKind.WARM_UP, it, SoundCue.CHIME) }

        when (type) {
            WorkoutType.AMRAP -> {
                val minutes = intervalMinutes ?: 12
                nodes += IntervalNode(
                    "amrap",
                    IntervalSpec(
                        id = "amrap",
                        name = "AMRAP $minutes:00",
                        kind = PhaseKind.WORK,
                        durationMillis = minutes * 60_000L,
                        manualCompletion = true,
                        notes = "Count your rounds as you go",
                    ),
                )
            }
            WorkoutType.EMOM, WorkoutType.E2MOM -> {
                val minutes = intervalMinutes ?: 10
                val perMinute = if (type == WorkoutType.E2MOM) 2 else 1
                nodes += RepeatGroup(
                    nodeId = "emom",
                    name = "Blocks",
                    repeat = minutes / perMinute,
                    children = listOf(phase("Block", PhaseKind.WORK, perMinute * 60_000L, SoundCue.BEEP)),
                )
            }
            WorkoutType.BREATHING -> {
                nodes += RepeatGroup(
                    nodeId = "breath",
                    name = "Cycles",
                    repeat = 8,
                    children = listOf(
                        phase("Breathe in", PhaseKind.WORK, 4_000, SoundCue.TICK),
                        phase("Hold", PhaseKind.CUSTOM, 4_000, SoundCue.NONE),
                        phase("Breathe out", PhaseKind.REST, 4_000, SoundCue.TICK),
                        phase("Hold", PhaseKind.CUSTOM, 4_000, SoundCue.NONE),
                    ),
                )
            }
            else -> {
                val work = workMillis
                val rest = restMillis
                if (work != null) {
                    val count = rounds ?: 1
                    if (count > 1) {
                        val children = mutableListOf<Node>()
                        children += phase("Work", PhaseKind.WORK, work, SoundCue.TICK)
                        if (rest != null && rest > 0) children += phase("Rest", PhaseKind.REST, rest, SoundCue.BEEP)
                        nodes += RepeatGroup(nodeId = "rounds", name = "$count rounds", repeat = count, children = children)
                    } else {
                        nodes += phase("Work", PhaseKind.WORK, work, SoundCue.TICK)
                        if (rest != null && rest > 0) nodes += phase("Rest", PhaseKind.REST, rest, SoundCue.BEEP)
                    }
                } else if (rest != null) {
                    nodes += phase("Rest", PhaseKind.REST, rest, SoundCue.BEEP)
                }
            }
        }

        coolDownMillis?.let { nodes += phase("Cool-down", PhaseKind.COOLDOWN, it, SoundCue.CHIME) }

        if (nodes.isEmpty()) {
            return ParseResult(
                plan = null,
                recognised = recognised,
                unmatched = unmatched,
                message = "No pattern recognised. Try naming rounds, seconds or minutes, for example " +
                    "“8 rounds, 30 seconds work, 15 seconds rest”.",
            )
        }

        val plan = WorkoutPlan(
            id = UUID.randomUUID().toString(),
            name = name,
            description = "Created from your description: “$input”",
            type = type ?: WorkoutType.CUSTOM_SEQUENCE,
            nodes = nodes,
            // Parsed plans keep the final rest: typing "6 rounds, 40 seconds work, 20 seconds rest"
            // should give six rests, and "tabata" should give the textbook 4:00 protocol.
            includeFinalRest = true,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
        )
        val duration = runCatching { TimelineExpander.expand(plan).knownMillis }.getOrDefault(0L)
        val summary = buildString {
            append("Total ")
            append(com.pulse.engine.formatDuration(duration))
            append(" · ")
            append(runCatching { TimelineExpander.expand(plan).steps.size }.getOrDefault(0))
            append(" intervals")
        }
        val message = if (unmatched.isEmpty()) {
            "Understood: ${recognised.joinToString("; ")}. $summary."
        } else {
            "Understood: ${recognised.joinToString("; ")}. Not understood: “${unmatched.joinToString("; ")}” — " +
                "this is a deterministic pattern parser, not an AI assistant. $summary."
        }
        return ParseResult(plan, recognised, unmatched, message)
    }

    /** Parses durations such as "90 seconds", "1 minute 30 seconds", "1:30", "45s", "2m", "1 min". */
    fun parseDuration(text: String): Long? {
        val lower = text.lowercase()
        val clock = Regex("(\\d{1,2}):(\\d{2})").find(lower)
        if (clock != null) {
            val minutes = clock.groupValues[1].toLong()
            val seconds = clock.groupValues[2].toLong()
            return (minutes * 60 + seconds) * 1000
        }
        var total = 0L
        var found = false
        Regex("(\\d+(?:\\.\\d+)?)\\s*(hours?|hrs?|h)\\b").find(lower)?.let {
            total += (it.groupValues[1].toDouble() * 3_600_000).toLong(); found = true
        }
        Regex("(\\d+(?:\\.\\d+)?)\\s*(minutes?|mins?|m)\\b").find(lower)?.let {
            total += (it.groupValues[1].toDouble() * 60_000).toLong(); found = true
        }
        Regex("(\\d+(?:\\.\\d+)?)\\s*(seconds?|secs?|s)\\b").find(lower)?.let {
            total += (it.groupValues[1].toDouble() * 1000).toLong(); found = true
        }
        return if (found) total.takeIf { it > 0 } else null
    }

    private fun phase(name: String, kind: PhaseKind, millis: Long, sound: SoundCue): IntervalNode =
        IntervalNode(
            nodeId = "$name-${kind.name}-$millis",
            interval = IntervalSpec(id = "$name-${kind.name}-$millis", name = name, kind = kind, durationMillis = millis, sound = sound),
        )
}
