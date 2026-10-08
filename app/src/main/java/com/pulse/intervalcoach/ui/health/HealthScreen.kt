package com.pulse.intervalcoach.ui.health

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pulse.intervalcoach.AppContainer
import com.pulse.intervalcoach.health.HC_REQUESTED_PERMISSIONS
import com.pulse.intervalcoach.health.FitAvailability
import com.pulse.intervalcoach.health.HcAvailability
import com.pulse.intervalcoach.ui.components.InfoBanner
import com.pulse.intervalcoach.ui.components.PrimaryActionButton
import com.pulse.intervalcoach.ui.components.PulseCard
import com.pulse.intervalcoach.ui.components.PulseIconButton
import com.pulse.intervalcoach.ui.components.PulseTone
import com.pulse.intervalcoach.ui.components.PulseTopBar
import com.pulse.intervalcoach.ui.components.SecondaryActionButton
import com.pulse.intervalcoach.ui.components.SectionHeader
import com.pulse.intervalcoach.ui.components.StatTile
import com.pulse.intervalcoach.ui.components.StatusBadge
import com.pulse.intervalcoach.ui.theme.LocalPulseColors
import com.pulse.intervalcoach.ui.theme.LocalPulseDimens
import com.pulse.intervalcoach.R
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

/**
 * Health setup & sync screen.
 *
 * Health Connect is the primary backend (read heart rate & steps, write workout sessions); Google
 * Fit is a best-effort secondary because Google has deprecated its on-device API as of 2026. Every
 * state on this screen is a real platform state — nothing is faked, and every failure says why.
 */
@Composable
fun HealthScreen(
    container: AppContainer,
    onBack: () -> Unit,
) {
    val colors = LocalPulseColors.current
    val dimens = LocalPulseDimens.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val healthConnectPermissionLauncher = rememberLauncherForActivityResult(
        contract = PermissionController.createRequestPermissionResultContract(),
    ) {
        container.health.refresh()
    }

    val availability by container.health.availability.collectAsStateWithLifecycle()
    val permissions by container.health.permissions.collectAsStateWithLifecycle()
    val fitAvailability by container.health.fitAvailability.collectAsStateWithLifecycle()
    val fitAuthorized by container.health.fitAuthorized.collectAsStateWithLifecycle()
    val snapshot by container.health.snapshot.collectAsStateWithLifecycle()

    var backfill by remember { mutableStateOf<String?>(null) }

    Scaffold(
        containerColor = colors.background,
        topBar = {
            PulseTopBar(
                title = "Health",
                subtitle = "Connect once — PULSE reads, writes, and says what happened",
                onBack = onBack,
                backDescription = stringResource(R.string.back),
                actions = {
                    PulseIconButton(
                        icon = Icons.Filled.Refresh,
                        contentDescription = "Refresh health status",
                        tooltip = "Refresh health status",
                        onClick = { container.health.refresh() },
                    )
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(
                start = dimens.pagePadding,
                end = dimens.pagePadding,
                top = dimens.cardGap,
                bottom = dimens.xxl,
            ),
            verticalArrangement = Arrangement.spacedBy(dimens.cardGap),
        ) {
            // --- What is connected right now ---
            item {
                PulseCard {
                    Column {
                        Text("Current connection", style = MaterialTheme.typography.labelMedium, color = colors.textSecondary)
                        Spacer(Modifier.height(LocalPulseDimens.current.s))
                        if (snapshot.source == null) {
                            Text(
                                "Nothing connected yet. Health Connect (below) is the recommended backend; " +
                                    "Google Fit is optional and deprecated by Google as of 2026.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.textPrimary,
                            )
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = colors.work, modifier = Modifier.padding(end = LocalPulseDimens.current.s))
                                Text("Reading through ${snapshot.source}", style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
                            }
                        }
                        if (snapshot.latestHeartRate != null) {
                            Spacer(Modifier.height(LocalPulseDimens.current.s))
                            Text(
                                "Latest heart rate: ${snapshot.latestHeartRate} bpm",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textSecondary,
                            )
                        }
                    }
                }
            }

            // --- Health Connect ---
            item { SectionHeader("Health Connect") }
            item {
                PulseCard {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "Platform: ${hcStatusText(availability)}",
                                style = MaterialTheme.typography.bodyLarge,
                                color = colors.textPrimary,
                                modifier = Modifier.weight(1f),
                            )
                            when (availability) {
                                HcAvailability.AVAILABLE -> StatusBadge("ready", PulseTone.SUCCESS)
                                else -> StatusBadge("unavailable", PulseTone.NEUTRAL)
                            }
                        }
                        when (availability) {
                            HcAvailability.NOT_INSTALLED -> {
                                Spacer(Modifier.height(LocalPulseDimens.current.s))
                                InfoBanner(
                                    "Health Connect isn't installed. It ships on Android 14+ and is available for older devices in the Play Store as the Google Health Connect app.",
                                )
                            }
                            HcAvailability.NOT_ENABLED -> {
                                Spacer(Modifier.height(LocalPulseDimens.current.s))
                                InfoBanner("Health Connect is installed but switched off in system settings. Enable it, then come back — no restart needed.")
                            }
                            HcAvailability.NO_PROVIDER -> {
                                Spacer(Modifier.height(LocalPulseDimens.current.s))
                                InfoBanner("No health app on this device offers Health Connect storage, so there is nothing to connect to.")
                            }
                            HcAvailability.NOT_SUPPORTED -> {
                                Spacer(Modifier.height(LocalPulseDimens.current.s))
                                InfoBanner("This device/Android version does not support Health Connect.")
                            }
                            HcAvailability.AVAILABLE -> Unit
                            HcAvailability.UNKNOWN -> {
                                Spacer(Modifier.height(LocalPulseDimens.current.s))
                                InfoBanner("Still checking what this device offers — tap the refresh icon when it settles.")
                            }
                        }
                        if (availability == HcAvailability.AVAILABLE) {
                            Spacer(Modifier.height(LocalPulseDimens.current.m))
                            permissionRow("Heart rate (read)", permissions.heartRateRead)
                            permissionRow("Steps (read)", permissions.stepsRead)
                            permissionRow("Workouts (write)", permissions.workoutWrite)
                            if (!permissions.anyReadGranted || !permissions.workoutWrite) {
                                Spacer(Modifier.height(LocalPulseDimens.current.m))
                                PrimaryActionButton(
                                    text = "Grant Health Connect access",
                                    onClick = {
                                        // The Health Connect system screen returns the new grants;
                                        // refresh these rows whenever it returns.
                                        healthConnectPermissionLauncher.launch(HC_REQUESTED_PERMISSIONS)
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }
                }
            }

            // --- Google Fit ---
            item { SectionHeader("Google Fit (secondary)") }
            item {
                PulseCard {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Person, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.padding(end = LocalPulseDimens.current.s))
                            Text(
                                "Deprecated by Google as of 2026",
                                style = MaterialTheme.typography.labelMedium,
                                color = colors.prepare,
                                modifier = Modifier.weight(1f),
                            )
                            when {
                                fitAuthorized -> StatusBadge("connected", PulseTone.SUCCESS)
                                fitAvailability == FitAvailability.AVAILABLE -> StatusBadge("optional", PulseTone.INFO)
                                else -> StatusBadge("unavailable", PulseTone.NEUTRAL)
                            }
                        }
                        Spacer(Modifier.height(LocalPulseDimens.current.s))
                        Text(
                            when (fitAvailability) {
                                FitAvailability.NOT_CONFIGURED ->
                                    "This build ships without a Google Fit OAuth client id (Google no longer accepts new Fit signups), so Fit cannot authorise. Health Connect above covers the same data."
                                FitAvailability.PLAY_SERVICES_MISSING ->
                                    "Google Play services (Fitness) is not available on this device."
                                FitAvailability.AVAILABLE ->
                                    if (fitAuthorized) {
                                        "Connected. Fit is used as a fallback whenever Health Connect has no data."
                                    } else {
                                        "Available but not authorised. It only adds a fallback data path — everything works without it."
                                    }
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textSecondary,
                        )
                        if (fitAvailability == FitAvailability.AVAILABLE && !fitAuthorized) {
                            Spacer(Modifier.height(LocalPulseDimens.current.m))
                            if (context is Activity) {
                                SecondaryActionButton(
                                    text = "Connect with Google",
                                    onClick = { container.health.requestFitAuthorization(context) },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }
                }
            }

            // --- Your data, as the backend sees it ---
            item { SectionHeader("Your data on the backend") }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(LocalPulseDimens.current.m)) {
                    StatTile(
                        "Steps today",
                        snapshot.stepsToday?.let { "${it} " } ?: "—",
                        modifier = Modifier.weight(1f),
                    )
                    StatTile(
                        "Latest HR",
                        snapshot.latestHeartRate?.let { "$it bpm" } ?: "—",
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            item {
                PulseCard {
                    Column {
                        Text(
                            "Last sync: " +
                                (if (snapshot.lastSyncAt > 0) {
                                    LocalDateTime.ofInstant(Instant.ofEpochMilli(snapshot.lastSyncAt), ZoneId.systemDefault())
                                        .format(DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm"))
                                } else "never"),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textSecondary,
                        )
                        Spacer(Modifier.height(LocalPulseDimens.current.m))
                        PrimaryActionButton(
                            text = "Backfill my last 7 days",
                            onClick = {
                                scope.launch {
                                    val sessions = container.sessions.history.first()
                                    val weekAgo = System.currentTimeMillis() - 7L * 24 * 3600 * 1000
                                    val candidates = sessions.filter { it.endedAt in weekAgo..System.currentTimeMillis() }
                                        .sortedByDescending { it.endedAt }
                                        .take(20)
                                    if (candidates.isEmpty()) {
                                        backfill = "No finished sessions in the last 7 days to backfill."
                                    } else {
                                        var ok = 0
                                        var failed = 0
                                        for (session in candidates) {
                                            val events = container.sessions.observeEvents(session.id).first()
                                            if (container.health.writeSession(session, events)) ok++ else failed++
                                        }
                                        backfill = "Backfilled $ok of ${candidates.size} sessions" +
                                            if (failed > 0) " — $failed failed on this device" else "" +
                                            ". They now appear in your health app's workout history."
                                        container.health.refresh()
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = snapshot.source != null,
                        )
                        if (snapshot.source == null) {
                            Spacer(Modifier.height(LocalPulseDimens.current.xs))
                            Text(
                                "Connect Health Connect or Google Fit above first — backfill writes your saved sessions to the connected app.",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textSecondary,
                            )
                        }
                        backfill?.let {
                            Spacer(Modifier.height(LocalPulseDimens.current.s))
                            Text(it, style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
                        }
                    }
                }
            }

            item {
                Text(
                    "Privacy: PULSE itself stores everything on this device and sends nothing to any cloud. " +
                        "Only the Google health apps you connect to receive the records you allow.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                )
            }
        }
    }
}

private fun hcStatusText(availability: HcAvailability): String = when (availability) {
    HcAvailability.AVAILABLE -> "available"
    HcAvailability.NOT_INSTALLED -> "not installed"
    HcAvailability.NOT_ENABLED -> "installed, switched off"
    HcAvailability.NO_PROVIDER -> "no provider app"
    HcAvailability.NOT_SUPPORTED -> "not supported"
    HcAvailability.UNKNOWN -> "checking…"
}

@Composable
private fun permissionRow(label: String, granted: Boolean) {
    val colors = LocalPulseColors.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = colors.textPrimary, modifier = Modifier.weight(1f))
        Text(
            if (granted) "granted" else "not granted",
            style = MaterialTheme.typography.labelLarge,
            color = if (granted) colors.work else colors.textSecondary,
        )
    }
}
