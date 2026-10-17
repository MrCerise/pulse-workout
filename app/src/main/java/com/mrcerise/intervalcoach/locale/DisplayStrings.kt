package com.mrcerise.intervalcoach.locale

import android.content.res.Resources
import com.mrcerise.intervalcoach.R
import com.mrcerise.intervalcoach.data.WorkoutCategory
import com.mrcerise.intervalcoach.ui.theme.CategoryHue
import com.pulse.engine.HapticCue
import com.pulse.engine.PhaseKind
import com.pulse.engine.SoundCue
import com.pulse.engine.WorkoutType

/** Translate presentation labels without changing the identifiers stored in workouts. */
fun Resources.categoryTitle(category: WorkoutCategory): String = getString(when (category.type) {
    WorkoutType.HIIT -> R.string.category_hiit
    WorkoutType.TABATA -> R.string.category_tabata
    WorkoutType.CIRCUIT -> R.string.category_circuit
    WorkoutType.BOXING -> R.string.category_boxing
    WorkoutType.EMOM -> R.string.category_emom
    WorkoutType.E2MOM -> R.string.category_e2mom
    WorkoutType.AMRAP -> R.string.category_amrap
    WorkoutType.STRENGTH -> R.string.category_strength
    WorkoutType.RUN -> R.string.category_run
    WorkoutType.RUN_WALK -> R.string.category_run_walk
    WorkoutType.STRETCHING -> R.string.category_stretching
    WorkoutType.BREATHING -> R.string.category_breathing
    WorkoutType.FOCUS -> R.string.category_focus
    WorkoutType.CUSTOM_SEQUENCE -> R.string.category_custom_sequence
    WorkoutType.COMPOUND -> R.string.category_compound
    WorkoutType.STOPWATCH -> R.string.category_stopwatch
    WorkoutType.CUE_TIMER -> R.string.category_cue_timer
})

fun Resources.hueTitle(hue: CategoryHue): String = getString(when (hue) {
    CategoryHue.CONDITIONING -> R.string.hue_conditioning
    CategoryHue.CARDIO -> R.string.hue_cardio
    CategoryHue.STRENGTH -> R.string.hue_strength
    CategoryHue.MOBILITY -> R.string.hue_mobility
    CategoryHue.MIND -> R.string.hue_mind
    CategoryHue.TOOLS -> R.string.hue_tools
})

fun Resources.phaseLabel(kind: PhaseKind): String = getString(when (kind) {
    PhaseKind.PREPARE -> R.string.phase_prepare
    PhaseKind.WARM_UP -> R.string.phase_warm_up
    PhaseKind.WORK -> R.string.builder_work
    PhaseKind.REST -> R.string.builder_rest
    PhaseKind.TRANSITION -> R.string.phase_transition
    PhaseKind.COOLDOWN -> R.string.builder_cooldown
    PhaseKind.CUSTOM -> R.string.phase_custom
})

fun Resources.soundLabel(cue: SoundCue): String = getString(when (cue) {
    SoundCue.NONE -> R.string.sound_none
    SoundCue.TICK -> R.string.sound_tick
    SoundCue.BEEP -> R.string.sound_beep
    SoundCue.DOUBLE_BEEP -> R.string.sound_double_beep
    SoundCue.BELL -> R.string.sound_bell
    SoundCue.WHISTLE -> R.string.sound_whistle
    SoundCue.CHIME -> R.string.sound_chime
    SoundCue.BUZZ -> R.string.sound_buzz
})

fun Resources.hapticLabel(cue: HapticCue): String = getString(when (cue) {
    HapticCue.NONE -> R.string.haptic_none
    HapticCue.LIGHT -> R.string.haptic_light
    HapticCue.DOUBLE -> R.string.haptic_double
    HapticCue.STRONG -> R.string.haptic_strong
    HapticCue.PATTERN_321 -> R.string.haptic_pattern_321
})
