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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pulse.engine.SoundCue
import com.pulse.engine.formatDuration
import com.pulse.intervalcoach.AppContainer
import com.pulse.intervalcoach.data.StarterWorkouts
import com.pulse.intervalcoach.ui.components.GradientActionButton
import com.pulse.intervalcoach.ui.components.OptionRow
import com.pulse.intervalcoach.ui.components.PrimaryActionButton
import com.pulse.intervalcoach.ui.components.ProgressRing
import com.pulse.intervalcoach.ui.components.SecondaryActionButton
import com.pulse.intervalcoach.ui.theme.LocalPulseColors
import com.pulse.intervalcoach.ui.theme.ThemeMode
import kotlinx.coroutines.launch
import androidx.compose.ui.res.stringResource
import com.pulse.intervalcoach.R

/**
 * Short, skippable welcome flow: theme choice, an optional voice sample, and a ready-to-start
 * example workout. Nothing here is mandatory — "Skip" finishes onboarding from any step.
 */
@Composable
fun WelcomeScreen(
    container: AppContainer,
    onDone: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
) {
    var step by remember { mutableIntStateOf(0) }
    val colors = LocalPulseColors.current
    val scope = rememberCoroutineScope()
    val prefs by container.preferences.flow.collectAsStateWithLifecycle(initialValue = null)
    val example = remember { StarterWorkouts.templates.first { it.plan.name == "HIIT Starter" } }
    val exampleDuration = remember { StarterWorkouts.knownDuration(example.plan) }

    Box(
        Modifier
            .fillMaxSize()
            .background(colors.background)
            .safeDrawingPadding()
            .padding(24.dp),
    ) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
            when (step) {
                0 -> {
                    Text(
                        stringResource(R.string.app_name).uppercase(),
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.work,
                        letterSpacing = MaterialTheme.typography.labelLarge.letterSpacing,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.app_tagline),
                        style = MaterialTheme.typography.displaySmall,
                        color = colors.textPrimary,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Precise intervals, spoken coaching, live heart rate from Health Connect, " +
                            "and a timer that keeps running with the screen off. Everything is stored on " +
                            "this device — no account, no ads, no tracking.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = colors.textSecondary,
                    )
                    Spacer(Modifier.height(28.dp))
                    Text(stringResource(R.string.welcome_theme), style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
                    Spacer(Modifier.height(8.dp))
                    OptionRow(
                        label = "",
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
                    Text(stringResource(R.string.welcome_voice), style = MaterialTheme.typography.headlineSmall, color = colors.textPrimary)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "PULSE speaks interval changes using the voice installed on this device.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textSecondary,
                    )
                    Spacer(Modifier.height(20.dp))
                    ProgressRing(
                        progress = 0.35f,
                        color = colors.work,
                        trackColor = colors.outline,
                        strokeWidth = 10.dp,
                        brush = colors.ringGradient(com.pulse.engine.PhaseKind.WORK),
                        modifier = Modifier.size(190.dp).align(Alignment.CenterHorizontally),
                    ) {
                        Text(
                            "20",
                            style = MaterialTheme.typography.displayMedium,
                            color = colors.textPrimary,
                            fontFeatureSettings = "tnum",
                        )
                    }
                    Spacer(Modifier.height(20.dp))
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
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "No speech engine? PULSE falls back to sound cues and shows you how to install an offline voice in Voice Studio.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary,
                    )
                }
                else -> {
                    Text("Start with an example", style = MaterialTheme.typography.headlineSmall, color = colors.textPrimary)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "${example.plan.name} · ${formatDuration(exampleDuration)}",
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.work,
                        fontFeatureSettings = "tnum",
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(example.plan.description, style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
                    Spacer(Modifier.height(20.dp))
                    SecondaryActionButton(
                        text = stringResource(R.string.welcome_notifications_allow),
                        onClick = onRequestNotificationPermission,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Notifications show the live timer controls. If you decline, workouts still run — you simply lose the shade controls.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary,
                    )
                }
            }
        }

        Column(Modifier.align(Alignment.BottomCenter), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.semantics { contentDescription = "Step ${step + 1} of 3" },
            ) {
                repeat(3) { index ->
                    Box(
                        Modifier
                            .size(8.dp)
                            .background(if (index == step) colors.work else colors.outline, CircleShape),
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            GradientActionButton(
                text = when (step) {
                    0 -> "Next"
                    1 -> "Next"
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
            TextButton(
                onClick = {
                    scope.launch {
                        container.preferences.setOnboardingComplete(true)
                        onDone()
                    }
                },
            ) { Text(stringResource(R.string.welcome_skip), textAlign = TextAlign.Center) }
        }
    }
}
