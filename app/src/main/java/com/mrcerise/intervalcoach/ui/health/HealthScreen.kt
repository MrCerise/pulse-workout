package com.mrcerise.intervalcoach.ui.health

import android.content.Intent
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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mrcerise.intervalcoach.AppContainer
import com.mrcerise.intervalcoach.PrivacyPolicyActivity
import com.mrcerise.intervalcoach.R
import com.mrcerise.intervalcoach.health.HC_REQUESTED_PERMISSIONS
import com.mrcerise.intervalcoach.health.HcAvailability
import com.mrcerise.intervalcoach.ui.components.InfoBanner
import com.mrcerise.intervalcoach.ui.components.PrimaryActionButton
import com.mrcerise.intervalcoach.ui.components.PulseIconButton
import com.mrcerise.intervalcoach.ui.components.PulsePageHeader
import com.mrcerise.intervalcoach.ui.components.PulseScaffold
import com.mrcerise.intervalcoach.ui.components.PulseTone
import com.mrcerise.intervalcoach.ui.components.SectionHeader
import com.mrcerise.intervalcoach.ui.components.StatTile
import com.mrcerise.intervalcoach.ui.components.StatusBadge
import com.mrcerise.intervalcoach.ui.theme.LocalPulseColors
import com.mrcerise.intervalcoach.ui.theme.LocalPulseDimens
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Health setup & sync screen.
 *
 * Health Connect is the one backend: it reads heart rate and steps and receives finished workout
 * sessions. Every state on this screen is a real platform state — nothing is faked, and every
 * failure says why.
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
    val snapshot by container.health.snapshot.collectAsStateWithLifecycle()

    var backfill by remember { mutableStateOf<String?>(null) }

    PulseScaffold { padding ->
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
            item {
                PulsePageHeader(
                    title = "Health",
                    onBack = onBack,
                    trailing = {
                        PulseIconButton(
                            icon = Icons.Filled.Refresh,
                            contentDescription = "Refresh health status",
                            tooltip = "Refresh health status",
                            onClick = { container.health.refresh() },
                        )
                    },
                )
            }

            // --- What is connected right now ---
            item {
                Column {
                    Text("Current connection", style = MaterialTheme.typography.labelMedium, color = colors.textSecondary)
                    Spacer(Modifier.height(LocalPulseDimens.current.s))
                    if (snapshot.source == null) {
                        Text(
                            "Not connected",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.textPrimary,
                        )
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = colors.work, modifier = Modifier.padding(end = LocalPulseDimens.current.s))
                            Text("${snapshot.source}", style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
                        }
                    }

                }
            }

            item {
                Text(stringResource(R.string.privacy_health_notice), color = colors.textSecondary)
            }
            item {
                PrimaryActionButton(
                    text = stringResource(R.string.privacy_policy_title),
                    onClick = { context.startActivity(Intent(context, PrivacyPolicyActivity::class.java)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            // --- Health Connect ---
            item { SectionHeader("Health Connect") }
            item {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            hcStatusText(availability),
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
                                "Install Health Connect from Google Play.",
                            )
                        }
                        HcAvailability.NOT_ENABLED -> {
                            Spacer(Modifier.height(LocalPulseDimens.current.s))
                            InfoBanner("Enable Health Connect in Android Settings.")
                        }
                        HcAvailability.NO_PROVIDER -> {
                            Spacer(Modifier.height(LocalPulseDimens.current.s))
                            InfoBanner("No Health Connect provider found.")
                        }
                        HcAvailability.NOT_SUPPORTED -> {
                            Spacer(Modifier.height(LocalPulseDimens.current.s))
                            InfoBanner("Health Connect is not supported on this device.")
                        }
                        HcAvailability.AVAILABLE -> Unit
                        HcAvailability.UNKNOWN -> {
                            Spacer(Modifier.height(LocalPulseDimens.current.s))
                            InfoBanner("Checking availability…")
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

            // --- Your data, as the backend sees it ---
            item { SectionHeader("Your data") }
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
                        text = "Sync last 7 days",
                        onClick = {
                            scope.launch {
                                val sessions = container.sessions.history.first()
                                val weekAgo = System.currentTimeMillis() - 7L * 24 * 3600 * 1000
                                val candidates = sessions.filter { it.endedAt in weekAgo..System.currentTimeMillis() }
                                    .sortedByDescending { it.endedAt }
                                    .take(20)
                                if (candidates.isEmpty()) {
                                    backfill = "No sessions in the last 7 days."
                                } else {
                                    var ok = 0
                                    var failed = 0
                                    for (session in candidates) {
                                        val events = container.sessions.observeEvents(session.id).first()
                                        if (container.health.writeSession(session, events)) ok++ else failed++
                                    }
                                    backfill = "Synced $ok of ${candidates.size} sessions" +
                                        (if (failed > 0) " · $failed failed" else "")
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
                            "Connect a health app to sync.",
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

            item {
                Text(
                    "Only connected health apps receive the records you allow.",
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
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textPrimary,
            modifier = Modifier.weight(1f),
        )
        // Status is the shared badge, like every other state on this screen: same shape, same
        // measured tone pairings, no ad-hoc colour.
        StatusBadge(
            text = if (granted) "granted" else "not granted",
            tone = if (granted) PulseTone.SUCCESS else PulseTone.NEUTRAL,
        )
    }
}
