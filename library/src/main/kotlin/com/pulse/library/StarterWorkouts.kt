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

    private fun work(name: String, millis: Long, notes: String? = null) = node(name, PhaseKind.WORK, millis, notes = notes)
    private fun rest(name: String, millis: Long) = node(name, PhaseKind.REST, millis)
    private fun prepare(millis: Long) = node("Get ready", PhaseKind.PREPARE, millis, SoundCue.DOUBLE_BEEP)
    private fun cooldown(millis: Long) = node("Cool-down", PhaseKind.COOLDOWN, millis, SoundCue.CHIME, haptic = HapticCue.DOUBLE)

    /**
     * Gives every node in a plan a distinct id. [node] derives ids from name, kind and duration, so a
     * circuit's repeated "Rest" or "Change" would otherwise share one — and the builder edits, moves
     * and deletes nodes by id, so editing one of them would edit them all.
     */
    private fun uniqueNodeIds(nodes: List<Node>, seen: MutableMap<String, Int> = mutableMapOf()): List<Node> =
        nodes.map { node ->
            val count = (seen[node.nodeId] ?: 0) + 1
            seen[node.nodeId] = count
            val id = if (count == 1) node.nodeId else "${node.nodeId}-$count"
            when (node) {
                is IntervalNode -> node.copy(
                    nodeId = id,
                    interval = if (count == 1) node.interval else node.interval.copy(id = "${node.interval.id}-$count"),
                )
                is RepeatGroup -> node.copy(nodeId = id, children = uniqueNodeIds(node.children, seen))
            }
        }

    /** One side of an alternating hold or exercise, announced as "Left side." / "Right side.". */
    private fun side(name: String, side: String, millis: Long, notes: String? = null): IntervalNode {
        val id = "$name-$side-$millis"
        return IntervalNode(
            nodeId = id,
            interval = IntervalSpec(
                id = id,
                name = name,
                kind = PhaseKind.WORK,
                durationMillis = millis,
                sideLabel = side,
                notes = notes,
                speechText = "$side side.",
            ),
        )
    }

    private fun plan(
        seed: String,
        name: String,
        type: WorkoutType,
        nodes: List<Node>,
        includeFinalRest: Boolean = true,
        equipment: String? = null,
        shuffle: Boolean = false,
        raceDistanceMeters: Double? = null,
        racePaceSecondsPerKm: Int? = null,
    ) = WorkoutPlan(
        id = id(seed),
        name = name,
        type = type,
        nodes = uniqueNodeIds(nodes),
        includeFinalRest = includeFinalRest,
        equipment = equipment,
        shuffleEager = shuffle,
        raceDistanceMeters = raceDistanceMeters,
        racePaceSecondsPerKm = racePaceSecondsPerKm,
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
                    name = "Running Intervals",
                    type = WorkoutType.RUN,
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
                // The running system's showcase: a paced 5K with race distance and a target pace,
                // so the player shows live speed, distance and what is left of the race.
                plan = plan(
                    seed = "race-5k",
                    name = "5K Race Run",
                    type = WorkoutType.RUN,
                    nodes = listOf(
                        node("Warm-up jog", PhaseKind.WARM_UP, 300_000, SoundCue.CHIME),
                        work(
                            "Race the 5K",
                            1_650_000,
                            notes = "Even effort — the race band shows speed, distance and what is left",
                        ),
                        cooldown(300_000),
                    ),
                    includeFinalRest = false,
                    raceDistanceMeters = 5_000.0,
                    racePaceSecondsPerKm = 330,
                ),
                tags = listOf("race", "5k", "road", "GPS"),
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
                // 10 s get ready + holds of 30/40/50/60 s + three 30 s rests (final rest omitted).
                tags = listOf("mobility", "progressive", "5 minutes"),
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
            Template(
                plan = plan(
                    seed = "e2mom-16",
                    name = "E2MOM 16",
                    type = WorkoutType.E2MOM,
                    nodes = listOf(
                        prepare(10_000),
                        RepeatGroup(
                            nodeId = "e2mom",
                            name = "8 blocks",
                            repeat = 8,
                            children = listOf(
                                work("Two-minute block", 120_000, "Finish your set, then rest until the next block starts"),
                            ),
                        ),
                    ),
                    includeFinalRest = false,
                    equipment = "Barbell, kettlebell or dumbbells",
                ),
                tags = listOf("E2MOM", "strength", "16 minutes"),
            ),
            Template(
                plan = plan(
                    seed = "norwegian-4x4",
                    name = "Norwegian 4x4",
                    type = WorkoutType.RUN,
                    nodes = listOf(
                        node("Easy warm-up", PhaseKind.WARM_UP, 600_000, SoundCue.CHIME),
                        RepeatGroup(
                            nodeId = "4x4",
                            name = "4 intervals",
                            repeat = 4,
                            children = listOf(
                                work("Hard", 240_000, "Hard but steady: you can say a word or two, not a sentence"),
                                rest("Easy jog", 180_000),
                            ),
                        ),
                        cooldown(120_000),
                    ),
                    includeFinalRest = false,
                ),
                tags = listOf("running", "VO2 max", "40 minutes"),
            ),
            Template(
                plan = plan(
                    seed = "sprint-repeats",
                    name = "Sprint Repeats",
                    type = WorkoutType.RUN,
                    nodes = listOf(
                        node("Jog warm-up", PhaseKind.WARM_UP, 480_000, SoundCue.CHIME),
                        RepeatGroup(
                            nodeId = "sprints",
                            name = "8 sprints",
                            repeat = 8,
                            children = listOf(
                                node(
                                    "Sprint",
                                    PhaseKind.WORK,
                                    30_000,
                                    SoundCue.WHISTLE,
                                    HapticCue.STRONG,
                                    notes = "Fast and relaxed, not all-out from the first step",
                                ),
                                rest("Walk back", 90_000),
                            ),
                        ),
                        cooldown(300_000),
                    ),
                    includeFinalRest = false,
                ),
                tags = listOf("running", "sprints", "29 minutes"),
            ),
            Template(
                plan = plan(
                    seed = "jump-rope",
                    name = "Jump Rope 30/30",
                    type = WorkoutType.HIIT,
                    nodes = listOf(
                        prepare(10_000),
                        RepeatGroup(
                            nodeId = "rope",
                            name = "10 rounds",
                            repeat = 10,
                            children = listOf(
                                work("Skip", 30_000, "Stay on the balls of your feet and turn the rope from the wrists"),
                                rest("Rest", 30_000),
                            ),
                        ),
                    ),
                    includeFinalRest = false,
                    equipment = "Jump rope",
                ),
                tags = listOf("HIIT", "cardio", "~10 minutes"),
            ),
            Template(
                plan = plan(
                    seed = "double-tabata",
                    name = "Double Tabata",
                    type = WorkoutType.TABATA,
                    nodes = listOf(
                        prepare(10_000),
                        RepeatGroup(
                            nodeId = "double-tabata",
                            name = "2 sets",
                            repeat = 2,
                            children = listOf(
                                RepeatGroup(
                                    nodeId = "double-tabata-rounds",
                                    name = "8 rounds",
                                    repeat = 8,
                                    children = listOf(work("Max effort", 20_000), rest("Recover", 10_000)),
                                ),
                            ),
                            restAfterGroupMillis = 60_000,
                            restAfterGroupName = "Reset",
                        ),
                    ),
                    includeFinalRest = false,
                ),
                tags = listOf("HIIT", "Tabata", "9 minutes"),
            ),
            Template(
                plan = plan(
                    seed = "lower-body-burner",
                    name = "Lower-Body Burner",
                    type = WorkoutType.HIIT,
                    nodes = listOf(
                        prepare(10_000),
                        RepeatGroup(
                            nodeId = "lower-body",
                            name = "3 rounds",
                            repeat = 3,
                            children = listOf(
                                work("Jump squats", 30_000, "Land softly, knees tracking over toes"),
                                rest("Rest", 15_000),
                                work("Reverse lunges", 30_000, "Alternate legs, front knee stacked over the ankle"),
                                rest("Rest", 15_000),
                                work("Wall sit", 45_000, "Thighs parallel to the floor, back flat on the wall"),
                                rest("Rest", 15_000),
                                work("Skater hops", 30_000, "Push off sideways and stick each landing"),
                                rest("Rest", 15_000),
                            ),
                            restAfterGroupMillis = 60_000,
                            restAfterGroupName = "Rest between rounds",
                        ),
                    ),
                    includeFinalRest = false,
                    equipment = "A wall",
                ),
                tags = listOf("HIIT", "legs", "~12 minutes"),
            ),
            Template(
                plan = plan(
                    seed = "core-crusher",
                    name = "Core Crusher",
                    type = WorkoutType.CIRCUIT,
                    nodes = listOf(
                        prepare(10_000),
                        RepeatGroup(
                            nodeId = "core-circuit",
                            name = "Circuit",
                            repeat = 2,
                            children = listOf(
                                work("Dead bug", 40_000, "Lower back pressed into the floor"),
                                rest("Rest", 20_000),
                                work("Bicycle crunches", 40_000, "Slow and controlled, elbow towards the opposite knee"),
                                rest("Rest", 20_000),
                                side("Side plank", "Left", 40_000, "Hips high, body in one line"),
                                rest("Rest", 20_000),
                                side("Side plank", "Right", 40_000, "Hips high, body in one line"),
                                rest("Rest", 20_000),
                                work("Mountain climbers", 40_000, "Shoulders over wrists, drive the knees"),
                                rest("Rest", 20_000),
                                work("Hollow hold", 40_000, "Ribs down; bend the knees to make it easier"),
                                rest("Rest", 20_000),
                            ),
                            restAfterGroupMillis = 60_000,
                            restAfterGroupName = "Rest between circuits",
                        ),
                    ),
                    includeFinalRest = false,
                    equipment = "Mat",
                ),
                tags = listOf("circuit", "core", "13 minutes"),
            ),
            Template(
                plan = plan(
                    seed = "shadowboxing",
                    name = "Shadowboxing Combos",
                    type = WorkoutType.BOXING,
                    nodes = listOf(
                        node("Stance up", PhaseKind.PREPARE, 15_000, SoundCue.BELL),
                        RepeatGroup(
                            nodeId = "shadow-rounds",
                            name = "6 rounds",
                            repeat = 6,
                            children = listOf(
                                node(
                                    "Round",
                                    PhaseKind.WORK,
                                    120_000,
                                    SoundCue.BELL,
                                    HapticCue.STRONG,
                                    notes = "Jab-cross, slip, hook — hands back to your chin after every punch",
                                ),
                            ),
                            restAfterGroupMillis = 30_000,
                            restAfterGroupName = "Shake out",
                        ),
                    ),
                    includeFinalRest = false,
                ),
                tags = listOf("boxing", "no equipment", "15 minutes"),
            ),
            Template(
                plan = plan(
                    seed = "morning-mobility",
                    name = "Morning Mobility",
                    type = WorkoutType.STRETCHING,
                    nodes = listOf(
                        prepare(5_000),
                        work("Cat-cow", 45_000, "Move slowly with your breath"),
                        node("Next", PhaseKind.TRANSITION, 5_000),
                        work("Hip circles", 40_000, "Big slow circles; switch direction halfway"),
                        node("Next", PhaseKind.TRANSITION, 5_000),
                        side("World's greatest stretch", "Left", 40_000, "Lunge, elbow to instep, then rotate open"),
                        node("Switch sides", PhaseKind.TRANSITION, 5_000),
                        side("World's greatest stretch", "Right", 40_000, "Lunge, elbow to instep, then rotate open"),
                        node("Next", PhaseKind.TRANSITION, 5_000),
                        work("Thoracic rotations", 40_000, "On all fours, hand behind your head; switch sides halfway"),
                        node("Next", PhaseKind.TRANSITION, 5_000),
                        work("Downward dog", 40_000, "Pedal the heels, keep the spine long"),
                        node("Next", PhaseKind.TRANSITION, 5_000),
                        work("Deep squat hold", 45_000, "Heels down, elbows pushing the knees out"),
                    ),
                    includeFinalRest = false,
                    equipment = "Mat",
                ),
                tags = listOf("stretching", "mobility", "6 minutes"),
            ),
            Template(
                plan = plan(
                    seed = "breathing-478",
                    name = "4-7-8 Breathing",
                    type = WorkoutType.BREATHING,
                    nodes = listOf(
                        RepeatGroup(
                            nodeId = "breath-478",
                            name = "6 cycles",
                            repeat = 6,
                            children = listOf(
                                IntervalNode("478-inhale", IntervalSpec("478-inhale", "Breathe in", PhaseKind.WORK, 4_000, sound = SoundCue.TICK, haptic = HapticCue.LIGHT, speechText = "In")),
                                IntervalNode("478-hold", IntervalSpec("478-hold", "Hold", PhaseKind.CUSTOM, 7_000, sound = SoundCue.NONE, haptic = HapticCue.NONE, speechText = "Hold")),
                                IntervalNode("478-exhale", IntervalSpec("478-exhale", "Breathe out", PhaseKind.REST, 8_000, sound = SoundCue.TICK, haptic = HapticCue.LIGHT, speechText = "Out")),
                            ),
                        ),
                    ),
                    // The last exhale belongs to the cycle; it is not a trailing rest to drop.
                    includeFinalRest = true,
                ),
                tags = listOf("breathing", "sleep", "2 minutes"),
            ),
            Template(
                plan = plan(
                    seed = "deep-work",
                    name = "Deep Work 50/10",
                    type = WorkoutType.FOCUS,
                    nodes = listOf(
                        RepeatGroup(
                            nodeId = "deep-work",
                            name = "3 blocks",
                            repeat = 3,
                            children = listOf(
                                work("Focus", 3_000_000, "One task, notifications off"),
                                rest("Break", 600_000),
                            ),
                        ),
                    ),
                    includeFinalRest = false,
                ),
                tags = listOf("focus", "work", "2 hours 50 minutes"),
            ),
        )
    }

    /** The workout offered at the end of onboarding and on an empty Home screen. */
    val examplePlan: WorkoutPlan get() = templates.first { it.plan.name == "HIIT Starter" }.plan

    /** Known duration for a template, computed by the engine (used for list previews and tests). */
    fun knownDuration(plan: WorkoutPlan): Long = TimelineExpander.knownDurationMillis(plan)
}
