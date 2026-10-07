package com.pulse.intervalcoach.data

import com.pulse.engine.IntervalNode
import com.pulse.engine.IntervalSpec
import com.pulse.engine.Node
import com.pulse.engine.PhaseKind
import com.pulse.engine.RepeatGroup
import com.pulse.engine.SoundCue
import com.pulse.engine.WorkoutPlan
import com.pulse.engine.WorkoutType
import java.util.UUID

/**
 * Builds ad-hoc workouts (Quick Start on Home, quick builder, cloning).
 *
 * The factory is pure and deterministic so tests can check the exact durations the user will see.
 */
object QuickWorkoutFactory {

    fun build(
        workMillis: Long,
        restMillis: Long,
        rounds: Int,
        preparationMillis: Long,
        cooldownMillis: Long = 0L,
        includeFinalRest: Boolean = false,
        name: String = "Quick workout",
        type: WorkoutType = WorkoutType.HIIT,
        workName: String = "Work",
        restName: String = "Rest",
        id: String = UUID.randomUUID().toString(),
        createdAt: Long = System.currentTimeMillis(),
    ): WorkoutPlan {
        val nodes = mutableListOf<Node>()
        if (preparationMillis > 0) {
            nodes += IntervalNode(
                "prep",
                IntervalSpec("prep", "Get ready", PhaseKind.PREPARE, preparationMillis, sound = SoundCue.DOUBLE_BEEP),
            )
        }
        val children = buildList {
            add(IntervalNode("work", IntervalSpec("work", workName, PhaseKind.WORK, workMillis, sound = SoundCue.TICK)))
            if (restMillis > 0) {
                add(IntervalNode("rest", IntervalSpec("rest", restName, PhaseKind.REST, restMillis, sound = SoundCue.BEEP)))
            }
        }
        if (children.isNotEmpty()) {
            nodes += if (rounds <= 1) children else listOf(
                RepeatGroup(nodeId = "rounds", name = "$rounds rounds", repeat = rounds, children = children),
            )
        }
        if (cooldownMillis > 0) {
            nodes += IntervalNode(
                "cooldown",
                IntervalSpec("cooldown", "Cool-down", PhaseKind.COOLDOWN, cooldownMillis, sound = SoundCue.CHIME),
            )
        }
        return WorkoutPlan(
            id = id,
            name = name,
            description = "",
            type = type,
            nodes = nodes,
            includeFinalRest = includeFinalRest,
            createdAt = createdAt,
            updatedAt = createdAt,
        )
    }

    /**
     * Tabata factory used by the Tabata preset and the Quick Start shortcut.
     *
     * The defaults reproduce the textbook protocol exactly — eight rounds of 20 s / 10 s, final rest
     * included, no preparation interval — so the preset is 4:00 with the final rest and 3:50 without
     * it (acceptance case 1). Pass [preparationMillis] to add a "get ready" countdown, and
     * [includeFinalRest] to follow the user's preference.
     */
    fun tabata(
        id: String = UUID.randomUUID().toString(),
        preparationMillis: Long = 0L,
        includeFinalRest: Boolean = true,
    ) = build(
        workMillis = 20_000,
        restMillis = 10_000,
        rounds = 8,
        preparationMillis = preparationMillis,
        includeFinalRest = includeFinalRest,
        name = "Tabata",
        type = WorkoutType.TABATA,
        id = id,
    )
}
