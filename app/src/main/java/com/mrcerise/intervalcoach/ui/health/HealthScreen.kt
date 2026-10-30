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
import androidx.compose.ui.platform.LocalResources
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
import com.mrcerise.intervalcoach.ui.components.StatusBadge
import com.mrcerise.intervalcoach.ui.theme.LocalPulseColors
import com.mrcerise.intervalcoach.ui.theme.LocalPulseDimens
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Optional Health Connect workout export setup and sync. */
@Composable
fun HealthScreen(
    container: AppContainer,
    onBack: () -> Unit,
) {
    val strings = LocalResources.current
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
                    title = strings.getString(R.string.nav_health),
                    onBack = onBack,
                    trailing = {
                        PulseIconButton(
                            icon = Icons.Filled.Refresh,
                            contentDescription = strings.getString(R.string.health_refresh),
                            tooltip = strings.getString(R.string.health_refresh),
                            onClick = { container.health.refresh() },
                        )
                    },
                )
            }

            // --- What is connected right now ---
            item {
                Column {
                    Text(strings.getString(R.string.health_current), style = MaterialTheme.typography.labelMedium, color = colors.textSecondary)
                    Spacer(Modifier.height(LocalPulseDimens.current.s))
                    if (snapshot.source == null) {
                        Text(
                            strings.getString(R.string.health_disconnected),
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
            item { SectionHeader(strings.getString(R.string.settings_health_title)) }
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
                            HcAvailability.AVAILABLE -> StatusBadge(strings.getString(R.string.health_ready), PulseTone.SUCCESS)
                            else -> StatusBadge(strings.getString(R.string.health_unavailable), PulseTone.NEUTRAL)
                        }
                    }
                    when (availability) {
                        HcAvailability.NOT_INSTALLED -> {
                            Spacer(Modifier.height(LocalPulseDimens.current.s))
                            InfoBanner(
                                strings.getString(R.string.health_install),
                            )
                        }
                        HcAvailability.NOT_ENABLED -> {
                            Spacer(Modifier.height(LocalPulseDimens.current.s))
                            InfoBanner(strings.getString(R.string.health_enable))
                        }
                        HcAvailability.NO_PROVIDER -> {
                            Spacer(Modifier.height(LocalPulseDimens.current.s))
                            InfoBanner(strings.getString(R.string.health_no_provider))
                        }
                        HcAvailability.NOT_SUPPORTED -> {
                            Spacer(Modifier.height(LocalPulseDimens.current.s))
                            InfoBanner(strings.getString(R.string.health_unsupported))
                        }
                        HcAvailability.AVAILABLE -> Unit
                        HcAvailability.UNKNOWN -> {
                            Spacer(Modifier.height(LocalPulseDimens.current.s))
                            InfoBanner(strings.getString(R.string.health_checking))
                        }
                    }
                    if (availability == HcAvailability.AVAILABLE) {
                        Spacer(Modifier.height(LocalPulseDimens.current.m))
                        permissionRow(strings.getString(R.string.health_write), permissions.workoutWrite)
                        if (!permissions.workoutWrite) {
                            Spacer(Modifier.height(LocalPulseDimens.current.m))
                            PrimaryActionButton(
                                text = strings.getString(R.string.health_grant),
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

            item {
                Column {
                    Text(
                        strings.getString(R.string.health_last_sync, if (snapshot.lastSyncAt > 0) {
                                LocalDateTime.ofInstant(Instant.ofEpochMilli(snapshot.lastSyncAt), ZoneId.systemDefault())
                                    .format(DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", strings.configuration.locales[0]))
                            } else strings.getString(R.string.health_never)),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary,
                    )
                    Spacer(Modifier.height(LocalPulseDimens.current.m))
                    PrimaryActionButton(
                        text = strings.getString(R.string.health_sync_week),
                        onClick = {
                            scope.launch {
                                val sessions = container.sessions.history.first()
                                val weekAgo = System.currentTimeMillis() - 7L * 24 * 3600 * 1000
                                val candidates = sessions.filter { it.endedAt in weekAgo..System.currentTimeMillis() }
                                    .sortedByDescending { it.endedAt }
                                    .take(20)
                                if (candidates.isEmpty()) {
                                    backfill = strings.getString(R.string.health_week_empty)
                                } else {
                                    var ok = 0
                                    var failed = 0
                                    for (session in candidates) {
                                        val events = container.sessions.observeEvents(session.id).first()
                                        if (container.health.writeSession(session, events)) ok++ else failed++
                                    }
                                    backfill = strings.getString(R.string.health_sync_result, ok, candidates.size, failed)
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
                            strings.getString(R.string.health_connect_hint),
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
                    strings.getString(R.string.health_access_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                )
            }
        }
    }
}

@Composable
private fun hcStatusText(availability: HcAvailability): String = when (availability) {
    HcAvailability.AVAILABLE -> stringResource(R.string.health_available)
    HcAvailability.NOT_INSTALLED -> stringResource(R.string.health_not_installed)
    HcAvailability.NOT_ENABLED -> stringResource(R.string.health_disabled)
    HcAvailability.NO_PROVIDER -> stringResource(R.string.health_no_app)
    HcAvailability.NOT_SUPPORTED -> stringResource(R.string.health_not_supported)
    HcAvailability.UNKNOWN -> stringResource(R.string.health_checking_short)
}

@Composable
private fun permissionRow(label: String, granted: Boolean) {
    val strings = LocalResources.current
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
            text = if (granted) strings.getString(R.string.health_granted) else strings.getString(R.string.health_not_granted),
            tone = if (granted) PulseTone.SUCCESS else PulseTone.NEUTRAL,
        )
    }
}
