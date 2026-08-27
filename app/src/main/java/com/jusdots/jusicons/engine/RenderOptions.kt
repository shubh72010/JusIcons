package com.jusdots.jusicons.engine

import androidx.annotation.ColorInt

data class RenderOptions(
    @ColorInt val bgColor: Int = 0xFF121212.toInt(), // Nothing circular bg (Image2 dark, not pure black)
    @ColorInt val fgColor: Int = 0xFFFFFFFF.toInt(),
    val enableMonoCheck: Boolean = false, // don't fallback to solid square (was causing Image1 black square)
    val useLightVariant: Boolean = false,
    val forensicScale: Boolean = false, // match Image2: large glyph, not 0.3888 tiny
    val showBackground: Boolean = true,
    val binary: Boolean = false, // continuous preserves internal glyph details (Image1: 05 loses details when binary)
)
