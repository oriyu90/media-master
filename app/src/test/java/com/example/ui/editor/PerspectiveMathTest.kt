package com.example.ui.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * v1.14.0: four-corner perspective selection math.
 */
class PerspectiveMathTest {

    private val epsilon = 0.0001f

    @Test
    fun `default quad covers the whole bitmap and is usable`() {
        val quad = defaultQuad()
        assertTrue(isConvexQuad(quad))
        assertTrue(isUsableQuad(quad))
        assertEquals(1f, abs(signedArea(quad)), epsilon)
    }

    @Test
    fun `a bow-tie quad is rejected`() {
        val quad = PerspectiveQuad(
            topLeft = NormalizedPoint(0f, 0f),
            topRight = NormalizedPoint(1f, 1f),
            bottomRight = NormalizedPoint(1f, 0f),
            bottomLeft = NormalizedPoint(0f, 1f),
        )
        assertFalse(isConvexQuad(quad))
        assertFalse(isUsableQuad(quad))
    }

    @Test
    fun `a collapsed quad is rejected`() {
        val quad = PerspectiveQuad(
            topLeft = NormalizedPoint(0.5f, 0.5f),
            topRight = NormalizedPoint(0.5f, 0.5f),
            bottomRight = NormalizedPoint(0.5f, 0.5f),
            bottomLeft = NormalizedPoint(0.5f, 0.5f),
        )
        assertFalse(isConvexQuad(quad))
    }

    @Test
    fun `output size averages opposite edges`() {
        val quad = PerspectiveQuad(
            topLeft = NormalizedPoint(0.1f, 0.1f),
            topRight = NormalizedPoint(0.9f, 0.1f),
            bottomRight = NormalizedPoint(0.9f, 0.9f),
            bottomLeft = NormalizedPoint(0.1f, 0.9f),
        )
        val (w, h) = perspectiveOutputSize(quad, 1000, 500)
        assertEquals(800, w)
        assertEquals(400, h)
    }

    @Test
    fun `output size of the full quad keeps the bitmap resolution`() {
        val (w, h) = perspectiveOutputSize(defaultQuad(), 4000, 3000)
        assertEquals(4000, w)
        assertEquals(3000, h)
    }

    @Test
    fun `output size never collapses below one pixel`() {
        val quad = PerspectiveQuad(
            topLeft = NormalizedPoint(0.5f, 0.5f),
            topRight = NormalizedPoint(0.5001f, 0.5f),
            bottomRight = NormalizedPoint(0.5001f, 0.5001f),
            bottomLeft = NormalizedPoint(0.5f, 0.5001f),
        )
        val (w, h) = perspectiveOutputSize(quad, 1000, 1000)
        assertTrue(w >= 1)
        assertTrue(h >= 1)
    }

    @Test
    fun `source and destination point arrays are ordered tl tr br bl`() {
        val src = perspectiveSourcePoints(defaultQuad(), 1000, 500)
        assertArrayEquals(floatArrayOf(0f, 0f, 1000f, 0f, 1000f, 500f, 0f, 500f), src)
        val dst = perspectiveDestinationPoints(800, 400)
        assertArrayEquals(floatArrayOf(0f, 0f, 800f, 0f, 800f, 400f, 0f, 400f), dst)
    }

    @Test
    fun `display delta converts to normalized coordinates`() {
        val (dx, dy) = displayDeltaToNormalized(100f, 50f, 400f, 200f)
        assertEquals(0.25f, dx, epsilon)
        assertEquals(0.25f, dy, epsilon)
        assertEquals(0f, displayDeltaToNormalized(10f, 10f, 0f, 0f).first, epsilon)
    }

    @Test
    fun `quad corner hit testing`() {
        val quad = defaultQuad()
        assertEquals(0, hitTestQuadCorner(quad, 0.02f, 0.02f, 0.05f))
        assertEquals(1, hitTestQuadCorner(quad, 0.98f, 0.02f, 0.05f))
        assertEquals(2, hitTestQuadCorner(quad, 0.98f, 0.98f, 0.05f))
        assertEquals(3, hitTestQuadCorner(quad, 0.02f, 0.98f, 0.05f))
        assertNull(hitTestQuadCorner(quad, 0.5f, 0.5f, 0.05f))
    }

    @Test
    fun `moving a corner keeps the others`() {
        val quad = defaultQuad().withPoint(0, NormalizedPoint(0.2f, 0.3f))
        assertEquals(0.2f, quad.topLeft.x, epsilon)
        assertEquals(0.3f, quad.topLeft.y, epsilon)
        assertEquals(1f, quad.topRight.x, epsilon)
        assertEquals(1f, quad.bottomRight.y, epsilon)
    }

    @Test
    fun `clamping keeps points inside the bitmap`() {
        val point = clampQuadPoint(NormalizedPoint(-1f, 2f))
        assertEquals(0f, point.x, epsilon)
        assertEquals(1f, point.y, epsilon)
    }

    @Test
    fun `a slightly moved convex quad stays usable`() {
        val quad = defaultQuad()
            .withPoint(0, NormalizedPoint(0.1f, 0.1f))
            .withPoint(1, NormalizedPoint(0.9f, 0.05f))
            .withPoint(2, NormalizedPoint(0.95f, 0.9f))
            .withPoint(3, NormalizedPoint(0.05f, 0.95f))
        assertTrue(isConservativeUsable(quad))
    }

    private fun isConservativeUsable(quad: PerspectiveQuad): Boolean = isUsableQuad(quad)

    private fun assertArrayEquals(expected: FloatArray, actual: FloatArray) {
        assertEquals(expected.size, actual.size)
        for (i in expected.indices) {
            assertEquals(expected[i], actual[i], epsilon)
        }
    }
}
