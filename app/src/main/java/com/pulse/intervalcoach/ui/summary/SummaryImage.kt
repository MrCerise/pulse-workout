package com.pulse.intervalcoach.ui.summary

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import androidx.core.content.FileProvider
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import com.pulse.intervalcoach.R
import com.pulse.intervalcoach.session.SessionSummaryData
import java.io.File
import java.io.FileOutputStream
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.UUID

/** Social-ready 4:5 card. No private notes or health data are included in an exported image. */
object SummaryImage {
    const val WIDTH = 1080
    const val HEIGHT = 1350
    private const val INK = 0xFF17181B.toInt()
    private const val WHITE = 0xFFF5F6F7.toInt()
    private const val MUTED = 0xFFAEB4BB.toInt()
    private const val ORANGE = 0xFFF38020.toInt()

    fun render(context: Context, summary: SessionSummaryData): Bitmap {
        val bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(bitmap)
            val regular = ResourcesCompat.getFont(context, R.font.inter_regular) ?: Typeface.DEFAULT
            val bold = ResourcesCompat.getFont(context, R.font.inter_bold) ?: Typeface.DEFAULT_BOLD
            fun paint(size: Float, color: Int = WHITE, heavy: Boolean = false) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = size
                this.color = color
                typeface = if (heavy) bold else regular
            }
            fun text(value: String, x: Float, baseline: Float, size: Float, color: Int = WHITE, heavy: Boolean = false, width: Float = 936f) {
                val p = paint(size, color, heavy)
                // Size by measured glyph width, not by character count (emoji and French included).
                val measured = p.measureText(value)
                if (measured > width) p.textSize *= width / measured
                canvas.drawText(value, x, baseline, p)
            }
            fun label(id: Int) = context.getString(id)
            canvas.drawColor(INK)
            val gradient = Paint().apply {
                shader = LinearGradient(0f, 0f, WIDTH.toFloat(), 700f,
                    intArrayOf(0xFF34271F.toInt(), INK), null, Shader.TileMode.CLAMP)
            }
            canvas.drawRect(0f, 0f, WIDTH.toFloat(), 700f, gradient)
            canvas.drawRect(72f, 0f, 184f, 12f, paint(1f, ORANGE))
            text("PULSE", 72f, 118f, 44f, heavy = true)
            text("INTERVAL COACH", 248f, 117f, 23f, MUTED)
            ContextCompat.getDrawable(context, R.drawable.ic_pulse)?.mutate()?.apply {
                setTint(WHITE)
                setBounds(916, 56, 1008, 148)
                draw(canvas)
            }
            val title = StaticLayout.Builder.obtain(summary.planName, 0, summary.planName.length, paint(60f, heavy = true), 936)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL).setIncludePad(false)
                .setMaxLines(2).setEllipsize(TextUtils.TruncateAt.END).setLineSpacing(6f, 1f).build()
            canvas.save()
            canvas.translate(72f, 194f)
            title.draw(canvas)
            canvas.restore()
            val statusColor = if (summary.stoppedEarly) 0xFFF5B544.toInt() else 0xFF4CC38A.toInt()
            canvas.drawCircle(81f, 377f, 7f, paint(1f, statusColor))
            text(label(if (summary.stoppedEarly) R.string.share_ended_early else R.string.share_completed), 104f, 387f, 28f, statusColor)

            text(ShareMetrics.clock(summary.activeMillis), 64f, 604f, 176f, heavy = true)
            text(label(R.string.share_active_time), 76f, 666f, 27f, MUTED)
            val percentage = ShareMetrics.completion(summary.completedIntervals, summary.totalIntervals)
            text(label(R.string.share_completion), 72f, 757f, 28f, MUTED)
            text("$percentage%", 820f, 757f, 34f, heavy = true, width = 188f)
            canvas.drawRoundRect(72f, 786f, 1008f, 800f, 7f, 7f, paint(1f, 0xFF34373D.toInt()))
            if (percentage > 0) canvas.drawRoundRect(72f, 786f, 72f + 936f * percentage / 100f, 800f, 7f, 7f, paint(1f, ORANGE))

            fun stat(x: Float, y: Float, name: String, value: String) {
                canvas.drawRoundRect(x, y, x + 456f, y + 144f, 24f, 24f, paint(1f, 0xFF222428.toInt()))
                text(name, x + 28f, y + 45f, 24f, MUTED, width = 400f)
                text(value, x + 28f, y + 108f, 43f, heavy = true, width = 400f)
            }
            stat(72f, 844f, label(R.string.share_intervals), "${summary.completedIntervals} / ${summary.totalIntervals}")
            stat(552f, 844f, label(R.string.share_total_time), ShareMetrics.clock(summary.wallMillis))
            stat(72f, 1012f, label(R.string.share_skipped), summary.skippedIntervals.toString())
            stat(552f, 1012f, label(R.string.share_rounds), summary.roundsLogged.toString())
            canvas.drawLine(72f, 1210f, 1008f, 1210f, paint(1f, 0xFF34373D.toInt()).apply { strokeWidth = 2f })
            text(label(R.string.share_recorded), 72f, 1269f, 23f, MUTED, width = 450f)
            if (summary.endedAt > 0) {
                val locale = context.resources.configuration.locales[0]
                val date = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)
                    .format(Instant.ofEpochMilli(summary.endedAt).atZone(ZoneId.systemDefault()))
                text(date, 620f, 1269f, 23f, MUTED, width = 388f)
            }
            return bitmap
        } catch (error: Throwable) {
            bitmap.recycle()
            throw error
        }
    }

    /** Called on IO; keep recent shares alive for receivers, prune only our own stale exports. */
    fun writeToCache(context: Context, bitmap: Bitmap): android.net.Uri {
        val dir = File(context.cacheDir, "shared")
        check(dir.isDirectory || dir.mkdirs()) { "Cannot create share directory" }
        val cutoff = System.currentTimeMillis() - 7 * 24 * 60 * 60 * 1000L
        dir.listFiles()?.filter { it.name.startsWith("pulse-session-") && it.extension == "png" && it.lastModified() < cutoff }
            ?.forEach { it.delete() }
        val file = File(dir, "pulse-session-${UUID.randomUUID()}.png")
        try {
            FileOutputStream(file).use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            return FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        } catch (error: Exception) {
            file.delete()
            throw error
        }
    }
}

/** Overflow-safe formatting kept separate from Android drawing for fast JVM coverage. */
internal object ShareMetrics {
    fun completion(completed: Int, total: Int): Int =
        if (total <= 0) 0 else (completed.toLong().coerceAtLeast(0) * 100 / total).coerceIn(0, 100).toInt()

    fun clock(millis: Long): String {
        val safe = millis.coerceAtLeast(0)
        val total = safe / 1000 + if (safe % 1000 > 0) 1 else 0
        val seconds = (total % 60).toString().padStart(2, '0')
        val minutes = total / 60 % 60
        val hours = total / 3600
        return if (hours > 0) "$hours:${minutes.toString().padStart(2, '0')}:$seconds" else "$minutes:$seconds"
    }
}
