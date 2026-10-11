package com.pulse.intervalcoach.data

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.AllInclusive
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SportsMma
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Tune
import androidx.compose.ui.graphics.vector.ImageVector
import com.pulse.engine.WorkoutPlan
import com.pulse.engine.WorkoutType
import com.pulse.intervalcoach.ui.theme.CategoryHue

/**
 * The single definition of every workout category.
 *
 * Before this file, "category" meant two unrelated things that had drifted apart: a `WorkoutType`
 * enum in the engine that knew how to spell its own name, and a free-text `iconKey` string that was
 * hand-written per starter workout, stored in the database and silently ignored everywhere else.
 * Adding a category meant touching the enum, the starter list, the builder's dropdown and whichever
 * screen happened to have its own `when` over the type — and forgetting one of them produced a
 * workout whose card said one thing and whose filter chip said another.
 *
 * Now there is exactly one record per category, and it carries everything the interface needs to
 * present it:
 *
 *  - **identity** — [WorkoutCategory.key], [WorkoutCategory.title], [WorkoutCategory.tagline],
 *    [WorkoutCategory.description];
 *  - **presentation** — [WorkoutCategory.icon] and [WorkoutCategory.hue], so a category looks the
 *    same on a card, in a filter rail and in the player header;
 *  - **behaviour** — [CategoryCapabilities], which is what decides whether the builder offers
 *    rounds, a ladder or equipment for this kind of workout;
 *  - **starting point** — [CategoryDefaults], so "new workout" opens with timings that suit the
 *    category instead of with one global work/rest pair.
 *
 * The engine's [WorkoutType] stays as the persisted key: it is what is serialised into a plan and
 * stored in the database, and it deliberately knows nothing about names, icons or colours. Anything
 * a human reads comes from here.
 */

/** What the builder and the player are allowed to offer for a given category. */
data class CategoryCapabilities(
    /** The workout has a knowable duration (false for open-ended tools). */
    val timed: Boolean = true,
    /** Repeating blocks of work/rest make sense. */
    val supportsRounds: Boolean = true,
    /** A progressive ladder over the repetitions makes sense. */
    val supportsLadder: Boolean = false,
    /** Equipment is a meaningful field. */
    val supportsEquipment: Boolean = false,
    /** Effort intervals can be shuffled once at session start. */
    val supportsShuffle: Boolean = false,
    /** The workout can emit a cue every N milliseconds instead of having a structure. */
    val supportsPeriodicCue: Boolean = false,
    /** Structured intervals are described by a repeating block rather than a flat list. */
    val blockBased: Boolean = true,
)

/** The timings a new workout of this category starts from. */
data class CategoryDefaults(
    val workMillis: Long = 40_000L,
    val restMillis: Long = 20_000L,
    val rounds: Int = 8,
    val preparationMillis: Long = 10_000L,
    val cooldownMillis: Long = 0L,
    val includeFinalRest: Boolean = true,
    val workName: String = "Work",
    val restName: String = "Rest",
)

/** One workout category, defined once. */
data class WorkoutCategory(
    /** The persisted engine key. Also the stable string id used in filters and deep links. */
    val type: WorkoutType,
    /** Short human-facing name. */
    val title: String,
    /** One line that says what the category is for. */
    val tagline: String,
    /** A longer explanation, shown when the category is being chosen. */
    val description: String,
    /** Which colour family this category belongs to. */
    val hue: CategoryHue,
    /** The glyph that stands for this category everywhere. */
    val icon: ImageVector,
    /** Timings a new workout of this category opens with. */
    val defaults: CategoryDefaults,
    /** What the builder and player may offer. */
    val capabilities: CategoryCapabilities = CategoryCapabilities(),
    /** Extra words the library search should match against. */
    val keywords: List<String> = emptyList(),
) {
    /** Stable string id: safe to persist in a filter, a shortcut or an intent. */
    val key: String get() = type.name
}

/**
 * The registry. Nothing in the app hard-codes a category name, icon or colour — they ask here.
 *
 * Adding a category is now a one-file change: add the member to [WorkoutType] in the engine, add the
 * record below, and every screen, filter, builder default and icon follows automatically.
 */
object WorkoutCategories {

    private val hiit = WorkoutCategory(
        type = WorkoutType.HIIT,
        title = "HIIT",
        tagline = "Hard efforts, short recoveries",
        description = "High-intensity interval training: repeated bouts of near-maximal effort separated by recoveries short enough that the next round still hurts. The most efficient way to buy fitness with minutes.",
        hue = CategoryHue.CONDITIONING,
        icon = Icons.Filled.LocalFireDepartment,
        defaults = CategoryDefaults(
            workMillis = 40_000L,
            restMillis = 20_000L,
            rounds = 10,
            preparationMillis = 10_000L,
            cooldownMillis = 60_000L,
        ),
        capabilities = CategoryCapabilities(supportsLadder = true, supportsEquipment = true, supportsShuffle = true),
        keywords = listOf("interval", "cardio", "fat burn", "conditioning"),
    )

    private val tabata = WorkoutCategory(
        type = WorkoutType.TABATA,
        title = "Tabata",
        tagline = "20 seconds on, 10 off, eight rounds",
        description = "The textbook protocol: eight rounds of 20 seconds all-out against 10 seconds of recovery, four minutes in total. One movement, honest pacing, no negotiation.",
        hue = CategoryHue.CONDITIONING,
        icon = Icons.Filled.Bolt,
        defaults = CategoryDefaults(
            workMillis = 20_000L,
            restMillis = 10_000L,
            rounds = 8,
            preparationMillis = 0L,
            includeFinalRest = true,
        ),
        capabilities = CategoryCapabilities(supportsEquipment = true),
        keywords = listOf("4 minutes", "protocol", "20/10"),
    )

    private val circuit = WorkoutCategory(
        type = WorkoutType.CIRCUIT,
        title = "Circuit",
        tagline = "A loop of stations, repeated",
        description = "Several exercises performed back to back, then the whole loop repeated. Rounds rather than sets, so the rest is defined by the circuit instead of by how long you feel like standing around.",
        hue = CategoryHue.CONDITIONING,
        icon = Icons.Filled.Repeat,
        defaults = CategoryDefaults(
            workMillis = 45_000L,
            restMillis = 15_000L,
            rounds = 4,
            preparationMillis = 10_000L,
            cooldownMillis = 90_000L,
        ),
        capabilities = CategoryCapabilities(supportsLadder = true, supportsEquipment = true, supportsShuffle = true),
        keywords = listOf("stations", "loop", "bodyweight"),
    )

    private val boxing = WorkoutCategory(
        type = WorkoutType.BOXING,
        title = "Boxing / MMA",
        tagline = "Rounds, bells and a corner",
        description = "Timed rounds with a bell and a minute in the corner, the way a fight gym runs a class. Round lengths and rest are fixed by the clock, not by how you feel.",
        hue = CategoryHue.CONDITIONING,
        icon = Icons.Filled.SportsMma,
        defaults = CategoryDefaults(
            workMillis = 180_000L,
            restMillis = 60_000L,
            rounds = 6,
            preparationMillis = 10_000L,
            workName = "Round",
            restName = "Corner",
        ),
        capabilities = CategoryCapabilities(supportsEquipment = true),
        keywords = listOf("rounds", "bell", "fight", "combat", "mma"),
    )

    private val emom = WorkoutCategory(
        type = WorkoutType.EMOM,
        title = "EMOM",
        tagline = "Every minute, on the minute",
        description = "A set amount of work starts at the top of every minute; whatever is left of that minute is your rest. Miss the window and the clock does not wait for you.",
        hue = CategoryHue.CONDITIONING,
        icon = Icons.Filled.Timer,
        defaults = CategoryDefaults(
            workMillis = 40_000L,
            restMillis = 20_000L,
            rounds = 10,
            preparationMillis = 5_000L,
        ),
        capabilities = CategoryCapabilities(supportsLadder = true, supportsEquipment = true),
        keywords = listOf("every minute", "clock", "density"),
    )

    private val e2mom = WorkoutCategory(
        type = WorkoutType.E2MOM,
        title = "E2MOM",
        tagline = "Every two minutes, on the minute",
        description = "EMOM with twice the window: heavier work, longer recovery, more time under tension. Used for strength and skill work that does not fit inside sixty seconds.",
        hue = CategoryHue.CONDITIONING,
        icon = Icons.Filled.Redo,
        defaults = CategoryDefaults(
            workMillis = 60_000L,
            restMillis = 60_000L,
            rounds = 8,
            preparationMillis = 5_000L,
        ),
        capabilities = CategoryCapabilities(supportsLadder = true, supportsEquipment = true),
        keywords = listOf("two minutes", "heavy", "alternating"),
    )

    private val amrap = WorkoutCategory(
        type = WorkoutType.AMRAP,
        title = "AMRAP",
        tagline = "As many rounds as possible",
        description = "A fixed window and a loop you repeat as many times as you can inside it. The score is the round count, which is why the timer — not your judgement — decides when it ends.",
        hue = CategoryHue.CONDITIONING,
        icon = Icons.Filled.AllInclusive,
        defaults = CategoryDefaults(
            workMillis = 45_000L,
            restMillis = 15_000L,
            rounds = 12,
            preparationMillis = 10_000L,
        ),
        capabilities = CategoryCapabilities(supportsEquipment = true, supportsShuffle = true),
        keywords = listOf("as many reps", "score", "benchmark", "crossfit"),
    )

    private val strength = WorkoutCategory(
        type = WorkoutType.STRENGTH,
        title = "Strength",
        tagline = "Loaded work, timed sets",
        description = "Barbell, dumbbell or machine work with a timed effort and a prescribed rest, so a set is a set and the clock — not a conversation — ends the break.",
        hue = CategoryHue.STRENGTH,
        icon = Icons.Filled.FitnessCenter,
        defaults = CategoryDefaults(
            workMillis = 45_000L,
            restMillis = 90_000L,
            rounds = 5,
            preparationMillis = 0L,
            workName = "Set",
            restName = "Rest",
        ),
        capabilities = CategoryCapabilities(supportsLadder = true, supportsEquipment = true),
        keywords = listOf("weights", "hypertrophy", "sets", "barbell", "dumbbell"),
    )

    private val runWalk = WorkoutCategory(
        type = WorkoutType.RUN_WALK,
        title = "Run / Walk",
        tagline = "Effort and recovery, on the move",
        description = "Alternating running and walking intervals. The classic way back into running and the classic way to cover distance without paying for it the next day.",
        hue = CategoryHue.CARDIO,
        icon = Icons.Filled.DirectionsRun,
        defaults = CategoryDefaults(
            workMillis = 60_000L,
            restMillis = 30_000L,
            rounds = 10,
            preparationMillis = 0L,
            workName = "Run",
            restName = "Walk",
            includeFinalRest = true,
        ),
        capabilities = CategoryCapabilities(supportsLadder = true),
        keywords = listOf("running", "couch to 5k", "jeffing", "endurance"),
    )

    private val stretching = WorkoutCategory(
        type = WorkoutType.STRETCHING,
        title = "Stretching",
        tagline = "Hold, breathe, move on",
        description = "Timed holds with a short changeover between positions. The timer exists so you hold long enough for it to matter instead of counting in your head and giving up early.",
        hue = CategoryHue.MOBILITY,
        icon = Icons.Filled.AccessibilityNew,
        defaults = CategoryDefaults(
            workMillis = 60_000L,
            restMillis = 10_000L,
            rounds = 8,
            preparationMillis = 5_000L,
            workName = "Hold",
            restName = "Change",
        ),
        capabilities = CategoryCapabilities(supportsShuffle = true),
        keywords = listOf("mobility", "flexibility", "yoga", "recovery"),
    )

    private val breathing = WorkoutCategory(
        type = WorkoutType.BREATHING,
        title = "Breathing",
        tagline = "In, hold, out, hold",
        description = "Paced breathing patterns such as box breathing and 4-7-8. Long exhales, no equipment, and a voice that tells you when to switch without you opening your eyes.",
        hue = CategoryHue.MIND,
        icon = Icons.Filled.Air,
        defaults = CategoryDefaults(
            workMillis = 4_000L,
            restMillis = 4_000L,
            rounds = 10,
            preparationMillis = 5_000L,
            workName = "In",
            restName = "Out",
        ),
        capabilities = CategoryCapabilities(supportsShuffle = false),
        keywords = listOf("box breathing", "relax", "calm", "478", "wim hof"),
    )

    private val focus = WorkoutCategory(
        type = WorkoutType.FOCUS,
        title = "Focus / Pomodoro",
        tagline = "Work in blocks, break on time",
        description = "A focus block and a break, repeated. Pomodoro for people who would rather be told to stand up than decide to.",
        hue = CategoryHue.MIND,
        icon = Icons.Filled.Psychology,
        defaults = CategoryDefaults(
            workMillis = 25 * 60_000L,
            restMillis = 5 * 60_000L,
            rounds = 4,
            preparationMillis = 0L,
            workName = "Focus",
            restName = "Break",
        ),
        keywords = listOf("pomodoro", "study", "deep work", "productivity"),
    )

    private val customSequence = WorkoutCategory(
        type = WorkoutType.CUSTOM_SEQUENCE,
        title = "Custom sequence",
        tagline = "Your intervals, in your order",
        description = "A hand-built list of intervals with no protocol behind it. Use it when the workout already exists — written on a whiteboard, or pasted in from a text description.",
        hue = CategoryHue.TOOLS,
        icon = Icons.Filled.Tune,
        defaults = CategoryDefaults(
            workMillis = 60_000L,
            restMillis = 20_000L,
            rounds = 1,
            preparationMillis = 10_000L,
        ),
        capabilities = CategoryCapabilities(supportsEquipment = true, blockBased = false),
        keywords = listOf("manual", "freeform", "paste"),
    )

    private val compound = WorkoutCategory(
        type = WorkoutType.COMPOUND,
        title = "Compound",
        tagline = "Blocks nested inside blocks",
        description = "A workout made of other blocks: a warm-up, then a circuit repeated three times, then a finisher. The strongest structure the builder can express.",
        hue = CategoryHue.TOOLS,
        icon = Icons.Filled.Layers,
        defaults = CategoryDefaults(
            workMillis = 40_000L,
            restMillis = 20_000L,
            rounds = 3,
            preparationMillis = 10_000L,
            cooldownMillis = 120_000L,
        ),
        capabilities = CategoryCapabilities(supportsLadder = true, supportsEquipment = true, supportsShuffle = true),
        keywords = listOf("nested", "blocks", "advanced", "superset"),
    )

    private val stopwatch = WorkoutCategory(
        type = WorkoutType.STOPWATCH,
        title = "Stopwatch",
        tagline = "Count up until you stop",
        description = "No structure and no end time: a clock that runs until you stop it. For open sets, long rows, and anything where the duration is the result rather than the plan.",
        hue = CategoryHue.TOOLS,
        icon = Icons.Filled.Schedule,
        defaults = CategoryDefaults(
            workMillis = 0L,
            restMillis = 0L,
            rounds = 1,
            preparationMillis = 0L,
        ),
        capabilities = CategoryCapabilities(
            timed = false,
            supportsRounds = false,
            supportsLadder = false,
            blockBased = false,
        ),
        keywords = listOf("count up", "open", "timer"),
    )

    private val cueTimer = WorkoutCategory(
        type = WorkoutType.CUE_TIMER,
        title = "Repeating cue",
        tagline = "A nudge every N seconds",
        description = "Not a workout: a metronome for training. A cue fires every N seconds or minutes so you can do your own thing and still be told when to change.",
        hue = CategoryHue.TOOLS,
        icon = Icons.Filled.NotificationsActive,
        defaults = CategoryDefaults(
            workMillis = 0L,
            restMillis = 0L,
            rounds = 1,
            preparationMillis = 0L,
        ),
        capabilities = CategoryCapabilities(
            timed = false,
            supportsRounds = false,
            supportsLadder = false,
            supportsPeriodicCue = true,
            blockBased = false,
        ),
        keywords = listOf("metronome", "interval beep", "reminder"),
    )

    /** Every category, in the order the picker shows them: training styles first, tools last. */
    val all: List<WorkoutCategory> = listOf(
        hiit,
        tabata,
        circuit,
        boxing,
        emom,
        e2mom,
        amrap,
        strength,
        runWalk,
        stretching,
        breathing,
        focus,
        customSequence,
        compound,
        stopwatch,
        cueTimer,
    )

    /** Every category that produces a structured, runnable workout. */
    val trainable: List<WorkoutCategory> get() = all.filter { it.capabilities.timed }

    /** The six colour families, in the order the filter rail shows them. */
    val hues: List<CategoryHue> = listOf(
        CategoryHue.CONDITIONING,
        CategoryHue.CARDIO,
        CategoryHue.STRENGTH,
        CategoryHue.MOBILITY,
        CategoryHue.MIND,
        CategoryHue.TOOLS,
    )

    /** Human name for a colour family, used by filter chips and section headers. */
    fun hueTitle(hue: CategoryHue): String = when (hue) {
        CategoryHue.CONDITIONING -> "Conditioning"
        CategoryHue.CARDIO -> "Cardio"
        CategoryHue.STRENGTH -> "Strength"
        CategoryHue.MOBILITY -> "Mobility"
        CategoryHue.MIND -> "Mind"
        CategoryHue.TOOLS -> "Tools"
    }

    private val byType: Map<WorkoutType, WorkoutCategory> = all.associateBy { it.type }

    /** The record for an engine type. Never null: every [WorkoutType] is registered. */
    fun forType(type: WorkoutType): WorkoutCategory = byType[type] ?: customSequence

    /** Looks a category up by its persisted string id, falling back to a custom sequence. */
    fun byKey(key: String?): WorkoutCategory =
        byType.entries.firstOrNull { it.key.name == key }?.value ?: customSequence

    /**
     * Everything the library search should match for a stored row: its own words plus its
     * category's title and keywords, so searching "boxing" finds a custom sequence tagged Boxing and
     * searching "intervals" finds everything in the conditioning family.
     */
    fun searchTerms(summary: WorkoutSummary): List<String> =
        listOf(summary.name, summary.description, summary.equipment.orEmpty()) +
            summary.tags +
            byKey(summary.type).let { listOf(it.title) + it.keywords }

    /** Everything search should match for a plan: its own words plus its category's. */
    fun searchTerms(plan: WorkoutPlan): List<String> =
        listOf(plan.name, plan.description, plan.equipment.orEmpty()) +
            plan.tags +
            forType(plan.type).let { listOf(it.title) + it.keywords }
}

/** The category record for this engine type. */
val WorkoutType.category: WorkoutCategory
    get() = WorkoutCategories.forType(this)

/** The category record for this plan. */
val WorkoutPlan.category: WorkoutCategory
    get() = WorkoutCategories.forType(type)

/**
 * The category for a stored library row, whose type is persisted as a string.
 *
 * `WorkoutSummary` is the projection the library list reads, so it carries a `String` rather than an
 * enum; this is the one conversion point between the two.
 */
fun WorkoutSummary.categoryOrCustom(): WorkoutCategory = WorkoutCategories.byKey(type)
