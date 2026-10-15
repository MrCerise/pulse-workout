package com.pulse.library

import com.pulse.engine.HapticCue
import com.pulse.engine.IntervalNode
import com.pulse.engine.IntervalSpec
import com.pulse.engine.Ladder
import com.pulse.engine.Node
import com.pulse.engine.PhaseKind
import com.pulse.engine.RepeatGroup
import com.pulse.engine.SoundCue
import com.pulse.engine.TimelineExpander
import com.pulse.engine.WorkoutPlan
import com.pulse.engine.WorkoutType
import java.util.UUID

/**
 * Bundled starter workouts.
 *
 * These are *editable examples*, not a pretend history: every duration shown in the UI is computed
 * by the engine from this structure, and the app never seeds fake completed sessions. Descriptions
 * are original copy written for PULSE.
 *
 * This lives in `:library` — a pure Kotlin module both APKs depend on — because the phone and the
 * watch offer the same catalogue. The phone seeds these into Room on first run; the watch seeds
 * them into its own store when it has never been paired, so a watch bought and worn without a
 * phone still has workouts to run. The catalogue itself is data: no Android, no resources, no
 * user-visible copy (the names are content, not UI strings, and are not translated).
 */
object StarterWorkouts {

    /**
     * A bundled starting point.
     *
     * There is deliberately no icon or colour here: the plan's category supplies both, which is why
     * a template and a saved workout of the same category look identical without this file knowing
     * anything about icons.
     */
    data class Template(
        val plan: WorkoutPlan,
        val tags: List<String>,
    )

    private fun id(seed: String) = "builtin-" + UUID.nameUUIDFromBytes(seed.toByteArray()).toString()

    private fun node(name: String, kind: PhaseKind, millis: Long, sound: SoundCue = SoundCue.TICK, haptic: HapticCue = HapticCue.LIGHT, notes: String? = null, speech: String? = null): IntervalNode =
        IntervalNode(
            nodeId = "$name-${kind.name}-$millis",
            interval = IntervalSpec(
                id = "$name-${kind.name}-$millis",
                name = name,
                kind = kind,
                durationMillis = millis,
                sound = sound,
                haptic = haptic,
                notes = notes,
                speechText = speech,
            ),
        )

    private fun work(name: String, millis: Long, notes: String? = null) = node(name, PhaseKind.WORK, millis)
    private fun rest(name: String, millis: Long) = node(name, PhaseKind.REST, millis)
    private fun prepare(millis: Long) = node("Get ready", PhaseKind.PREPARE, millis, SoundCue.DOUBLE_BEEP)
    private fun cooldown(millis: Long) = node("Cool-down", PhaseKind.COOLDOWN, millis, SoundCue.CHIME, haptic = HapticCue.DOUBLE)

    private fun plan(
        seed: String,
        name: String,
        type: WorkoutType,
        nodes: List<Node>,
        includeFinalRest: Boolean = true,
        equipment: String? = null,
        shuffle: Boolean = false,
    ) = WorkoutPlan(
        id = id(seed),
        name = name,
        type = type,
        nodes = nodes,
        includeFinalRest = includeFinalRest,
        equipment = equipment,
        shuffleEager = shuffle,
    )

    val templates: List<Template> by lazy {
        listOf(
            Template(
                plan = plan(
                    seed = "tabata",
                    name = "Tabata Classic",
                    type = WorkoutType.TABATA,
                    // The textbook protocol, and deliberately without a preparation interval: with the
                    // final rest the workout is exactly 4:00, without it 3:50. Every other template
                    // starts with a 10 second "get ready".
                    nodes = listOf(
                        RepeatGroup(
                            nodeId = "tabata-rounds",
                            name = "8 rounds",
                            repeat = 8,
                            children = listOf(work("Max effort", 20_000), rest("Recover", 10_000)),
                        ),
                    ),
                    includeFinalRest = true,
                ),
                tags = listOf("HIIT", "4 minutes", "no equipment"),
            ),
            Template(
                plan = plan(
                    seed = "hiit-starter",
                    name = "HIIT Starter",
                    type = WorkoutType.HIIT,
                    nodes = listOf(
                        prepare(10_000),
                        RepeatGroup(
                            nodeId = "hiit-rounds",
                            name = "6 rounds",
                            repeat = 6,
                            children = listOf(work("Move", 40_000), rest("Breathe", 20_000)),
                        ),
                        cooldown(60_000),
                    ),
                    includeFinalRest = false,
                ),
                tags = listOf("HIIT", "beginner", "6 minutes"),
            ),
            Template(
                plan = plan(
                    seed = "boxing",
                    name = "Boxing Rounds",
                    type = WorkoutType.BOXING,
                    nodes = listOf(
                        node("Wrap up", PhaseKind.PREPARE, 30_000, SoundCue.BELL),
                        RepeatGroup(
                            nodeId = "boxing-rounds",
                            name = "Rounds",
                            repeat = 3,
                            children = listOf(
                                node("Round", PhaseKind.WORK, 180_000, SoundCue.BELL, HapticCue.STRONG),
                            ),
                            restAfterGroupMillis = 60_000,
                            restAfterGroupName = "Stool break",
                        ),
                    ),
                    includeFinalRest = false,
                    equipment = "Gloves, wraps, bag or pads",
                ),
                tags = listOf("boxing", "conditioning", "11 minutes"),
            ),
            Template(
                plan = plan(
                    seed = "circuit-bodyweight",
                    name = "Bodyweight Circuit",
                    type = WorkoutType.CIRCUIT,
                    nodes = listOf(
                        prepare(15_000),
                        RepeatGroup(
                            nodeId = "circuit",
                            name = "Circuit",
                            repeat = 3,
                            children = listOf(
                                work("Squats", 45_000, "Chest tall, knees tracking over toes"),
                                node("Change", PhaseKind.TRANSITION, 15_000),
                                work("Push-ups", 45_000, "Elbows at 45 degrees"),
                                node("Change", PhaseKind.TRANSITION, 15_000),
                                work("Lunges", 45_000, "Alternate legs"),
                                node("Change", PhaseKind.TRANSITION, 15_000),
                                work("Plank", 45_000, "Ribs down, glutes on"),
                                node("Change", PhaseKind.TRANSITION, 15_000),
                                work("Glute bridge", 45_000, "Drive through the heels"),
                            ),
                            restAfterGroupMillis = 60_000,
                            restAfterGroupName = "Rest between circuits",
                        ),
                    ),
                    includeFinalRest = false,
                    equipment = "Mat (optional)",
                ),
                tags = listOf("circuit", "bodyweight", "17 minutes"),
            ),
            Template(
                plan = plan(
                    seed = "emom-10",
                    name = "EMOM 10",
                    type = WorkoutType.EMOM,
                    nodes = listOf(
                        prepare(10_000),
                        RepeatGroup(
                            nodeId = "emom",
                            name = "Minutes",
                            repeat = 10,
                            children = listOf(
                                work("Minute block", 60_000, "Finish your reps early, then rest until the next minute"),
                            ),
                        ),
                    ),
                    includeFinalRest = false,
                    equipment = "Kettlebell or dumbbell",
                ),
                tags = listOf("EMOM", "conditioning", "10 minutes"),
            ),
            Template(
                plan = plan(
                    seed = "amrap-12",
                    name = "AMRAP 12",
                    type = WorkoutType.AMRAP,
                    nodes = listOf(
                        prepare(5_000),
                        IntervalNode(
                            nodeId = "amrap-block",
                            interval = IntervalSpec(
                                id = "amrap-block",
                                name = "AMRAP — 5 burpees, 10 swings, 15 air squats",
                                kind = PhaseKind.WORK,
                                durationMillis = 720_000,
                                manualCompletion = true,
                                notes = "Tap the round button each time you complete the sequence.",
                            ),
                        ),
                    ),
                ),
                tags = listOf("AMRAP", "conditioning", "12 minutes"),
            ),
            Template(
                plan = plan(
                    seed = "strength-sets",
                    name = "Strength Sets",
                    type = WorkoutType.STRENGTH,
                    nodes = listOf(
                        prepare(20_000),
                        RepeatGroup(
                            nodeId = "strength",
                            name = "Sets",
                            repeat = 4,
                            children = listOf(
                                IntervalNode(
                                    nodeId = "strength-set",
                                    interval = IntervalSpec(
                                        id = "strength-set",
                                        name = "Working set",
                                        kind = PhaseKind.WORK,
                                        durationMillis = 0,
                                        manualCompletion = true,
                                        reps = 8,
                                        notes = "Target 8 reps at a weight you could lift 10 times",
                                    ),
                                ),
                                rest("Rest", 90_000),
                            ),
                        ),
                    ),
                    includeFinalRest = false,
                    equipment = "Barbell or dumbbells",
                ),
                tags = listOf("strength", "sets", "~7 minutes"),
            ),
            Template(
                plan = plan(
                    seed = "run-walk",
                    name = "Run / Walk Intervals",
                    type = WorkoutType.RUN_WALK,
                    nodes = listOf(
                        node("Walk warm-up", PhaseKind.WARM_UP, 300_000, SoundCue.CHIME),
                        RepeatGroup(
                            nodeId = "runwalk",
                            name = "5 rounds",
                            repeat = 5,
                            children = listOf(work("Run", 60_000), rest("Walk", 90_000)),
                        ),
                        cooldown(300_000),
                    ),
                    includeFinalRest = false,
                ),
                tags = listOf("running", "walking", "28 minutes"),
            ),
            Template(
                plan = plan(
                    seed = "stretch",
                    name = "Full-Body Stretch",
                    type = WorkoutType.STRETCHING,
                    nodes = listOf(
                        prepare(5_000),
                        RepeatGroup(
                            nodeId = "stretch",
                            name = "Holds",
                            repeat = 6,
                            children = listOf(
                                IntervalNode(
                                    nodeId = "hold",
                                    interval = IntervalSpec(
                                        id = "hold",
                                        name = "Hip flexor stretch",
                                        kind = PhaseKind.WORK,
                                        durationMillis = 30_000,
                                        sideLabel = "Left",
                                        speechText = "Left side. Ease into the stretch.",
                                        notes = "Alternate: hip flexor, hamstring, chest, quad, calf, shoulder",
                                    ),
                                ),
                                node("Switch sides", PhaseKind.TRANSITION, 5_000),
                                IntervalNode(
                                    nodeId = "hold-right",
                                    interval = IntervalSpec(
                                        id = "hold-right",
                                        name = "Hip flexor stretch",
                                        kind = PhaseKind.WORK,
                                        durationMillis = 30_000,
                                        sideLabel = "Right",
                                        speechText = "Right side.",
                                    ),
                                ),
                                node("Next stretch", PhaseKind.TRANSITION, 5_000),
                            ),
                            restAfterGroupMillis = 0L,
                        ),
                    ),
                    includeFinalRest = false,
                    equipment = "Mat",
                ),
                tags = listOf("stretching", "mobility", "7 minutes"),
            ),
            Template(
                plan = plan(
                    seed = "breathing",
                    name = "Box Breathing",
                    type = WorkoutType.BREATHING,
                    nodes = listOf(
                        RepeatGroup(
                            nodeId = "breath",
                            name = "8 cycles",
                            repeat = 8,
                            children = listOf(
                                IntervalNode("inhale", IntervalSpec("inhale", "Breathe in", PhaseKind.WORK, 4_000, sound = SoundCue.TICK, haptic = HapticCue.LIGHT, speechText = "In")),
                                IntervalNode("hold-in", IntervalSpec("hold-in", "Hold", PhaseKind.CUSTOM, 4_000, sound = SoundCue.NONE, haptic = HapticCue.NONE, speechText = "Hold")),
                                IntervalNode("exhale", IntervalSpec("exhale", "Breathe out", PhaseKind.REST, 4_000, sound = SoundCue.TICK, haptic = HapticCue.LIGHT, speechText = "Out")),
                                IntervalNode("hold-out", IntervalSpec("hold-out", "Hold", PhaseKind.CUSTOM, 4_000, sound = SoundCue.NONE, haptic = HapticCue.NONE, speechText = "Hold")),
                            ),
                        ),
                    ),
                    includeFinalRest = false,
                ),
                tags = listOf("breathing", "recovery", "2 minutes"),
            ),
            Template(
                plan = plan(
                    seed = "pomodoro",
                    name = "Focus Blocks",
                    type = WorkoutType.FOCUS,
                    nodes = listOf(
                        RepeatGroup(
                            nodeId = "pomodoro",
                            name = "4 blocks",
                            repeat = 4,
                            children = listOf(
                                work("Focus", 1_500_000, "Phone away, one task only"),
                                rest("Short break", 300_000),
                            ),
                            restAfterGroupMillis = 900_000,
                            restAfterGroupName = "Long break",
                        ),
                    ),
                    includeFinalRest = false,
                ),
                tags = listOf("focus", "work", "2 hours 10 minutes"),
            ),
            Template(
                plan = plan(
                    seed = "ladder",
                    name = "Mobility Ladder",
                    type = WorkoutType.CUSTOM_SEQUENCE,
                    nodes = listOf(
                        prepare(10_000),
                        RepeatGroup(
                            nodeId = "ladder",
                            name = "Ladder",
                            repeat = 4,
                            children = listOf(
                                work("Hold", 30_000, "Hold position, breathe steadily"),
                                rest("Rest", 30_000),
                            ),
                            ladder = Ladder(target = Ladder.TargetKind.WORK, deltaMillis = 10_000, minMillis = 10_000, maxMillis = 90_000),
                        ),
                    ),
                    includeFinalRest = false,
                ),
                tags = listOf("mobility", "progressive", "6 minutes"),
            ),
            Template(
                plan = plan(
                    seed = "cue-minutes",
                    name = "Hourly Posture Cue",
                    type = WorkoutType.CUE_TIMER,
                    nodes = listOf(
                        IntervalNode(
                            nodeId = "cue",
                            interval = IntervalSpec(
                                id = "cue",
                                name = "Posture reset",
                                kind = PhaseKind.CUSTOM,
                                durationMillis = 0,
                                manualCompletion = true,
                                sound = SoundCue.CHIME,
                                speechText = "Stand tall",
                            ),
                        ),
                    ),
                ).copy(periodicCueMillis = 60_000),
                tags = listOf("desk", "habit", "open-ended"),
            ),
            Template(
                plan = plan(
                    seed = "stopwatch",
                    name = "Stopwatch",
                    type = WorkoutType.STOPWATCH,
                    nodes = listOf(
                        IntervalNode(
                            nodeId = "stopwatch",
                            interval = IntervalSpec(
                                id = "stopwatch",
                                name = "Stopwatch",
                                kind = PhaseKind.CUSTOM,
                                durationMillis = 0,
                                manualCompletion = true,
                            ),
                        ),
                    ),
                ),
                tags = listOf("stopwatch", "open-ended"),
            ),
            Template(
                plan = plan(
                    seed = "compound",
                    name = "Warm-up + Tabata + Core",
                    type = WorkoutType.COMPOUND,
                    nodes = listOf(
                        node("Easy warm-up", PhaseKind.WARM_UP, 120_000, SoundCue.CHIME),
                        RepeatGroup(
                            nodeId = "tabata-part",
                            name = "Tabata",
                            repeat = 8,
                            children = listOf(work("Max effort", 20_000), rest("Recover", 10_000)),
                        ),
                        RepeatGroup(
                            nodeId = "core-part",
                            name = "Core",
                            repeat = 3,
                            children = listOf(
                                work("Hollow hold", 30_000),
                                node("Change", PhaseKind.TRANSITION, 10_000),
                                work("Russian twists", 30_000),
                                node("Change", PhaseKind.TRANSITION, 10_000),
                                work("Plank", 30_000),
                            ),
                            restAfterGroupMillis = 45_000,
                            restAfterGroupName = "Core rest",
                        ),
                        cooldown(120_000),
                    ),
                    includeFinalRest = false,
                ),
                tags = listOf("compound", "full session", "11 minutes"),
            ),
        )
    }

    /** The workout offered at the end of onboarding and on an empty Home screen. */
    val examplePlan: WorkoutPlan get() = templates.first { it.plan.name == "HIIT Starter" }.plan

    /** Known duration for a template, computed by the engine (used for list previews and tests). */
    fun knownDuration(plan: WorkoutPlan): Long = TimelineExpander.knownDurationMillis(plan)
}
