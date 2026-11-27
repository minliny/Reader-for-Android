package io.reader.rebuild.design

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp
import com.reader.core.theme.ReaderDesignMetrics
import com.reader.core.theme.ReaderTypographyFamily
import com.reader.core.theme.ReaderTypographyMetric

internal object ReaderFonts {
    const val sansPath = "reader_design/fonts/NotoSansSC-VF.otf"
    const val serifPath = "reader_design/fonts/NotoSerifSC-Regular.ttf"
}

@Composable
internal fun readerTextStyle(metric: ReaderTypographyMetric): TextStyle {
    val assets = LocalContext.current.assets
    val font = remember(assets, metric.family, metric.weight) {
        val weight = FontWeight(metric.weight)
        when (metric.family) {
            ReaderTypographyFamily.UI -> Font(
                ReaderFonts.sansPath, assets, weight,
                variationSettings = FontVariation.Settings(FontVariation.weight(metric.weight)),
            )
            ReaderTypographyFamily.LITERARY -> Font(ReaderFonts.serifPath, assets)
        }
    }
    return remember(font, metric) {
        TextStyle(
            fontFamily = FontFamily(font),
            fontWeight = FontWeight(metric.weight),
            fontSize = metric.fontSize.sp,
            lineHeight = metric.lineHeight.sp,
            platformStyle = PlatformTextStyle(includeFontPadding = false),
            lineHeightStyle = LineHeightStyle(alignment = LineHeightStyle.Alignment.Center, trim = LineHeightStyle.Trim.None),
        )
    }
}

@Composable
internal fun readerMaterialTypography(): Typography = with(ReaderDesignMetrics) {
    Typography(
        displayLarge = readerTextStyle(appTitle),
        displayMedium = readerTextStyle(appTitle),
        displaySmall = readerTextStyle(pageTitle),
        headlineLarge = readerTextStyle(appTitle),
        headlineMedium = readerTextStyle(pageTitle),
        headlineSmall = readerTextStyle(pageTitle),
        titleLarge = readerTextStyle(pageTitle),
        titleMedium = readerTextStyle(sectionHeading),
        titleSmall = readerTextStyle(sectionHeading),
        bodyLarge = readerTextStyle(rowLabel),
        bodyMedium = readerTextStyle(rowDetail),
        bodySmall = readerTextStyle(rowDetail),
        labelLarge = readerTextStyle(rowLabel),
        labelMedium = readerTextStyle(rowDetail),
        labelSmall = readerTextStyle(rowDetail),
    )
}
