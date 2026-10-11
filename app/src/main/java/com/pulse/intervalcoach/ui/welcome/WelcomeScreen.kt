package com.pulse.intervalcoach.ui.welcome

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
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pulse.engine.SoundCue
import com.pulse.engine.formatDuration
import com.pulse.intervalcoach.AppContainer
import com.pulse.intervalcoach.R
import com.pulse.intervalcoach.data.StarterWorkouts
import com.pulse.intervalcoach.ui.components.GhostActionButton
import com.pulse.intervalcoach.ui.components.OptionRow
import com.pulse.intervalcoach.ui.components.PrimaryActionButton
import com.pulse.intervalcoach.ui.components.ProgressRing
import com.pulse.intervalcoach.ui.components.PulseCard
import com.pulse.intervalcoach.ui.components.PulsePanel
import com.pulse.intervalcoach.ui.components.SecondaryActionButton
import com.pulse.intervalcoach.ui.theme.LocalPulseColors
import com.pulse.intervalcoach.ui.theme.LocalPulseDimens
import com.pulse.intervalcoach.ui.theme.LocalPulseShapes
import com.pulse.intervalcoach.ui.theme.PulseType
import com.pulse.intervalcoach.ui.theme.ThemeMode
import kotlinx.coroutines.launch

/**
 * Short, skippable welcome flow: theme choice, an optional voice sample, and a ready-to-start
 * example workout. Nothing here is mandatory — "Skip" finishes onboarding from any step.
 *
 * The screen is deliberately quiet: one column on the canvas, no chrome, the accent spent on a single
 * brand block and the step indicator. The step indicator is three bars rather than dots, because a
 * bar can encode position and progress at a glance and matches the timeline language used in the
 * player.
 */
@Composable
fun WelcomeScreen(
    container: AppContainer,
    onDone: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
) {
    var step by remember { mutableIntStateOf(0) }
    val colors = LocalPulseColors.current
    val dimens = LocalPulseDimens.current
    val shapes = LocalPulseShapes.current
    val scope = rememberCoroutineScope()
    val prefs by container.preferences.flow.collectAsStateWithLifecycle(initialValue = null)
    val example = remember { StarterWorkouts.templates.first { it.plan.name == "HIIT Starter" } }
    val exampleDuration = remember { StarterWorkouts.knownDuration(example.plan) }

    Box(
        Modifier
            .fillMaxSize()
            .background(colors.background)
            .safeDrawingPadding()
            .padding(horizontal = dimens.xl, vertical = dimens.l),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.Center,
        ) {
            when (step) {
                0 -> {
                    // The brand block: one flat accent square. The identity of the app is the mark
                    // plus the wordmark, not a gradient.
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(shapes.control)
                            .background(colors.accent),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Bolt,
                            contentDescription = null,
                            tint = colors.onAccent,
                            modifier = Modifier.size(26.dp),
                        )
                    }
                    Spacer(Modifier.height(dimens.xl))
                    Text(
                        text = stringResource(R.string.app_tagline),
                        style = MaterialTheme.typography.displaySmall,
                        color = colors.textPrimary,
                    )
                    Spacer(Modifier.height(dimens.m))
                    Text(
                        text = "Precise intervals, spoken coaching, live heart rate from Health Connect, " +
                            "and a timer that keeps running with the screen off. Everything is stored on " +
                            "this device — no account, no ads, no tracking.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textSecondary,
                    )
                    Spacer(Modifier.height(dimens.xl))
                    OptionRow(
                        label = stringResource(R.string.welcome_theme),
                        options = listOf(
                            ThemeMode.SYSTEM to "System",
                            ThemeMode.DARK to "Dark",
                            ThemeMode.LIGHT to "Light",
                            ThemeMode.TRUE_BLACK to "True black",
                        ),
                        selected = prefs?.themeMode ?: ThemeMode.SYSTEM,
                        onSelect = { mode -> scope.launch { container.preferences.setTheme(mode) } },
                    )
                }
                1 -> {
                    Text(
                        text = stringResource(R.string.welcome_voice),
                        style = MaterialTheme.typography.headlineMedium,
                        color = colors.textPrimary,
                    )
                    Spacer(Modifier.height(dimens.s))
                    Text(
                        text = "PULSE speaks interval changes using the voice installed on this device.",
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
                                text = "work",
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.textMuted,
                            )
                        }
                    }
                    Spacer(Modifier.height(dimens.l))
                    PulsePanel {
                        Text(
                            text = "Round one. Work, twenty seconds. Three, two, one, go.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.textSecondary,
                        )
                    }
                    Spacer(Modifier.height(dimens.l))
                    SecondaryActionButton(
                        text = "Play voice sample",
                        onClick = {
                            container.speech.initialise()
                            val spoken = container.speech.speakPreview("Round one. Work, twenty seconds. Three, two, one, go.")
                            if (!spoken) {
                                container.cueSoundPlayer.play(SoundCue.DOUBLE_BEEP)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(dimens.s))
                    Text(
                        text = "No speech engine? PULSE falls back to sound cues and shows you how to install an offline voice in Voice Studio.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textMuted,
                    )
                }
                else -> {
                    Text(
                        text = "Start with an example",
                        style = MaterialTheme.typography.headlineMedium,
                        color = colors.textPrimary,
                    )
                    Spacer(Modifier.height(dimens.s))
                    Text(
                        text = "A ready-made session is saved to your library, so the first workout is one tap away.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textSecondary,
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
                            Spacer(Modifier.height(dimens.xs))
                            Text(
                                text = example.plan.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textSecondary,
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
                        text = "Notifications show the live timer controls. If you decline, workouts still run — you simply lose the shade controls.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textMuted,
                    )
                }
            }
            Spacer(Modifier.height(dimens.massive))
        }

        Column(
            modifier = Modifier.align(Alignment.BottomCenter),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(dimens.chipGap),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.semantics { contentDescription = "Step ${step + 1} of 3" },
            ) {
                repeat(3) { index ->
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
                    0 -> "Continue"
                    1 -> "Continue"
                    else -> "Start using PULSE"
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
