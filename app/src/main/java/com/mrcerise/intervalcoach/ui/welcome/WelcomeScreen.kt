package com.mrcerise.intervalcoach.ui.welcome

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pulse.engine.SoundCue
import com.pulse.engine.formatDuration
import com.mrcerise.intervalcoach.AppContainer
import com.mrcerise.intervalcoach.R
import com.mrcerise.intervalcoach.ui.components.GhostActionButton
import com.mrcerise.intervalcoach.ui.components.PulseChoiceChip
import com.mrcerise.intervalcoach.ui.components.PulseSliderRow
import com.mrcerise.intervalcoach.ui.components.ThemePicker
import com.mrcerise.intervalcoach.ui.components.LanguagePicker
import com.mrcerise.intervalcoach.ui.components.PulseLogo
import com.mrcerise.intervalcoach.ui.components.PrimaryActionButton
import com.mrcerise.intervalcoach.ui.components.ProgressRing
import com.mrcerise.intervalcoach.ui.components.PulseCard
import com.mrcerise.intervalcoach.ui.components.PulsePanel
import com.mrcerise.intervalcoach.ui.components.SecondaryActionButton
import com.mrcerise.intervalcoach.ui.theme.LocalPulseColors
import com.mrcerise.intervalcoach.ui.theme.LocalPulseDimens
import com.mrcerise.intervalcoach.ui.theme.LocalPulseShapes
import com.mrcerise.intervalcoach.ui.theme.PulseType
import com.mrcerise.intervalcoach.ui.theme.ThemeMode
import com.mrcerise.intervalcoach.data.AppLanguage
import com.mrcerise.intervalcoach.locale.AppLocale
import com.pulse.library.StarterWorkouts
import com.mrcerise.intervalcoach.ui.home.WHO_WEEKLY_MINUTES
import kotlinx.coroutines.launch

/**
 * Short, skippable welcome flow: theme choice, an optional voice sample, and a ready-to-start
 * example workout. Nothing here is mandatory — "Skip" finishes onboarding from any step.
 *
 * Four steps: brand & appearance, the weekly goal, a voice sample, and a ready-to-start example.
 *
 * The screen is deliberately quiet: one column on the canvas, no chrome, the accent spent on a single
 * brand block and the step indicator. The step indicator is bars rather than dots, because a bar can
 * encode position and progress at a glance and matches the timeline language used in the player.
 */
@Composable
fun WelcomeScreen(
    container: AppContainer,
    onDone: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
) {
    val strings = LocalContext.current.resources
    var step by rememberSaveable { mutableIntStateOf(0) }
    val colors = LocalPulseColors.current
    val dimens = LocalPulseDimens.current
    val shapes = LocalPulseShapes.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val prefs by container.preferences.flow.collectAsStateWithLifecycle(initialValue = null)
    val example = remember { StarterWorkouts.templates.first { it.plan.name == "HIIT Starter" } }
    val exampleDuration = remember { StarterWorkouts.knownDuration(example.plan) }

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.background)
            .safeDrawingPadding()
            .padding(horizontal = dimens.xl, vertical = dimens.l),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.Center,
        ) {
            when (step) {
                0 -> {
                    PulseLogo(size = 64.dp)
                    Spacer(Modifier.height(dimens.xl))
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.displaySmall,
                        color = colors.textPrimary,
                    )
                    Spacer(Modifier.height(dimens.m))
                    Text(
                        text = stringResource(R.string.welcome_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textSecondary,
                    )
                    Spacer(Modifier.height(dimens.xl))
                    Text(stringResource(R.string.welcome_theme), style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(dimens.s))
                    ThemePicker(
                        selected = prefs?.themeMode ?: ThemeMode.SYSTEM,
                        onSelect = { mode -> scope.launch { container.preferences.setTheme(mode) } },
                    )
                    Spacer(Modifier.height(dimens.l))
                    Text(stringResource(R.string.welcome_language), style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(dimens.s))
                    LanguagePicker(
                        selected = prefs?.appLanguage ?: AppLanguage.SYSTEM,
                        onSelect = { language ->
                            // Apply the choice to the screens only. Handing it to the platform here
                            // (LocaleManager.setApplicationLocales) can trigger a configuration
                            // change that tears down the welcome tour before onboarding finishes;
                            // PulseApp re-applies the stored choice to the platform at the next start.
                            container.appScope.launch {
                                container.preferences.setAppLanguage(language)
                            }
                        },
                    )
                }
                1 -> {
                    // The goal step: the one personal choice the tour can make for the user, so the
                    // hero and the Goals page read "your target" from the first session onwards.
                    var goal by remember(prefs?.weeklyGoalMinutes) {
                        mutableIntStateOf(prefs?.weeklyGoalMinutes ?: WHO_WEEKLY_MINUTES)
                    }
                    Text(
                        text = strings.getString(R.string.welcome_goal_question),
                        style = MaterialTheme.typography.headlineMedium,
                        color = colors.textPrimary,
                    )
                    Spacer(Modifier.height(dimens.s))
                    Text(
                        text = strings.getString(R.string.welcome_goal_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textSecondary,
                    )
                    Spacer(Modifier.height(dimens.xl))
                    Row(horizontalArrangement = Arrangement.spacedBy(dimens.chipGap)) {
                        listOf(75, WHO_WEEKLY_MINUTES, 250).forEach { preset ->
                            PulseChoiceChip(
                                text = "$preset min",
                                selected = goal == preset,
                                onClick = { goal = preset },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                    Spacer(Modifier.height(dimens.l))
                    PulseSliderRow(
                        label = strings.getString(R.string.settings_weekly_goal),
                        value = goal.toFloat(),
                        onValueChange = { goal = (Math.round(it / 15f) * 15).coerceIn(30, 600) },
                        valueRange = 30f..600f,
                        valueLabel = "$goal min",
                    )
                    Spacer(Modifier.height(dimens.l))
                    PulsePanel {
                        Column {
                            Text(
                                text = strings.getString(R.string.welcome_week),
                                style = MaterialTheme.typography.labelMedium,
                                color = colors.textMuted,
                            )
                            Spacer(Modifier.height(dimens.xs))
                            Text(
                                text = strings.getQuantityString(R.plurals.welcome_week_summary, goal / 30, goal, goal / 30),
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.textSecondary,
                            )
                        }
                    }
                    // Written straight through: leaving the step (or the tour) persists it.
                    LaunchedEffect(goal) {
                        if (goal != prefs?.weeklyGoalMinutes) {
                            container.preferences.setWeeklyGoalMinutes(goal)
                        }
                    }
                }
                2 -> {
                    Text(
                        text = stringResource(R.string.welcome_voice),
                        style = MaterialTheme.typography.headlineMedium,
                        color = colors.textPrimary,
                    )
                    Spacer(Modifier.height(dimens.s))
                    Text(
                        text = stringResource(R.string.welcome_voice_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textSecondary,
                    )
                    Spacer(Modifier.height(dimens.xl))
                    ProgressRing(
                        progress = 0.35f,
                        color = colors.accent,
                        trackColor = colors.track,
                        strokeWidth = 6.dp,
                        modifier = Modifier
                            .size(168.dp)
                            .align(Alignment.CenterHorizontally),
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "20",
                                style = PulseType.NumericDisplay,
                                color = colors.textPrimary,
                            )
                            Text(
                                text = strings.getString(R.string.builder_work),
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.textMuted,
                            )
                        }
                    }
                    Spacer(Modifier.height(dimens.l))
                    PulsePanel {
                        Text(
                            text = strings.getString(R.string.voice_test_line),
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.textSecondary,
                        )
                    }
                    Spacer(Modifier.height(dimens.l))
                    SecondaryActionButton(
                        text = strings.getString(R.string.welcome_play_voice),
                        onClick = {
                            container.speech.initialise()
                            val spoken = container.speech.speakPreview(strings.getString(R.string.voice_test_line))
                            if (!spoken) {
                                container.cueSoundPlayer.play(SoundCue.DOUBLE_BEEP)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                else -> {
                    Text(
                        text = strings.getString(R.string.welcome_example),
                        style = MaterialTheme.typography.headlineMedium,
                        color = colors.textPrimary,
                    )
                    Spacer(Modifier.height(dimens.l))
                    PulseCard {
                        Column(verticalArrangement = Arrangement.spacedBy(dimens.xs)) {
                            Text(
                                text = example.plan.name,
                                style = MaterialTheme.typography.titleMedium,
                                color = colors.textPrimary,
                            )
                            Text(
                                text = formatDuration(exampleDuration),
                                style = PulseType.NumericSmall,
                                color = colors.textMuted,
                            )
                        }
                    }
                    Spacer(Modifier.height(dimens.l))
                    SecondaryActionButton(
                        text = stringResource(R.string.welcome_notifications_allow),
                        onClick = onRequestNotificationPermission,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(dimens.s))
                    Text(
                        text = stringResource(R.string.welcome_notifications),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textMuted,
                    )
                }
            }
            Spacer(Modifier.height(dimens.massive))
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(dimens.chipGap),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.semantics { contentDescription = strings.getString(R.string.welcome_step, step + 1, 4) },
            ) {
                repeat(4) { index ->
                    Box(
                        Modifier
                            .size(
                                width = if (index == step) dimens.xl else dimens.s,
                                height = dimens.xs,
                            )
                            .clip(shapes.chip)
                            .background(if (index <= step) colors.accent else colors.track),
                    )
                }
            }
            Spacer(Modifier.height(dimens.l))
            PrimaryActionButton(
                text = when (step) {
                    0 -> strings.getString(R.string.continue_label)
                    1 -> strings.getString(R.string.continue_label)
                    2 -> strings.getString(R.string.continue_label)
                    else -> stringResource(R.string.welcome_start)
                },
                onClick = {
                    if (step < 2) {
                        step++
                    } else {
                        scope.launch {
                            container.workouts.save(example.plan, tags = example.tags)
                            container.preferences.setOnboardingComplete(true)
                            onDone()
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            GhostActionButton(
                text = stringResource(R.string.welcome_skip),
                onClick = {
                    scope.launch {
                        container.preferences.setOnboardingComplete(true)
                        onDone()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
