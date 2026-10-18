package com.twilighty.yuyu.feature.attendee.ticket

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/**
 * Encodes the ticket QR locally. The payload is the bare ticket token, exactly
 * like the website (`components/ticket/TicketQrPanel.tsx`, error correction M);
 * the door scanner's `parseCheckInPayload` accepts it as-is.
 */
object TicketQr {
    fun encode(payload: String): BitMatrix = QRCodeWriter().encode(
        payload,
        BarcodeFormat.QR_CODE,
        0,
        0,
        mapOf(
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
            EncodeHintType.MARGIN to 0,
            EncodeHintType.CHARACTER_SET to "UTF-8",
        ),
    )
}

/** Draws [payload] as a QR code in [dark] on [light] (keep it dark-on-white in both themes). */
@Composable
fun QrCode(payload: String, dark: Color, light: Color, modifier: Modifier = Modifier, contentDescription: String = "Ticket QR code") {
    val matrix = remember(payload) { TicketQr.encode(payload) }
    Canvas(modifier.aspectRatio(1f).semantics { this.contentDescription = contentDescription }) {
        drawRect(light)
        val cells = matrix.width
        val cell = size.minDimension / cells
        for (y in 0 until matrix.height) {
            var x = 0
            while (x < cells) {
                if (matrix[x, y]) {
                    // Merge horizontal runs into one rect: fewer draws, no hairline gaps.
                    var run = 1
                    while (x + run < cells && matrix[x + run, y]) run++
                    drawRect(dark, topLeft = Offset(x * cell, y * cell), size = Size(run * cell + 0.5f, cell + 0.5f))
                    x += run
                } else {
                    x++
                }
            }
        }
    }
}
