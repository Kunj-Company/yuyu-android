package com.twilighty.yuyu.feature.checkin.camera

import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.io.Closeable
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean

/**
 * CameraX analyzer running the bundled ML Kit model, QR codes only. It runs on
 * the analysis executor (never the main thread); with KEEP_ONLY_LATEST the next
 * frame arrives only after this one is closed, so at most one frame is in
 * flight. Frames are dropped unread while [isPaused] (a result is showing).
 * Decoded contents go to [onCode] and are never logged.
 */
internal class QrCodeAnalyzer(
    private val callbackExecutor: Executor,
    private val isPaused: () -> Boolean,
    private val onCode: (String) -> Unit,
) : ImageAnalysis.Analyzer, Closeable {
    private val scanner = BarcodeScanning.getClient(
        BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build(),
    )
    private val closed = AtomicBoolean(false)

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(image: ImageProxy) {
        val media = image.image
        if (closed.get() || isPaused() || media == null) {
            image.close()
            return
        }
        // This runs on the camera's analysis thread, where an uncaught exception kills the
        // app: a frame the reader can't take (or a reader closed mid-frame) is just skipped.
        try {
            val input = InputImage.fromMediaImage(media, image.imageInfo.rotationDegrees)
            scanner.process(input)
                .addOnSuccessListener(callbackExecutor) { barcodes ->
                    val value = barcodes.firstNotNullOfOrNull { code -> code.rawValue?.takeIf { it.isNotBlank() } }
                    if (value != null && !closed.get() && !isPaused()) onCode(value)
                }
                .addOnCompleteListener(callbackExecutor) { image.close() }
        } catch (e: Exception) {
            image.close()
        }
    }

    override fun close() {
        if (closed.compareAndSet(false, true)) scanner.close()
    }
}
