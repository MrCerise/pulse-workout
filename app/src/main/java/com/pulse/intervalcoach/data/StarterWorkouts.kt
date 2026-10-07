package com.pulse.intervalcoach.data

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
 */
object StarterWorkouts {

    data class Template(
        val plan: WorkoutPlan,
        val iconKey: String,
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
        description: String,
        nodes: List<Node>,
        includeFinalRest: Boolean = true,
        equipment: String? = null,
        shuffle: Boolean = false,
    ) = WorkoutPlan(
        id = id(seed),
        name = name,
        description = description,
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
                    description = "The textbook protocol: eight rounds of 20 seconds all-out effort against 10 seconds of recovery. Pick one movement and stay honest about the pace.",
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
                iconKey = "tabata",
                tags = listOf("HIIT", "4 minutes", "no equipment"),
            ),
            Template(
                plan = plan(
                    seed = "hiit-starter",
                    name = "HIIT Starter",
                    type = WorkoutType.HIIT,
                    description = "A gentle entry point to interval training: six rounds of 40 seconds of movement with 20 seconds to breathe. Works with any cardio machine or bodyweight move.",
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
                iconKey = "hiit",
                tags = listOf("HIIT", "beginner", "6 minutes"),
            ),
            Template(
                plan = plan(
                    seed = "boxing",
                    name = "Boxing Rounds",
                    type = WorkoutType.BOXING,
                    description = "Three three-minute rounds with a minute on the stool between them. Bell at the start and end of every round, with a ten-second warning before each bell.",
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
                iconKey = "boxing",
                tags = listOf("boxing", "conditioning", "11 minutes"),
            ),
            Template(
                plan = plan(
                    seed = "circuit-bodyweight",
                    name = "Bodyweight Circuit",
                    type = WorkoutType.CIRCUIT,
                    description = "Three circuits of five bodyweight stations, 45 seconds on and 15 seconds to change station, with a full minute between circuits.",
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
                iconKey = "circuit",
                tags = listOf("circuit", "bodyweight", "17 minutes"),
            ),
            Template(
                plan = plan(
                    seed = "emom-10",
                    name = "EMOM 10",
                    type = WorkoutType.EMOM,
                    description = "Every minute on the minute for ten minutes: a fresh block starts on each minute boundary no matter how early you finish, so the clock never drifts.",
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
                iconKey = "emom",
                tags = listOf("EMOM", "conditioning", "10 minutes"),
            ),
            Template(
                plan = plan(
                    seed = "amrap-12",
                    name = "AMRAP 12",
                    type = WorkoutType.AMRAP,
                    description = "Twelve minutes on a running clock. Complete as many rounds as you can and tap the round button each time you finish a circuit — the clock keeps its own time.",
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
                iconKey = "amrap",
                tags = listOf("AMRAP", "conditioning", "12 minutes"),
            ),
            Template(
                plan = plan(
                    seed = "strength-sets",
                    name = "Strength Sets",
                    type = WorkoutType.STRENGTH,
                    description = "Four working sets of 45 seconds with a generous 90 seconds between them. The set waits for you if you need a moment longer — tap when the set is done.",
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
                iconKey = "strength",
                tags = listOf("strength", "sets", "~7 minutes"),
            ),
            Template(
                plan = plan(
                    seed = "run-walk",
                    name = "Run / Walk Intervals",
                    type = WorkoutType.RUN_WALK,
                    description = "A classic return-to-running ladder: five rounds of one minute running and ninety seconds walking, wrapped in a five-minute warm-up and cool-down.",
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
                iconKey = "run",
                tags = listOf("running", "walking", "28 minutes"),
            ),
            Template(
                plan = plan(
                    seed = "stretch",
                    name = "Full-Body Stretch",
                    type = WorkoutType.STRETCHING,
                    description = "Six long holds with a short transition between each. Every hold runs on both sides — the interval duplicates itself left and right so nothing gets forgotten.",
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
                iconKey = "stretch",
                tags = listOf("stretching", "mobility", "7 minutes"),
            ),
            Template(
                plan = plan(
                    seed = "breathing",
                    name = "Box Breathing",
                    type = WorkoutType.BREATHING,
                    description = "Four counts in, four hold, four out, four hold — eight calm cycles with a slow animation pacing each phase. Used for down-regulation after hard training.",
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
                iconKey = "breathing",
                tags = listOf("breathing", "recovery", "2 minutes"),
            ),
            Template(
                plan = plan(
                    seed = "pomodoro",
                    name = "Focus Blocks",
                    type = WorkoutType.FOCUS,
                    description = "Four 25-minute deep-work blocks with short breaks, then a longer 15-minute break. Timed like intervals, counted like a Pomodoro.",
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
                iconKey = "focus",
                tags = listOf("focus", "work", "2 hours 10 minutes"),
            ),
            Template(
                plan = plan(
                    seed = "ladder",
                    name = "Mobility Ladder",
                    type = WorkoutType.CUSTOM_SEQUENCE,
                    description = "Four rounds where the hold grows by ten seconds each time: 30, 40, 50 then 60 seconds, with 30 seconds of rest between. Demonstrates progressive ladders.",
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
                iconKey = "ladder",
                tags = listOf("mobility", "progressive", "6 minutes"),
            ),
            Template(
                plan = plan(
                    seed = "cue-minutes",
                    name = "Hourly Posture Cue",
                    type = WorkoutType.CUE_TIMER,
                    description = "An open-ended session that sounds a cue on every minute so you can stand up, reset your posture and sit back down. Stop whenever you like.",
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
                iconKey = "cue",
                tags = listOf("desk", "habit", "open-ended"),
            ),
            Template(
                plan = plan(
                    seed = "stopwatch",
                    name = "Stopwatch",
                    type = WorkoutType.STOPWATCH,
                    description = "A plain count-up stopwatch with lap recording. Useful for time trials, holds, or anything where you want splits rather than phases.",
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
                iconKey = "stopwatch",
                tags = listOf("stopwatch", "open-ended"),
            ),
            Template(
                plan = plan(
                    seed = "compound",
                    name = "Warm-up + Tabata + Core",
                    type = WorkoutType.COMPOUND,
                    description = "Three workouts in one session: a two-minute warm-up, a classic Tabata block, then a three-station core finisher. Shows how compound workouts chain blocks together.",
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
                iconKey = "compound",
                tags = listOf("compound", "full session", "11 minutes"),
            ),
        )
    }

    /** The workout offered at the end of onboarding and on an empty Home screen. */
    val examplePlan: WorkoutPlan get() = templates.first { it.plan.name == "HIIT Starter" }.plan

    /** Known duration for a template, computed by the engine (used for list previews and tests). */
    fun knownDuration(plan: WorkoutPlan): Long = TimelineExpander.knownDurationMillis(plan)
}
