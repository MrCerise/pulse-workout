package com.pulse.intervalcoach.ui.summary

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pulse.engine.WorkoutPlan
import com.pulse.engine.formatDuration
import com.pulse.intervalcoach.AppContainer
import com.pulse.intervalcoach.health.HealthHub
import com.pulse.intervalcoach.ui.components.GradientActionButton
import com.pulse.intervalcoach.ui.components.InfoBanner
import com.pulse.intervalcoach.ui.components.NeutralChip
import com.pulse.intervalcoach.ui.components.PrimaryActionButton
import com.pulse.intervalcoach.ui.components.PulseCard
import com.pulse.intervalcoach.ui.components.SecondaryActionButton
import com.pulse.intervalcoach.ui.components.SectionHeader
import com.pulse.intervalcoach.ui.components.StatTile
import com.pulse.intervalcoach.ui.theme.LocalPulseColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import androidx.compose.ui.res.stringResource
import com.pulse.intervalcoach.R

/**
 * Post-workout summary built from the saved session — real recorded numbers only.
 * When Health Connect is connected and the user allows sharing, the finished session is written
 * to it here (idempotent per session UUID), and real heart-rate stats are shown when available.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionSummaryScreen(
    container: AppContainer,
    onDone: () -> Unit,
    onRepeat: (WorkoutPlan) -> Unit,
) {
    val summary by container.sessionController.summary.collectAsStateWithLifecycle()
    val prefs by container.preferences.flow.collectAsStateWithLifecycle(initialValue = null)
    val colors = LocalPulseColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var notes by remember(summary?.sessionId) { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.summary_title)) },
                navigationIcon = {
                    IconButton(onClick = onDone) { Icon(Icons.Filled.ArrowBack, contentDescription = "Close summary") }
                },
            )
        },
    ) { padding ->
        val current = summary
        if (current == null) {
            Column(Modifier.padding(padding)) {
                InfoBanner("No session to summarise — this screen appears right after a workout ends.")
                Spacer(Modifier.height(12.dp))
                PrimaryActionButton("Back to Home", onDone, Modifier.fillMaxWidth())
            }
            return@Scaffold
        }

        val entity by container.sessions.observeSession(current.sessionId).collectAsStateWithLifecycle(initialValue = null)
        var hrStats by remember { mutableStateOf<HealthHub.HrStats?>(null) }
        var healthShared by remember { mutableStateOf<Boolean?>(null) }

        // Read HR stats for the session window and (if the user allows) write the session to health apps.
        LaunchedEffect(current.sessionId, entity) {
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // --- Hero ---
            PulseCard {
                Column {
                    Text(current.planName, style = MaterialTheme.typography.titleLarge, color = colors.textPrimary)
                    Spacer(Modifier.height(4.dp))
                    NeutralChip(if (current.stoppedEarly) "Ended early" else "Completed")
                    Spacer(Modifier.height(14.dp))
                    Text(
                        formatDuration(current.activeMillis),
                        style = MaterialTheme.typography.displaySmall,
                        color = colors.work,
                        fontFeatureSettings = "tnum",
                    )
                    Text("active time", style = MaterialTheme.typography.labelMedium, color = colors.textSecondary)
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile(
                    "Completion",
                    "${if (current.totalIntervals == 0) 0 else (current.completedIntervals * 100 / current.totalIntervals)}%",
                    modifier = Modifier.weight(1f),
                )
                StatTile(
                    "Intervals",
                    "${current.completedIntervals}/${current.totalIntervals}",
                    modifier = Modifier.weight(1f),
                )
                StatTile("Skipped", current.skippedIntervals.toString(), modifier = Modifier.weight(1f))
            }
            if (current.roundsLogged > 0) {
                StatTile("Rounds", current.roundsLogged.toString(), modifier = Modifier.fillMaxWidth())
            }
            StatTile("Total time (incl. pauses)", formatDuration(current.wallMillis), modifier = Modifier.fillMaxWidth())

            // --- Heart rate (real data only, when a connected app recorded it) ---
            val stats = hrStats
            if (stats != null && stats.sampleCount >= 4) {
                SectionHeader("Heart rate")
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatTile("Average", "${stats.average} bpm", accent = colors.work, modifier = Modifier.weight(1f))
                    StatTile("Peak", "${stats.max} bpm", accent = colors.prepare, modifier = Modifier.weight(1f))
                }
            }

            // --- Health sync result (only ever shown for what actually happened) ---
            val shared = healthShared
            if (shared != null) {
                Text(
                    if (shared) "Shared to your health apps — it now appears in their workout history."
                    else "Could not share to health apps this time — the session is saved on this device.",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (shared) colors.work else colors.textSecondary,
                )
            }

            SectionHeader("Notes")
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text(stringResource(R.string.summary_notes_hint)) },
                modifier = Modifier.fillMaxWidth(),
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

            SectionHeader("Share")
            SecondaryActionButton(
                text = stringResource(R.string.summary_share_image),
                onClick = {
                    scope.launch {
                        val bitmap = withContext(Dispatchers.Default) { SummaryImage.render(current) }
                        val uri = SummaryImage.writeToCache(context, bitmap)
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "image/png"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(intent, "Share summary"))
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(8.dp))
            GradientActionButton(
                text = "Repeat workout",
                onClick = { scope.launch { container.workouts.plan(current.workoutId)?.let(onRepeat) } },
                modifier = Modifier.fillMaxWidth(),
            )
            SecondaryActionButton(text = stringResource(R.string.summary_done), onClick = onDone, modifier = Modifier.fillMaxWidth())
        }
    }
}

/**
 * Renders the shareable summary card with the Android canvas API so it stays sharp on any display
 * and needs no extra dependency. Colours match the app's design tokens.
 */
object SummaryImage {

    private const val WIDTH = 1080
    private const val HEIGHT = 1350

    fun render(summary: com.pulse.intervalcoach.session.SessionSummaryData): Bitmap {
        val bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(AndroidColor.parseColor("#0C0E13"))

        val title = Paint().apply {
            color = AndroidColor.parseColor("#F4F6FA")
            textSize = 64f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        val body = Paint().apply {
            color = AndroidColor.parseColor("#A9B0C0")
            textSize = 40f
            isAntiAlias = true
        }
        val big = Paint().apply {
            color = AndroidColor.parseColor("#BFF374")
            textSize = 190f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        val label = Paint().apply {
            color = AndroidColor.parseColor("#A9B0C0")
            textSize = 34f
            letterSpacing = 0.12f
            isAntiAlias = true
        }

        canvas.drawText("PULSE", 80f, 130f, label)
        canvas.drawText(summary.planName.take(28), 80f, 220f, title)
        canvas.drawText(
            if (summary.stoppedEarly) "Ended early" else "Completed",
            80f, 290f,
            Paint(body).apply { color = AndroidColor.parseColor(if (summary.stoppedEarly) "#FFD58E" else "#BFF374") },
        )

        canvas.drawText(formatClock(summary.activeMillis), 80f, 520f, big)
        canvas.drawText("ACTIVE TIME", 84f, 590f, label)

        val statsY = 760f
        drawStat(canvas, body, title, 80f, statsY, "Intervals", "${summary.completedIntervals}/${summary.totalIntervals}")
        drawStat(canvas, body, title, 560f, statsY, "Completion", "${if (summary.totalIntervals == 0) 0 else summary.completedIntervals * 100 / summary.totalIntervals}%")
        canvas.drawText("Recorded by PULSE — every number above is from this device's saved session", 80f, 1120f, body.apply { textSize = 30f })
        canvas.drawText(
            DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM).withLocale(Locale.getDefault())
                .format(Instant.now().atZone(ZoneId.systemDefault())),
            80f, 1180f,
            body.apply { textSize = 30f },
        )
        return bitmap
    }

    private fun drawStat(canvas: Canvas, label: Paint, value: Paint, x: Float, y: Float, name: String, text: String) {
        canvas.drawText(name.uppercase(Locale.getDefault()), x, y, label)
        canvas.drawText(text, x, y + 80f, value)
    }

    fun writeToCache(context: Context, bitmap: Bitmap): android.net.Uri {
        val dir = File(context.cacheDir, "shared").apply { mkdirs() }
        val file = File(dir, "pulse-session-${System.currentTimeMillis()}.png")
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    }

    private fun formatClock(millis: Long): String {
        val total = (millis.coerceAtLeast(0) + 999) / 1000
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
    }
}
