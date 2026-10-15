package dev.litetv.ui

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.toArgb
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter

val LiteTvColors = darkColorScheme(
    primary = Color(0xFFFF6B81),
    background = Color(0xFF0B0B0F),
    surface = Color(0xFF16161C),
    onBackground = Color(0xFFF2F2F5),
    onSurface = Color(0xFFF2F2F5),
    surfaceVariant = Color(0xFF222229),
    primaryContainer = Color(0xFF2A2A33),
)

@Composable
fun rememberQrBitmap(content: String, sizePx: Int = 420): ImageBitmap = remember(content, sizePx) {
    val hints = mapOf(EncodeHintType.MARGIN to 1)
    val matrix = MultiFormatWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
    val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val dark = Color.Black.toArgb()
    val light = Color.White.toArgb()
    for (x in 0 until sizePx) {
        for (y in 0 until sizePx) {
            bmp.setPixel(x, y, if (matrix[x, y]) dark else light)
        }
    }
    bmp.asImageBitmap()
}

fun fmt(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, (s % 3600) / 60, s % 60)
    else "%d:%02d".format(s / 60, s % 60)
}
