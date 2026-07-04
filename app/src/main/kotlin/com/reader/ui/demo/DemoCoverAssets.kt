package com.reader.ui.demo

import androidx.annotation.DrawableRes
import com.reader.android.R

private const val DEMO_COVER_PREFIX = "demo-cover://"

fun demoCoverUrl(coverKey: String): String = "$DEMO_COVER_PREFIX$coverKey"

fun demoCoverUrlForTitle(title: String): String = demoCoverUrl(demoCoverKeyForTitle(title))

@DrawableRes
fun demoCoverDrawableRes(coverUrl: String): Int? {
    if (!coverUrl.startsWith(DEMO_COVER_PREFIX)) return null
    return when (coverUrl.removePrefix(DEMO_COVER_PREFIX)) {
        "long-night", "longNight" -> R.drawable.reader_cover_long_night
        "mystery-lord", "mysteryLord" -> R.drawable.reader_cover_mystery_lord
        "bright-moon", "brightMoon", "ming" -> R.drawable.reader_cover_bright_moon
        "three-body", "threeBody" -> R.drawable.reader_cover_three_body
        "renjian-cihua", "renjian" -> R.drawable.reader_cover_renjian_cihua
        "android-notes", "androidNotes" -> R.drawable.reader_cover_android_notes
        else -> null
    }
}

private fun demoCoverKeyForTitle(title: String): String = when {
    title.contains("诡秘") -> "mystery-lord"
    title.contains("明朝") -> "bright-moon"
    title.contains("三体") || title.contains("群星") -> "three-body"
    title.contains("人间") -> "renjian-cihua"
    title.contains("Android") || title.contains("纸上") -> "android-notes"
    title.contains("灯塔") -> "bright-moon"
    title.contains("旧日") -> "mystery-lord"
    else -> "long-night"
}
