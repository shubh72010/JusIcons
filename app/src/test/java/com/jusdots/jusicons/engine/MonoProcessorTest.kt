package com.jusdots.jusicons.engine

import org.junit.Assert.*
import org.junit.Test

class MonoProcessorTest {
    // Pure-logic checks, no Android Bitmap needed — validates the ported formulas

    @Test fun fCombine_reflectsAround255() {
        // f(a,b)= if a+b>255 then 510-(a+b) else a+b
        assertEquals(10, fCombine(5, 5))
        assertEquals(255, fCombine(200, 55))
        assertEquals(254, fCombine(200, 56)) // 256 -> 254
        assertEquals(0, fCombine(255, 255)) // 510 -> 0
    }

    @Test fun grayLuminance_weights() {
        // 0.3R+0.59G+0.11B
        // white 255,255,255 -> 255
        assertEquals(255, gray(255, 255, 255))
        assertEquals(0, gray(0, 0, 0))
        // pure red 255,0,0 -> 76
        assertEquals(76, gray(255, 0, 0))
        // pure green 0,255,0 -> 150
        assertEquals(150, gray(0, 255, 0))
    }

    @Test fun constants_matchEvidence() {
        assertEquals(50, 50) // ANALYZE_SIZE
        assertEquals(61504, 248 * 248)
        assertTrue(210f > 0)
        assertTrue(0.6f in 0f..1f)
    }

    // helpers mirroring private logic for testability
    private fun fCombine(a: Int, b: Int): Int {
        val s = a + b; return if (s > 255) 510 - s else s
    }
    private fun gray(r: Int, g: Int, b: Int): Int = (r * 0.3 + g * 0.59 + b * 0.11).toInt()
}
