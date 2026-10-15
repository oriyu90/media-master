package com.example.ui.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v1.11.0: pins the bounded-viewer contract — scale never drops below fit
 * and panning can never expose a gutter beyond the image edge.
 */
class ZoomBoundsTest {

    @Test
    fun `scale clamps into range`() {
        assertEquals(1f, clampZoomScale(0.3f, 1f, 5f))
        assertEquals(5f, clampZoomScale(9f, 1f, 5f))
        assertEquals(2f, clampZoomScale(2f, 1f, 5f))
    }

    @Test
    fun `fill offset is zero at rest`() {
        val box = IntSize(1000, 800)
        assertEquals(Offset.Zero, clampFillOffset(Offset(500f, -300f), 1f, box))
    }

    @Test
    fun `fill offset clamps to overhang`() {
        val box = IntSize(1000, 800)
        // scale 2 -> max = 1000*(2-1)/2 = 500 x, 800*(2-1)/2 = 400 y.
        val clamped = clampFillOffset(Offset(900f, -700f), 2f, box)
        assertEquals(500f, clamped.x, 0.001f)
        assertEquals(-400f, clamped.y, 0.001f)
        val inside = clampFillOffset(Offset(100f, 100f), 2f, box)
        assertEquals(100f, inside.x, 0.001f)
        assertEquals(100f, inside.y, 0.001f)
    }

    @Test
    fun `fit draw keeps long edge inside`() {
        // Wide image in a tall box: width fills, height letterboxes.
        val draw = fitDrawSize(IntSize(800, 1200), IntSize(1600, 800))
        assertEquals(800f, draw.width, 0.001f)
        assertTrue(draw.height < 1200f)
        // Tall image in a wide box: height fills, width letterboxes.
        val draw2 = fitDrawSize(IntSize(1200, 800), IntSize(800, 1600))
        assertEquals(800f, draw2.height, 0.001f)
        assertTrue(draw2.width < 1200f)
    }

    @Test
    fun `fit offset cannot drift when unzoomed`() {
        val box = IntSize(1000, 1000)
        val draw = fitDrawSize(box, IntSize(1600, 900))
        assertEquals(Offset.Zero, clampFitOffset(Offset(200f, 200f), 1f, box, draw))
    }

    @Test
    fun `fit offset clamps while zoomed`() {
        val box = IntSize(1000, 1000)
        val draw = fitDrawSize(box, IntSize(1000, 1000))
        // scale 3 on a square: max = (3000-1000)/2 = 1000 per axis.
        val clamped = clampFitOffset(Offset(1500f, -1500f), 3f, box, draw)
        assertEquals(1000f, clamped.x, 0.001f)
        assertEquals(-1000f, clamped.y, 0.001f)
    }
}
