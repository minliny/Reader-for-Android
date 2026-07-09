package com.reader.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import androidx.compose.ui.platform.LocalContext
import com.reader.ui.motion.MotionController
import com.reader.ui.motion.MotionIds

/**
 * Coil 图片懒加载封装
 *
 * 契约来源：审计报告 A8（无图片懒加载）
 *
 * 三态：loading / error / success
 * 自动注册 motion.async.resultGuard
 */
@Composable
fun ReaderImage(
    model: Any?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop
) {
    val context = LocalContext.current
    val painter = rememberAsyncImagePainter(
        model = ImageRequest.Builder(context)
            .data(model)
            .crossfade(true)
            .build()
    )

    when (painter.state) {
        is AsyncImagePainter.State.Loading -> {
            Box(
                modifier = modifier.background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.08f)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(strokeWidth = 2.dp)
            }
        }
        is AsyncImagePainter.State.Error -> {
            Box(
                modifier = modifier.background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.08f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "加载失败",
                    fontWeight = FontWeight(500)
                )
            }
        }
        is AsyncImagePainter.State.Success -> {
            Image(
                painter = painter,
                contentDescription = contentDescription,
                modifier = modifier,
                contentScale = contentScale
            )
        }
        is AsyncImagePainter.State.Empty -> {
            Box(modifier = modifier.background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.08f)))
        }
    }
}
