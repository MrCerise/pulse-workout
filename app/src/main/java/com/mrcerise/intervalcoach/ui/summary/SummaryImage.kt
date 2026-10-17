package com.mrcerise.intervalcoach.ui.summary

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
import com.mrcerise.intervalcoach.R
import com.mrcerise.intervalcoach.session.SessionSummaryData
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

    // The card is drawn on the app's ink panel (PulseHeroCard's surface), so a shared session
    // image is recognisably the same product as the app that produced it. Values mirror
    // PulsePalette exactly; the renderer runs on a bitmap thread, so it reads constants.
    private val INK: Int = 0xFF131519.toInt()      // heroSurface, both themes
    private val WHITE: Int = 0xFFF4F6F8.toInt()    // textPrimary
    private val MUTED: Int = 0xFFA8B0BA.toInt()    // heroMuted
    private val VOLT: Int = 0xFFA3E635.toInt()     // brand / accent

    /** The volt at the hero wash's 16 % alpha, pre-multiplied into the ARGB word. */
    private val VOLT_WASH: Int = 0x28A3E635.toInt()
    private val SURFACE: Int = 0xFF1E2126.toInt()  // surfaceRaised: stat tiles
    private val BORDER: Int = 0xFF2E323A.toInt()   // hairlines
    private val SUCCESS: Int = 0xFF4CC38A.toInt()  // work / success
    private val WARNING: Int = 0xFFF5B544.toInt()  // prepare / warning

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
            /** Draws [value] at most [maxWidth] wide: long strings shrink, they never run into a neighbour. */
            fun text(value: String, x: Float, baseline: Float, size: Float, color: Int = WHITE, heavy: Boolean = false, width: Float = 936f) {
                val p = paint(size, color, heavy)
                // Size by measured glyph width, not by character count (emoji and French included).
                val measured = p.measureText(value)
                if (measured > width) p.textSize *= width / measured
                canvas.drawText(value, x, baseline, p)
            }
            /** Same shrink rule, but the string's right edge is pinned to [right] — one margin, no guessing. */
            fun textRight(value: String, right: Float, baseline: Float, size: Float, color: Int = WHITE, heavy: Boolean = false, width: Float = 936f) {
                val p = paint(size, color, heavy)
                val measured = p.measureText(value)
                if (measured > width) p.textSize *= width / measured
                canvas.drawText(value, right - p.measureText(value), baseline, p)
            }
            fun label(id: Int) = context.getString(id)
            canvas.drawColor(INK)
            // The same volt wash the hero panel draws: brand colour as light rising from the
            // bottom edge, not an orange gradient from a previous identity.
            val wash = Paint().apply {
                shader = LinearGradient(0f, HEIGHT.toFloat(), 0f, HEIGHT - 420f,
                    intArrayOf(VOLT_WASH, Color.TRANSPARENT), null, Shader.TileMode.CLAMP)
            }
            canvas.drawRect(0f, HEIGHT - 420f, WIDTH.toFloat(), HEIGHT.toFloat(), wash)
            canvas.drawRect(72f, 0f, 184f, 12f, paint(1f, VOLT))
            // The wordmark and its tagline are measured, not assumed: the tagline starts after the
            // wordmark's real width and shrinks to fit the gap before the brand mark, so no font
            // metrics can ever draw one string on top of the other.
            val wordmark = paint(44f, heavy = true)
            canvas.drawText("Interval Coach", 72f, 118f, wordmark)
            val taglineX = 72f + wordmark.measureText("Interval Coach") + 28f
            text("", taglineX, 117f, 23f, MUTED, width = (884f - taglineX).coerceAtLeast(80f))
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
            // The status line sits under the *rendered* title, not under an assumed one-liner:
            // a two-line name pushes it down instead of being overrun by it.
            val statusColor = if (summary.stoppedEarly) WARNING else SUCCESS
            val statusBaseline = 194f + title.height + 38f
            canvas.drawCircle(81f, statusBaseline - 10f, 7f, paint(1f, statusColor))
            text(label(if (summary.stoppedEarly) R.string.share_ended_early else R.string.share_completed), 104f, statusBaseline, 28f, statusColor, width = 904f)

            text(ShareMetrics.clock(summary.activeMillis), 64f, 604f, 176f, heavy = true)
            text(label(R.string.share_active_time), 76f, 666f, 27f, MUTED)
            val percentage = ShareMetrics.completion(summary.completedIntervals, summary.totalIntervals)
            text(label(R.string.share_completion), 72f, 757f, 28f, MUTED, width = 620f)
            textRight("$percentage%", 1008f, 757f, 34f, heavy = true, width = 188f)
            canvas.drawRoundRect(72f, 786f, 1008f, 800f, 7f, 7f, paint(1f, BORDER))
            if (percentage > 0) canvas.drawRoundRect(72f, 786f, 72f + 936f * percentage / 100f, 800f, 7f, 7f, paint(1f, VOLT))

            val route = summary.raceRoute
            var statsTop = 844f
            if (route.size >= 2) {
                canvas.drawRoundRect(72f, 816f, 1008f, 966f, 28f, 28f, paint(1f, SURFACE))
                val minLat = route.minOf { it.latitude }; val maxLat = route.maxOf { it.latitude }
                val minLng = route.minOf { it.longitude }; val maxLng = route.maxOf { it.longitude }
                val latSpan = (maxLat - minLat).takeIf { it > 0 } ?: 1.0
                val lngSpan = (maxLng - minLng).takeIf { it > 0 } ?: 1.0
                val routePath = android.graphics.Path().apply {
                    route.forEachIndexed { i, pt ->
                        val px = (104f + (pt.longitude - minLng) / lngSpan * 872f).toFloat()
                        val py = (934f - (pt.latitude - minLat) / latSpan * 86f).toFloat()
                        if (i == 0) moveTo(px, py) else lineTo(px, py)
                    }
                }
                canvas.drawPath(routePath, paint(1f, VOLT).apply { style = Paint.Style.STROKE; strokeWidth = 5f })
                statsTop = 940f
            }
            fun stat(x: Float, y: Float, name: String, value: String) {
                canvas.drawRoundRect(x, y, x + 456f, y + 144f, 28f, 28f, paint(1f, SURFACE))
                text(name, x + 28f, y + 45f, 24f, MUTED, width = 400f)
                text(value, x + 28f, y + 108f, 43f, heavy = true, width = 400f)
            }
            stat(72f, statsTop, label(R.string.share_intervals), "${summary.completedIntervals} / ${summary.totalIntervals}")
            stat(552f, statsTop, label(R.string.share_total_time), ShareMetrics.clock(summary.wallMillis))
            stat(72f, statsTop + if (route.size >= 2) 124f else 168f, label(R.string.share_skipped), summary.skippedIntervals.toString())
            stat(552f, statsTop + if (route.size >= 2) 124f else 168f, label(R.string.share_rounds), summary.roundsLogged.toString())
            canvas.drawLine(72f, 1210f, 1008f, 1210f, paint(1f, BORDER).apply { strokeWidth = 2f })
            text(label(R.string.share_recorded), 72f, 1269f, 23f, MUTED, width = 500f)
            if (summary.endedAt > 0) {
                val locale = context.resources.configuration.locales[0]
                val date = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)
                    .format(Instant.ofEpochMilli(summary.endedAt).atZone(ZoneId.systemDefault()))
                textRight(date, 1008f, 1269f, 23f, MUTED, width = 388f)
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
