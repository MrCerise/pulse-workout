package com.mrcerise.intervalcoach.journey

import com.pulse.engine.IntervalNode
import com.pulse.engine.IntervalSpec
import com.pulse.engine.PhaseKind
import com.pulse.engine.WorkoutPlan

/** Immutable content: ids and versions never depend on locale or the editable workout library. */
data class JourneyText(val en: String, val fr: String) {
    fun localized(french: Boolean) = if (french) fr else en
}

enum class JourneyLevel(val title: JourneyText) {
    BEGINNER(JourneyText("Beginner", "Débutant")),
    INTERMEDIATE(JourneyText("Intermediate", "Intermédiaire")),
    ADVANCED(JourneyText("Advanced", "Avancé"));

    fun next(): JourneyLevel? = entries.getOrNull(ordinal + 1)
}

data class JourneyExercise(
    val title: JourneyText,
    val guidance: JourneyText,
    val easier: JourneyText,
)

data class JourneyNode(
    val id: String,
    val level: JourneyLevel,
    val chapter: Int,
    val position: Int,
    val title: JourneyText,
    val exercises: List<JourneyExercise>,
    val workSeconds: Int,
    val restSeconds: Int,
    val rounds: Int,
) {
    val checkpoint get() = position == 5
    fun plan(french: Boolean): WorkoutPlan {
        fun interval(key: String, name: String, kind: PhaseKind, seconds: Int, notes: String? = null) =
            IntervalNode(key, IntervalSpec(id = key, name = name, kind = kind,
                durationMillis = seconds * 1000L, notes = notes))
        val nodes = buildList {
            add(interval("$id-warm", JourneyText("Easy march and shoulder rolls", "Marche douce et rotations des épaules").localized(french),
                PhaseKind.WARM_UP, 90))
            repeat(rounds) { round ->
                exercises.forEachIndexed { index, exercise ->
                    val key = "$id-$round-$index"
                    add(interval(key, exercise.title.localized(french), PhaseKind.WORK, workSeconds,
                        exercise.guidance.localized(french) + "\n" + exercise.easier.localized(french)))
                    add(interval("$key-rest", JourneyText("Rest — breathe comfortably", "Repos — respirez calmement").localized(french),
                        PhaseKind.REST, restSeconds))
                }
            }
            add(interval("$id-cool", JourneyText("Slow walking and relaxed breathing", "Marche lente et respiration calme").localized(french),
                PhaseKind.COOLDOWN, 90))
        }
        return WorkoutPlan(id = id, name = title.localized(french), nodes = nodes,
            revision = JourneyCatalogue.VERSION)
    }
}

object JourneyCatalogue {
    const val VERSION = 1
    val chapters = listOf(
        JourneyText("Find your rhythm", "Trouvez votre rythme"),
        JourneyText("Build your foundation", "Construisez vos bases"),
        JourneyText("Grow your capacity", "Développez vos capacités"),
        JourneyText("Move with confidence", "Bougez avec confiance"),
    )
    private fun exercise(en: String, fr: String, cue: String, cueFr: String, easy: String, easyFr: String) =
        JourneyExercise(JourneyText(en, fr), JourneyText(cue, cueFr), JourneyText(easy, easyFr))
    private val march = exercise("March in place", "Marche sur place", "Stand tall; keep a comfortable pace.", "Tenez-vous droit ; gardez une allure confortable.", "Easier: slow down and lift knees less.", "Plus facile : ralentissez et levez moins les genoux.")
    private val squat = exercise("Controlled squat", "Squat contrôlé", "Send hips back; use a comfortable depth.", "Reculez les hanches ; choisissez une profondeur confortable.", "Easier: reduce the depth.", "Plus facile : réduisez la profondeur.")
    private val wall = exercise("Wall push-up", "Pompes au mur", "Place palms on a stable wall; bend and extend elbows.", "Posez les mains sur un mur stable ; pliez puis tendez les coudes.", "Easier: stand closer to the wall.", "Plus facile : rapprochez-vous du mur.")
    private val step = exercise("Side steps", "Pas latéraux", "Step sideways with soft knees; stay controlled.", "Faites un pas de côté, genoux souples, en contrôlant le mouvement.", "Easier: take smaller steps.", "Plus facile : faites de plus petits pas.")
    private val hinge = exercise("Hip hinge", "Flexion des hanches", "Soften knees and move hips back with a long spine.", "Fléchissez légèrement les genoux et reculez les hanches, dos allongé.", "Easier: shorten the movement.", "Plus facile : réduisez le mouvement.")
    private val calf = exercise("Calf raises", "Élévations des mollets", "Rise onto toes slowly and lower with control.", "Montez lentement sur les pointes puis redescendez en contrôlant.", "Easier: use a wall for balance.", "Plus facile : appuyez-vous sur un mur.")
    private val bird = exercise("Standing opposite-arm reach", "Extension bras et jambe opposés debout", "Reach one arm forward and the opposite leg back; alternate.", "Tendez un bras devant et la jambe opposée derrière ; alternez.", "Easier: keep both feet on the floor.", "Plus facile : gardez les deux pieds au sol.")
    private val reach = exercise("Gentle overhead reach", "Étirement doux vers le haut", "Reach within a comfortable range and breathe steadily.", "Étirez-vous dans une amplitude confortable et respirez régulièrement.", "Easier: keep hands below shoulder height.", "Plus facile : gardez les mains sous les épaules.")
    private val routines = listOf(
        JourneyText("Full-body foundations", "Bases du corps entier") to listOf(march, squat, wall, hinge),
        JourneyText("Steady cardio", "Cardio régulier") to listOf(march, step, calf, march),
        JourneyText("Strength and balance", "Force et équilibre") to listOf(squat, wall, bird, calf),
        JourneyText("Mobility and control", "Mobilité et contrôle") to listOf(reach, hinge, step, bird),
        JourneyText("Mixed practice", "Entraînement varié") to listOf(step, squat, wall, bird),
        JourneyText("Chapter checkpoint", "Étape bilan") to listOf(march, squat, wall, hinge, bird),
    )

    val nodes: List<JourneyNode> = JourneyLevel.entries.flatMap { level ->
        (0..3).flatMap { chapter ->
            routines.mapIndexed { position, (title, exercises) ->
                JourneyNode(
                    id = "journey-v1-${level.name.lowercase()}-c${chapter + 1}-s${position + 1}",
                    level = level, chapter = chapter, position = position,
                    title = JourneyText("${title.en} · ${chapter + 1}", "${title.fr} · ${chapter + 1}"),
                    exercises = exercises,
                    workSeconds = 20 + level.ordinal * 10 + chapter * 5,
                    restSeconds = 25 - level.ordinal * 5,
                    rounds = 2 + level.ordinal + if (chapter >= 2) 1 else 0,
                )
            }
        }
    }
    const val FOUNDATION_SESSIONS = 24
    const val SESSIONS_PER_CHAPTER = 6
    private val generatedId = Regex("journey-generated-v1-(beginner|intermediate|advanced)-c([1-9][0-9]*)-s([1-6])")

    /** The original milestones stay unchanged, including their ids and saved progress. */
    fun path(level: JourneyLevel) = nodes.filter { it.level == level }

    fun node(id: String): JourneyNode? {
        nodes.firstOrNull { it.id == id }?.let { return it }
        val match = generatedId.matchEntire(id) ?: return null
        val level = JourneyLevel.entries.firstOrNull { it.name.lowercase() == match.groupValues[1] } ?: return null
        val chapterNumber = match.groupValues[2].toIntOrNull()?.takeIf { it >= 5 } ?: return null
        return generated(level, chapterNumber - 1, match.groupValues[3].toInt() - 1)
    }

    /** Resolve a single session on demand; the path has no predefined final chapter. */
    fun at(level: JourneyLevel, index: Int): JourneyNode {
        require(index >= 0)
        return if (index < FOUNDATION_SESSIONS) nodes[level.ordinal * FOUNDATION_SESSIONS + index]
        else generated(level, index / SESSIONS_PER_CHAPTER, index % SESSIONS_PER_CHAPTER)
    }

    fun belongsToLevel(id: String, level: JourneyLevel): Boolean =
        id.startsWith("journey-v1-${level.name.lowercase()}-") ||
            generatedId.matchEntire(id)?.groupValues?.get(1) == level.name.lowercase()

    fun chapterTitle(chapter: Int): JourneyText = if (chapter < chapters.size) chapters[chapter] else
        listOf(
            JourneyText("Keep building", "Continuez à progresser"),
            JourneyText("Find your balance", "Trouvez votre équilibre"),
            JourneyText("Stay consistent", "Gardez le rythme"),
            JourneyText("Explore your strength", "Explorez votre force"),
        )[(chapter - chapters.size) % 4]

    fun recommended(level: JourneyLevel, completed: Set<String>): JourneyNode {
        var index = 0
        while (true) {
            val candidate = at(level, index)
            if (candidate.id !in completed) return candidate
            index++
        }
    }

    /**
     * Versioned, reproducible local generation. Exercise groups maintain the session focus.
     * Load varies within the level's existing range rather than increasing forever.
     * java.util.Random and this explicit shuffle keep a saved id reproducible across devices.
     */
    private fun generated(level: JourneyLevel, chapter: Int, position: Int): JourneyNode {
        val seed = 0x50554C5345L xor (chapter.toLong() shl 32) xor
            (level.ordinal.toLong() shl 16) xor position.toLong()
        val random = java.util.Random(seed)
        val cardio = listOf(march, step)
        val legs = listOf(squat, hinge, calf)
        val upper = listOf(wall, reach)
        val balance = listOf(bird, calf, hinge)
        val groups = when (position) {
            1 -> listOf(cardio, cardio, balance, cardio)
            2 -> listOf(legs, listOf(wall), legs, balance)
            3 -> listOf(listOf(reach), listOf(hinge), listOf(step), listOf(bird))
            5 -> listOf(cardio, legs, listOf(wall), balance, legs)
            else -> listOf(cardio, legs, upper, balance)
        }
        val exercises = mutableListOf<JourneyExercise>()
        for (group in groups) {
            val available = group.filter { it !in exercises }.ifEmpty { group }
            exercises += available[random.nextInt(available.size)]
        }
        for (index in exercises.lastIndex downTo 1) {
            val other = random.nextInt(index + 1)
            val value = exercises[index]
            exercises[index] = exercises[other]
            exercises[other] = value
        }
        val title = routines[position].first
        return JourneyNode(
            id = "journey-generated-v1-${level.name.lowercase()}-c${chapter + 1}-s${position + 1}",
            level = level, chapter = chapter, position = position,
            title = JourneyText("${title.en} · ${chapter + 1}", "${title.fr} · ${chapter + 1}"),
            exercises = exercises.toList(),
            workSeconds = 35 + level.ordinal * 10 - random.nextInt(3) * 5,
            restSeconds = 25 - level.ordinal * 5 + random.nextInt(2) * 5,
            rounds = 2 + level.ordinal + random.nextInt(2),
        )
    }
}
