package io.reader.rebuild.design

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.reader.core.theme.ReaderDesignMetrics
import com.reader.core.theme.ReaderIconRegistry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal val LocalCoverBytes = staticCompositionLocalOf<(suspend (String) -> ByteArray?)?> { null }

@Composable
internal fun BookCover(url: String?, modifier: Modifier = Modifier, radius: Int = ReaderDesignMetrics.badgeRadius) {
    val colors = LocalReaderColors.current
    val fetch = LocalCoverBytes.current
    val cover by produceState<Bitmap?>(null, url, fetch) {
        value = null
        if (url != null) value = withContext(Dispatchers.IO) {
            try {
                val bytes = when {
                    url.length <= 4_000_000 && url.startsWith("data:image/") && ";base64," in url ->
                        Base64.decode(url.substringAfter(";base64,"), Base64.DEFAULT)
                    url.startsWith("https://") || url.startsWith("http://") -> fetch?.invoke(url)
                    else -> null
                }
                bytes?.takeIf { it.size <= 3_000_000 }?.let(::thumbnail)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) { null }
        }
    }
    Surface(modifier, shape = RoundedCornerShape(radius.dp), color = colors.coverPlaceholder,
        contentColor = colors.accent, border = BorderStroke(1.dp, colors.outline)) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            val bitmap = cover
            if (bitmap != null) Image(bitmap.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            else ReaderIcon(ReaderIconRegistry.shelf, null, tint = colors.accent)
        }
    }
}

private fun thumbnail(bytes: ByteArray): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 320) sample *= 2
    val options = BitmapFactory.Options().apply { inSampleSize = sample }
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
}
