package com.example.ui.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * v1.14.0: crop rectangle math shared by the overlay and the save pipeline.
 */
class CropMathTest {

    private val epsilon = 0.0001f

    private fun assertRatio(rect: CropRect, ratio: Float, imageAspect: Float) {
        val pixelRatio = (rect.width * imageAspect) / rect.height
        assertTrue(
            "pixel ratio $pixelRatio should be $ratio",
            abs(pixelRatio - ratio) <= 0.01f,
        )
    }

    private fun assertInside(rect: CropRect) {
        assertTrue(rect.left >= -epsilon)
        assertTrue(rect.top >= -epsilon)
        assertTrue(rect.right <= 1f + epsilon)
        assertTrue(rect.bottom <= 1f + epsilon)
        assertTrue(rect.right > rect.left)
        assertTrue(rect.bottom > rect.top)
    }

    @Test
    fun `fitAspect makes a full-width band for wide ratios`() {
        val rect = fitAspect(CropRect.FULL, 16f / 9f, 4f / 3f)
        assertEquals(0f, rect.left, epsilon)
        assertEquals(1f, rect.right, epsilon)
        assertEquals(0.125f, rect.top, epsilon)
        assertEquals(0.875f, rect.bottom, epsilon)
        assertRatio(rect, 16f / 9f, 4f / 3f)
        assertInside(rect)
    }

    @Test
    fun `fitAspect makes a full-height column for tall ratios`() {
        val rect = fitAspect(CropRect.FULL, 9f / 16f, 3f / 4f)
        assertEquals(0f, rect.top, epsilon)
        assertEquals(1f, rect.bottom, epsilon)
        assertEquals(0.125f, rect.left, epsilon)
        assertEquals(0.875f, rect.right, epsilon)
        assertRatio(rect, 9f / 16f, 3f / 4f)
    }

    @Test
    fun `fitAspect keeps the square in pixels not in normalized units`() {
        val rect = fitAspect(CropRect.FULL, 1f, 4f / 3f)
        assertRatio(rect, 1f, 4f / 3f)
        assertEquals(0.75f, rect.width, epsilon)
        assertEquals(1f, rect.height, epsilon)
    }

    @Test
    fun `fitAspect produces a centred inside frame`() {
        val rect = fitAspect(CropRect(0.2f, 0.3f, 0.4f, 0.5f), 16f / 9f, 1f)
        // The wide band can only be centred horizontally (it spans the full
        // width); vertically the requested centre still fits and is kept.
        assertEquals(0.5f, rect.centerX, epsilon)
        assertEquals(0.4f, rect.centerY, epsilon)
        assertInside(rect)
        assertRatio(rect, 16f / 9f, 1f)
    }

    @Test
    fun `fitAspect rejects an invalid image aspect`() {
        val current = CropRect(0.1f, 0.1f, 0.9f, 0.9f)
        assertEquals(current, fitAspect(current, 1f, 0f))
    }

    @Test
    fun `free corner drag resizes from the opposite corner`() {
        val rect = resizeCorner(CropRect.FULL, CropCorner.TOP_LEFT, 0.1f, 0.2f, null, 1f)
        assertEquals(0.1f, rect.left, epsilon)
        assertEquals(0.2f, rect.top, epsilon)
        assertEquals(1f, rect.right, epsilon)
        assertEquals(1f, rect.bottom, epsilon)
        assertInside(rect)
    }

    @Test
    fun `free corner drag clamps at the image bounds`() {
        val rect = resizeCorner(CropRect.FULL, CropCorner.TOP_LEFT, -5f, -5f, null, 1f)
        assertEquals(CropRect.FULL, rect)
    }

    @Test
    fun `free corner drag honours the minimum size`() {
        val rect = resizeCorner(CropRect.FULL, CropCorner.BOTTOM_RIGHT, -5f, -5f, null, 1f, minSize = 0.05f)
        assertTrue(rect.width >= 0.05f - epsilon)
        assertTrue(rect.height >= 0.05f - epsilon)
        assertInside(rect)
    }

    @Test
    fun `locked ratio resize keeps the pixel ratio`() {
        val rect = resizeCorner(CropRect.FULL, CropCorner.BOTTOM_RIGHT, -0.2f, -0.2f, 1f, 1.5f)
        assertRatio(rect, 1f, 1.5f)
        assertEquals(1f, rect.bottom, epsilon)
        assertInside(rect)
    }

    @Test
    fun `locked ratio resize anchors the opposite corner`() {
        val rect = resizeCorner(CropRect.FULL, CropCorner.TOP_LEFT, 0.25f, 0.25f, 16f / 9f, 16f / 9f)
        assertEquals(1f, rect.right, epsilon)
        assertEquals(1f, rect.bottom, epsilon)
        assertRatio(rect, 16f / 9f, 16f / 9f)
    }

    @Test
    fun `moving the frame clamps at the edges without resizing`() {
        val rect = moveRect(CropRect(0.2f, 0.2f, 0.6f, 0.6f), 0.1f, -0.1f)
        assertEquals(0.3f, rect.left, epsilon)
        assertEquals(0.1f, rect.top, epsilon)
        assertEquals(0.4f, rect.width, epsilon)
        assertEquals(0.4f, rect.height, epsilon)

        val edge = moveRect(CropRect(0.2f, 0.2f, 0.6f, 0.6f), 5f, 5f)
        assertEquals(1f, edge.right, epsilon)
        assertEquals(1f, edge.bottom, epsilon)
        assertInside(edge)
    }

    @Test
    fun `full rect converts to the whole bitmap`() {
        val pixels = CropRect.FULL.toPixelRect(4000, 3000)
        assertEquals(0, pixels.left)
        assertEquals(0, pixels.top)
        assertEquals(4000, pixels.right)
        assertEquals(3000, pixels.bottom)
        assertEquals(4000, pixels.width)
        assertEquals(3000, pixels.height)
    }

    @Test
    fun `pixel rect never collapses to zero`() {
        val pixels = CropRect(0.5f, 0.5f, 0.5f, 0.5f).toPixelRect(100, 100)
        assertTrue(pixels.width >= 1)
        assertTrue(pixels.height >= 1)
    }

    @Test
    fun `hit testing finds each corner and misses the centre`() {
        val rect = CropRect(0.2f, 0.2f, 0.8f, 0.8f)
        assertEquals(CropCorner.TOP_LEFT, hitTestCorner(rect, 0.2f, 0.2f, 0.05f))
        assertEquals(CropCorner.TOP_RIGHT, hitTestCorner(rect, 0.8f, 0.2f, 0.05f))
        assertEquals(CropCorner.BOTTOM_RIGHT, hitTestCorner(rect, 0.8f, 0.8f, 0.05f))
        assertEquals(CropCorner.BOTTOM_LEFT, hitTestCorner(rect, 0.2f, 0.8f, 0.05f))
        assertNull(hitTestCorner(rect, 0.5f, 0.5f, 0.05f))
    }

    @Test
    fun `usability rejects degenerate rects`() {
        assertTrue(isUsableCrop(CropRect.FULL))
        assertFalse(isUsableCrop(CropRect(0.5f, 0.5f, 0.5f, 0.9f)))
        assertFalse(isUsableCrop(CropRect(0.5f, 0.5f, 0.9f, 0.5f)))
    }

    @Test
    fun `aspect presets resolve against the image`() {
        assertNull(AspectOption.FREE.resolveRatio(1.5f))
        assertEquals(1.5f, AspectOption.ORIGINAL.resolveRatio(1.5f)!!, epsilon)
        assertEquals(1f, AspectOption.SQUARE.resolveRatio(1.5f)!!, epsilon)
        assertNotNull(AspectOption.RATIO_16_9.resolveRatio(1.5f))
    }

    @Test
    fun `fitImageRect letterboxes a wide image inside a square container`() {
        val fit = fitImageRect(1000f, 1000f, 2000f, 1000f)
        assertEquals(0f, fit.left, epsilon)
        assertEquals(1000f, fit.width, epsilon)
        assertEquals(500f, fit.height, epsilon)
        assertEquals(250f, fit.top, epsilon)
    }

    @Test
    fun `fitImageRect is empty for a zero container`() {
        val fit = fitImageRect(0f, 100f, 100f, 100f)
        assertEquals(0f, fit.width, epsilon)
        assertEquals(0f, fit.height, epsilon)
    }

    @Test
    fun `rescaleToAspect keeps a locked ratio when the bitmap aspect changes`() {
        // A 1:1 crop at 40% height on a 1.0 aspect bitmap.
        val rect = CropRect(0.3f, 0.3f, 0.5f, 0.5f)
        val rescaled = rescaleToAspect(rect, 1f, 2f)
        assertRatio(rescaled, 1f, 2f)
        assertEquals(0.4f, rescaled.centerX, 0.01f)
        assertEquals(0.4f, rescaled.centerY, 0.01f)
        assertInside(rescaled)
    }

    @Test
    fun `rescaleToAspect never shrinks below the minimum size`() {
        val rect = CropRect(0.49f, 0.49f, 0.51f, 0.51f)
        val rescaled = rescaleToAspect(rect, 16f / 9f, 4f / 3f)
        assertTrue(rescaled.width >= 0.05f - 0.001f)
        assertTrue(rescaled.height >= 0.05f - 0.001f)
        assertInside(rescaled)
    }
}
