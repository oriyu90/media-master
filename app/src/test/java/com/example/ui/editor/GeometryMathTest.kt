package com.example.ui.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.hypot

/**
 * v1.14.0: preview and save share this geometry; the tests pin the parts that
 * previously differed (rotation bounding box and resolution-dependent tilt).
 */
class GeometryMathTest {

    private val epsilon = 0.001f

    private fun assertCorners(expected: FloatArray, actual: FloatArray) {
        assertEquals(expected.size, actual.size)
        for (i in expected.indices) {
            assertEquals("corner[$i]", expected[i], actual[i], epsilon)
        }
    }

    private fun edgeLength(quad: FloatArray, from: Int, to: Int): Float =
        hypot(quad[to * 2] - quad[from * 2], quad[to * 2 + 1] - quad[from * 2 + 1])

    @Test
    fun `identity geometry is the source rectangle`() {
        val quad = geometryCorners(100f, 50f, GeometryParams())
        assertCorners(floatArrayOf(0f, 0f, 100f, 0f, 100f, 50f, 0f, 50f), quad)
    }

    @Test
    fun `zero tilt returns the source corners`() {
        assertCorners(sourceCorners(320f, 200f), tiltProjectedCorners(320f, 200f, 0f, 0f))
    }

    @Test
    fun `flip mirrors horizontally`() {
        val quad = geometryCorners(100f, 50f, GeometryParams(flipHorizontal = true))
        assertCorners(floatArrayOf(100f, 0f, 0f, 0f, 0f, 50f, 100f, 50f), quad)
    }

    @Test
    fun `180 degree rotation swaps opposite corners`() {
        val quad = geometryCorners(100f, 50f, GeometryParams(rotateZ = 180f))
        assertEquals(100f, quad[0], epsilon)
        assertEquals(50f, quad[1], epsilon)
        assertEquals(0f, quad[4], epsilon)
        assertEquals(0f, quad[5], epsilon)
    }

    @Test
    fun `90 degree rotation swaps the bounding box`() {
        val bounds = geometryBounds(100f, 50f, GeometryParams(rotateZ = 90f))
        assertEquals(50f, bounds[2] - bounds[0], epsilon)
        assertEquals(100f, bounds[3] - bounds[1], epsilon)
    }

    @Test
    fun `tilt expands the bounding box and keeps the quad convex`() {
        val params = GeometryParams(tiltX = 25f, tiltY = 20f)
        val bounds = geometryBounds(400f, 300f, params)
        assertTrue(bounds[0] < 0f)
        assertTrue(bounds[2] > 400f)
        val quad = geometryCorners(400f, 300f, params)
        val convex = PerspectiveQuad(
            topLeft = NormalizedPoint(quad[0] / 400f, quad[1] / 300f),
            topRight = NormalizedPoint(quad[2] / 400f, quad[3] / 300f),
            bottomRight = NormalizedPoint(quad[4] / 400f, quad[5] / 300f),
            bottomLeft = NormalizedPoint(quad[6] / 400f, quad[7] / 300f),
        )
        assertTrue(isConvexQuad(convex))
    }

    @Test
    fun `pitch brings one edge closer and makes it wider`() {
        val forward = geometryCorners(400f, 300f, GeometryParams(tiltX = 30f))
        val top = edgeLength(forward, 0, 1)
        val bottom = edgeLength(forward, 2, 3)
        assertTrue("top=$top bottom=$bottom", top > bottom)

        val backward = geometryCorners(400f, 300f, GeometryParams(tiltX = -30f))
        assertTrue(edgeLength(backward, 0, 1) < edgeLength(backward, 2, 3))
    }

    @Test
    fun `yaw moves the near edge outward`() {
        // With the camera at +Z, the left edge comes closer and the right edge
        // recedes, so the quad is asymmetric: the near side leaves the source
        // bounds while the far side shrinks inside them.
        val bounds = geometryBounds(400f, 300f, GeometryParams(tiltY = 25f))
        assertTrue(bounds[0] < 0f)
        assertTrue(bounds[2] < 400f)
    }

    @Test
    fun `geometry is resolution independent`() {
        val params = GeometryParams(flipHorizontal = true, tiltX = 18f, tiltY = -12f, rotateZ = 33f)
        val small = geometryCorners(200f, 120f, params)
        val large = geometryCorners(800f, 480f, params)
        for (i in 0 until 4) {
            assertEquals(small[i * 2] * 4f, large[i * 2], 0.01f)
            assertEquals(small[i * 2 + 1] * 4f, large[i * 2 + 1], 0.01f)
        }
    }

    @Test
    fun `extreme tilt keeps a finite positive divisor`() {
        val quad = geometryCorners(100f, 100f, GeometryParams(tiltX = 45f, tiltY = 45f))
        for (value in quad) {
            assertTrue("value should be finite", abs(value) < 10_000f)
        }
    }
}
