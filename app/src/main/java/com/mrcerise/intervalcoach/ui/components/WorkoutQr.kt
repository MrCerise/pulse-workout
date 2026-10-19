package com.mrcerise.intervalcoach.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.WriterException
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.mrcerise.intervalcoach.R
import com.mrcerise.intervalcoach.ui.theme.LocalPulseShapes
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/** A rendered QR code: one pixel per module, quiet zone included. */
class WorkoutQrImage(val modules: Bitmap, val withLogo: Boolean)

object WorkoutQrCodes {
    /** Strongest correction first; the logo is only drawn where the correction can absorb it. */
    private val LEVELS = listOf(ErrorCorrectionLevel.H, ErrorCorrectionLevel.Q, ErrorCorrectionLevel.M, ErrorCorrectionLevel.L)

    /**
     * Encodes [content] at the strongest error-correction level that still fits, or returns null
     * when it does not fit a QR code at all. Rendering at the code's native size (one pixel per
     * module) and scaling without filtering keeps every module edge sharp, which is what lets a
     * phone camera read a dense code off another phone's screen.
     */
    fun render(content: String): WorkoutQrImage? {
        for (level in LEVELS) {
            val hints = mapOf(EncodeHintType.ERROR_CORRECTION to level, EncodeHintType.MARGIN to 4)
            val matrix = try {
                QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, 0, 0, hints)
            } catch (e: WriterException) {
                continue
            }
            return WorkoutQrImage(matrix.toBitmap(), withLogo = level == ErrorCorrectionLevel.H || level == ErrorCorrectionLevel.Q)
        }
        return null
    }

    private fun BitMatrix.toBitmap(): Bitmap {
        val pixels = IntArray(width * height) { index ->
            if (get(index % width, index / width)) AndroidColor.BLACK else AndroidColor.WHITE
        }
        return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
    }

    /** Called on IO: a share-sized PNG of the code, whole pixels per module, in the share cache. */
    fun writeToCache(context: Context, qr: WorkoutQrImage, targetPx: Int = 1024): android.net.Uri {
        val scale = (targetPx / qr.modules.width).coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(qr.modules, qr.modules.width * scale, qr.modules.height * scale, false)
        val dir = File(context.cacheDir, "shared")
        check(dir.isDirectory || dir.mkdirs()) { "Cannot create share directory" }
        val cutoff = System.currentTimeMillis() - 7 * 24 * 60 * 60 * 1000L
        dir.listFiles()?.filter { it.name.startsWith("pulse-workout-qr-") && it.extension == "png" && it.lastModified() < cutoff }
            ?.forEach { it.delete() }
        val file = File(dir, "pulse-workout-qr-${UUID.randomUUID()}.png")
        try {
            FileOutputStream(file).use { check(scaled.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            return FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        } catch (error: Exception) {
            file.delete()
            throw error
        } finally {
            if (scaled !== qr.modules) scaled.recycle()
        }
    }
}

/** A workout QR code on a white tile, with the app mark in the middle when the code allows it. */
@Composable
fun WorkoutQrCode(qr: WorkoutQrImage, modifier: Modifier = Modifier, size: Dp = 240.dp) {
    Box(
        modifier
            .size(size)
            .background(Color.White, LocalPulseShapes.current.control)
            .padding(4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            bitmap = qr.modules.asImageBitmap(),
            contentDescription = stringResource(R.string.workout_qr),
            modifier = Modifier.fillMaxSize(),
            filterQuality = FilterQuality.None,
        )
        if (qr.withLogo) {
            Box(
                Modifier
                    .size(size * 0.2f)
                    .background(Color.White, LocalPulseShapes.current.control),
                contentAlignment = Alignment.Center,
            ) {
                PulseLogo(size = size * 0.14f, tint = Color.Black)
            }
        }
    }
}
