package com.mrcerise.intervalcoach.ui.summary

import android.content.ClipData
import android.content.Intent
import kotlinx.coroutines.CancellationException
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pulse.engine.RaceMath
import com.pulse.engine.WorkoutPlan
import com.pulse.engine.formatDuration
import com.mrcerise.intervalcoach.AppContainer
import com.mrcerise.intervalcoach.R
import com.mrcerise.intervalcoach.health.HrStats
import com.mrcerise.intervalcoach.ui.components.InfoBanner
import com.mrcerise.intervalcoach.ui.components.MetricRow
import com.mrcerise.intervalcoach.ui.components.NeutralChip
import com.mrcerise.intervalcoach.ui.components.PrimaryActionButton
import com.mrcerise.intervalcoach.ui.components.PulseCard
import com.mrcerise.intervalcoach.ui.components.PulseDivider
import com.mrcerise.intervalcoach.ui.components.PulseHeroCard
import com.mrcerise.intervalcoach.ui.components.PulsePageHeader
import com.mrcerise.intervalcoach.ui.components.PulseScaffold
import com.mrcerise.intervalcoach.ui.components.PulseTextField
import com.mrcerise.intervalcoach.ui.components.RouteMap
import com.mrcerise.intervalcoach.ui.components.PulseTone
import com.mrcerise.intervalcoach.ui.components.SecondaryActionButton
import com.mrcerise.intervalcoach.ui.components.SectionHeader
import com.mrcerise.intervalcoach.ui.components.StatTile
import com.mrcerise.intervalcoach.ui.components.StatusBadge
import com.mrcerise.intervalcoach.ui.theme.LocalPulseColors
import com.mrcerise.intervalcoach.ui.theme.LocalPulseDimens
import com.mrcerise.intervalcoach.ui.theme.PulseType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Post-workout summary built from the saved session — real recorded numbers only.
 * When Health Connect is connected and the user allows sharing, the finished session is written
 * to it here (idempotent per session UUID), and real heart-rate stats are shown when available.
 */
@Composable
fun SessionSummaryScreen(
    container: AppContainer,
    onDone: () -> Unit,
    onRepeat: (WorkoutPlan) -> Unit,
) {
    val summary by container.sessionController.summary.collectAsStateWithLifecycle()
    val prefs by container.preferences.flow.collectAsStateWithLifecycle(initialValue = null)
    val colors = LocalPulseColors.current
    val dimens = LocalPulseDimens.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var notes by remember(summary?.sessionId) { mutableStateOf("") }
    var sharing by remember { mutableStateOf(false) }
    var shareError by remember { mutableStateOf(false) }

    PulseScaffold { padding ->
        val current = summary
        if (current == null) {
            Column(
                Modifier
                    .padding(padding)
                    .padding(dimens.pagePadding),
            ) {
                InfoBanner("No session to summarise")
                Spacer(Modifier.height(dimens.m))
                PrimaryActionButton(
                    text = "Back to Home",
                    onClick = onDone,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            return@PulseScaffold
        }

        val entity by container.sessions.observeSession(current.sessionId).collectAsStateWithLifecycle(initialValue = null)
        var hrStats by remember(current.sessionId) { mutableStateOf<HrStats?>(null) }
        var healthShared by remember(current.sessionId) { mutableStateOf<Boolean?>(null) }

        // Read HR stats for the session window and (if the user allows) write the session to health apps.
        LaunchedEffect(current.sessionId, entity, prefs?.healthSyncSessions) {
            val e = entity ?: return@LaunchedEffect
            hrStats = if (e.endedAt > e.startedAt) {
                container.health.heartRateStats(e.startedAt, e.endedAt)
            } else null
            if (prefs?.healthSyncSessions == true && healthShared == null) {
                val events = container.sessions.observeEvents(current.sessionId)
                    .first()
                healthShared = container.health.writeSession(e, events)
            }
        }

        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(
                    start = dimens.pagePadding,
                    end = dimens.pagePadding,
                    top = dimens.cardGap,
                    bottom = dimens.xxl,
                ),
            verticalArrangement = Arrangement.spacedBy(dimens.cardGap),
        ) {
            PulsePageHeader(
                title = current.planName,
                onBack = onDone,
                backLabel = "Close",
            )
            // --- Hero --------------------------------------------------------------------------
            // The one ink panel on the page, exactly as on Today: a finished session is a headline,
            // and a headline is set in ink rather than painted orange.
            PulseHeroCard {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StatusBadge(
                            text = if (current.stoppedEarly) "Ended early" else "Completed",
                            tone = if (current.stoppedEarly) PulseTone.WARNING else PulseTone.SUCCESS,
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            text = stringResource(R.string.summary_title),
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.heroMuted,
                        )
                    }
                    Spacer(Modifier.height(dimens.l))
                    Text(
                        text = formatDuration(current.activeMillis),
                        style = PulseType.NumericLarge,
                        color = colors.heroText,
                    )
                    Text(
                        text = "active time",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.heroMuted,
                    )
                }
            }
            PulseCard(contentPadding = PaddingValues(horizontal = dimens.l, vertical = dimens.s)) {
                Column(Modifier.fillMaxWidth()) {
                    MetricRow(
                        label = "Completion",
                        value = "${if (current.totalIntervals == 0) 0 else (current.completedIntervals * 100 / current.totalIntervals)}%",
                    )
                    PulseDivider()
                    MetricRow(
                        label = "Intervals completed",
                        value = "${current.completedIntervals} / ${current.totalIntervals}",
                    )
                    PulseDivider()
                    MetricRow(label = "Skipped", value = current.skippedIntervals.toString())
                    PulseDivider()
                    MetricRow(label = "Total time (incl. pauses)", value = formatDuration(current.wallMillis))
                    if (current.roundsLogged > 0) {
                        PulseDivider()
                        MetricRow(label = "Rounds", value = current.roundsLogged.toString())
                    }
                }
            }

            // --- Race info (the running system): what the run actually covered ---
            if (current.raceDistanceMeters != null) {
                SectionHeader(stringResource(R.string.builder_race_section))
                PulseCard(contentPadding = PaddingValues(horizontal = dimens.l, vertical = dimens.s)) {
                    Column(Modifier.fillMaxWidth()) {
                        val sourceLabel = if (current.raceSource == "GPS") {
                            stringResource(R.string.summary_race_source_gps)
                        } else {
                            stringResource(R.string.summary_race_source_estimated)
                        }
                        MetricRow(
                            label = stringResource(R.string.summary_race_distance) + " · " + sourceLabel,
                            value = "${RaceMath.formatDistanceKm(current.raceDistanceMeters!!)} km",
                        )
                        PulseDivider()
                        MetricRow(
                            label = stringResource(R.string.summary_race_avg_speed),
                            value = current.raceAverageSpeedMps?.let { "${RaceMath.formatSpeedKph(it)} km/h" } ?: "—",
                        )
                        val target = current.raceTargetDistanceMeters
                        if (target != null) {
                            PulseDivider()
                            MetricRow(
                                label = if (current.raceFinished) "Race distance" else "Race distance (ended early)",
                                value = "${RaceMath.formatDistanceKm(target)} km",
                            )
                        }
                    }
                }
            }

            if (current.raceRoute.size >= 2) {
                SectionHeader("Route")
                PulseCard { RouteMap(current.raceRoute) }
            }

            // --- Heart rate (real data only, when a connected app recorded it) ---
            val stats = hrStats
            if (stats != null && stats.sampleCount >= 4) {
                SectionHeader("Heart rate")
                PulseCard(contentPadding = PaddingValues(horizontal = dimens.l, vertical = dimens.s)) {
                    Column(Modifier.fillMaxWidth()) {
                        MetricRow("Average", "${stats.average} bpm", valueColor = colors.success)
                        PulseDivider()
                        MetricRow("Peak", "${stats.max} bpm", valueColor = colors.warning)
                    }
                }
            }

            // --- Health sync result (only ever shown for what actually happened) ---
            val shared = healthShared
            if (shared != null) {
                InfoBanner(
                    text = if (shared) {
                        "Synced to health apps"
                    } else {
                        "Health sync failed. Session saved on this device."
                    },
                    tone = if (shared) PulseTone.SUCCESS else PulseTone.NEUTRAL,
                )
            }

            SectionHeader("Notes")
            PulseTextField(
                value = notes,
                onValueChange = { notes = it },
                label = stringResource(R.string.summary_notes_hint),
                minLines = 3,
            )
            SecondaryActionButton(
                text = stringResource(R.string.summary_save_note),
                onClick = {
                    scope.launch {
                        container.sessions.updateNotes(current.sessionId, notes.ifBlank { null })
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )

            SectionHeader(stringResource(R.string.summary_share_title))
            if (shareError) InfoBanner(stringResource(R.string.summary_share_error))
            SecondaryActionButton(
                text = stringResource(if (sharing) R.string.summary_share_preparing else R.string.summary_share_image),
                enabled = !sharing,
                onClick = {
                    sharing = true
                    shareError = false
                    scope.launch {
                        // Image first; if rendering or the provider fails for any reason, fall back
                        // to a text share so sharing always works.
                        val imageUri = runCatching {
                            withContext(Dispatchers.IO) {
                                val bitmap = SummaryImage.render(context, current)
                                try { SummaryImage.writeToCache(context, bitmap) } finally { bitmap.recycle() }
                            }
                        }.getOrNull()
                        try {
                            if (imageUri != null) {
                                val send = Intent(Intent.ACTION_SEND).apply {
                                    type = "image/png"
                                    putExtra(Intent.EXTRA_STREAM, imageUri)
                                    putExtra(Intent.EXTRA_TITLE, current.planName)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                // Grant every chooser target read access explicitly (some OEM
                                // choosers drop the flag grant for the wrapped intent).
                                val chooser = Intent.createChooser(send, context.getString(R.string.summary_share_title)).apply {
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.packageManager.queryIntentActivities(send, 0).forEach { info ->
                                    runCatching { context.grantUriPermission(info.activityInfo.packageName, imageUri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                                }
                                context.startActivity(chooser)
                            } else {
                                val text = "${current.planName} — ${formatDuration(current.activeMillis)} active, " +
                                    "${current.completedIntervals}/${current.totalIntervals} intervals"
                                val send = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_SUBJECT, current.planName)
                                    putExtra(Intent.EXTRA_TEXT, text)
                                }
                                context.startActivity(Intent.createChooser(send, context.getString(R.string.summary_share_title)))
                            }
                        } catch (_: Exception) {
                            shareError = true
                        } finally {
                            sharing = false
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(dimens.s))
            PrimaryActionButton(
                text = "Repeat workout",
                onClick = { scope.launch { container.workouts.plan(current.workoutId)?.let(onRepeat) } },
                modifier = Modifier.fillMaxWidth(),
            )
            SecondaryActionButton(
                text = stringResource(R.string.summary_done),
                onClick = onDone,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
