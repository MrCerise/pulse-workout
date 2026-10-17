package com.mrcerise.intervalcoach.ui.components

import com.mrcerise.intervalcoach.R
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.mrcerise.intervalcoach.ui.theme.LocalPulseColors
import com.mrcerise.intervalcoach.ui.theme.LocalPulseShapes

/** Encodes [content] as a QR bitmap (high error correction so the centre logo never breaks scanning). */
fun workoutQrBitmap(content: String, sizePx: Int = 640): Bitmap {
    val hints = mapOf(EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.H, EncodeHintType.MARGIN to 1)
    val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    for (x in 0 until sizePx) for (y in 0 until sizePx) {
        bitmap.setPixel(x, y, if (matrix[x, y]) AndroidColor.BLACK else AndroidColor.WHITE)
    }
    return bitmap
}

/** QR code for a workout with the app logo in the middle. */
@Composable
fun WorkoutQrCode(content: String, modifier: Modifier = Modifier, size: androidx.compose.ui.unit.Dp = 220.dp) {
    val strings = LocalContext.current.resources
    val colors = LocalPulseColors.current
    val bitmap = remember(content) { runCatching { workoutQrBitmap(content) }.getOrNull() }
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        if (bitmap != null) {
            Image(bitmap.asImageBitmap(), contentDescription = strings.getString(R.string.workout_qr), modifier = Modifier.size(size))
            Surface(shape = LocalPulseShapes.current.control, color = androidx.compose.ui.graphics.Color.White) {
                Box(Modifier.size(52.dp).background(androidx.compose.ui.graphics.Color.White), contentAlignment = Alignment.Center) {
                    PulseLogo(size = 36.dp, tint = androidx.compose.ui.graphics.Color.Black)
                }
            }
        }
    }
}
