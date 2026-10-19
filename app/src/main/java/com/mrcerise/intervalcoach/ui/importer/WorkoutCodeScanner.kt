package com.mrcerise.intervalcoach.ui.importer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.google.android.gms.common.moduleinstall.ModuleInstall
import com.google.android.gms.common.moduleinstall.ModuleInstallRequest
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.LuminanceSource
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.ReaderException
import com.google.zxing.common.GlobalHistogramBinarizer
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader

/**
 * The two ways a workout QR code gets into the app.
 *
 *  - **Camera**: Google Play services' code scanner. Play services owns the camera screen and hands
 *    back only the decoded text, so Interval Coach itself never requests the camera permission.
 *  - **Image**: a screenshot or photo picked by the user, decoded on the device with ZXing — the
 *    same library that draws the codes — with no Play services and no network at all.
 *
 * Both return raw text; deciding whether it is a workout is [com.pulse.sync.ShareCode]'s job.
 */
object WorkoutCodeScanner {

    /** Longest side an image is decoded at: plenty for a QR code, bounded for memory. */
    private const val MAX_IMAGE_SIDE = 2048

    /**
     * Opens the camera scanner. [onScanned] receives the code's text; a dismissed scanner calls
     * nothing; [onUnavailable] means Play services cannot provide a scanner on this device.
     */
    fun scan(context: Context, onScanned: (String) -> Unit, onUnavailable: () -> Unit) {
        val options = GmsBarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .enableAutoZoom()
            .build()
        try {
            val scanner = GmsBarcodeScanning.getClient(context, options)
            // The scanner UI is an on-demand module; asking for it here starts the download on a
            // device that does not have it yet, so a second attempt usually succeeds.
            runCatching {
                ModuleInstall.getClient(context).installModules(
                    ModuleInstallRequest.newBuilder().addApi(scanner).build(),
                )
            }
            scanner.startScan()
                .addOnSuccessListener { barcode ->
                    val text = barcode.rawValue
                    if (text != null) onScanned(text) else onUnavailable()
                }
                .addOnFailureListener { onUnavailable() }
        } catch (e: RuntimeException) {
            onUnavailable()
        }
    }

    /** Reads a QR code out of the picked image, or null when there is none. Call off the main thread. */
    fun decodeImage(context: Context, uri: Uri): String? {
        val bitmap = loadBounded(context, uri) ?: return null
        try {
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            val source = RGBLuminanceSource(bitmap.width, bitmap.height, pixels)
            // Light-on-dark codes (a dark-theme screenshot) only read once inverted.
            return decode(source) ?: decode(source.invert())
        } finally {
            bitmap.recycle()
        }
    }

    private fun decode(source: LuminanceSource): String? {
        val hints = mapOf(
            DecodeHintType.TRY_HARDER to true,
            DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
        )
        for (binary in listOf(BinaryBitmap(HybridBinarizer(source)), BinaryBitmap(GlobalHistogramBinarizer(source)))) {
            try {
                return QRCodeReader().decode(binary, hints).text
            } catch (e: ReaderException) {
                // Try the next binarizer.
            }
        }
        return null
    }

    private fun loadBounded(context: Context, uri: Uri): Bitmap? {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        // A bounds-only decode always returns null; the size lands in `bounds`.
        val stream = resolver.openInputStream(uri) ?: return null
        stream.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > MAX_IMAGE_SIDE) sample *= 2
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
    }
}
