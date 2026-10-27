package de.jw.einkaufsliste.feature.scan

import android.os.SystemClock
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage

/**
 * Reads EAN-13, EAN-8, UPC-A and UPC-E codes from camera frames with ML Kit (bundled
 * model, works offline). Once a code counts (see [BarcodeConfirmation]) scanning pauses
 * until [resume] and [onBarcode] is called on the main thread.
 */
class BarcodeAnalyzer(private val onBarcode: (String) -> Unit) : ImageAnalysis.Analyzer {
    private val scanner = BarcodeScanning.getClient(
        BarcodeScannerOptions.Builder()
            .setBarcodeFormats(
                Barcode.FORMAT_EAN_13,
                Barcode.FORMAT_EAN_8,
                Barcode.FORMAT_UPC_A,
                Barcode.FORMAT_UPC_E,
            )
            .build(),
    )

    @Volatile
    private var paused = false

    // Only touched on the main thread, where ML Kit delivers its results.
    private val confirmation = BarcodeConfirmation()

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(image: ImageProxy) {
        val mediaImage = image.image
        if (paused || mediaImage == null) {
            image.close()
            return
        }
        try {
            scanner.process(InputImage.fromMediaImage(mediaImage, image.imageInfo.rotationDegrees))
                .addOnSuccessListener { barcodes -> onFrame(barcodes.mapNotNull { it.rawValue }) }
                .addOnCompleteListener { image.close() }
        } catch (e: Exception) {
            // Scanner already closed (screen left while a frame was on its way).
            image.close()
        }
    }

    /** Stops looking at frames, e.g. while a result is shown. Main thread. */
    fun pause() {
        paused = true
        confirmation.reset()
    }

    /** Looks at frames again. Main thread. */
    fun resume() {
        confirmation.reset()
        confirmation.restartIgnoreTimer(SystemClock.elapsedRealtime())
        paused = false
    }

    /** Releases the ML Kit scanner; the analyzer must not be used afterwards. */
    fun close() {
        paused = true
        scanner.close()
    }

    private fun onFrame(codes: List<String>) {
        if (paused) return
        val code = confirmation.onFrame(codes, SystemClock.elapsedRealtime()) ?: return
        paused = true
        onBarcode(code)
    }
}
