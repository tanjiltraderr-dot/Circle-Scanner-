package com.example

import android.graphics.Bitmap
import android.graphics.Color
import com.example.scanner.engine.DocumentEnhancementEngine
import com.example.scanner.engine.EnhancementMode
import com.example.scanner.engine.ImageOrientationCorrector
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DocumentEnhancementEngineTest {

    private fun createSampleDocument(width: Int = 100, height: Int = 100): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(width * height) { Color.rgb(238, 235, 230) } // Off-white paper background

        // Dark text line at y = 40 (thickness 4)
        for (y in 38..42) {
            for (x in 20 until 80) {
                pixels[y * width + x] = Color.rgb(30, 30, 30)
            }
        }

        // Blue signature at y = 70 (thickness 3)
        for (y in 69..71) {
            for (x in 30 until 70) {
                pixels[y * width + x] = Color.rgb(20, 50, 180)
            }
        }

        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        return bitmap
    }

    /**
     * Requirement: Mode 1 - Original Color.
     * Must return an independent bitmap copy that preserves authentic pixel colors
     * without modifying or overwriting the original captured bitmap in-place.
     */
    @Test
    fun testOriginalColorPreservesSourceAndPixelValues() {
        val original = createSampleDocument()
        val originalPixel = original.getPixel(50, 40) // text pixel

        val result = DocumentEnhancementEngine.applyOriginal(original)

        assertNotNull(result)
        assertNotSame("Must return a new Bitmap instance to prevent in-place overwriting", original, result)
        assertEquals(original.width, result.width)
        assertEquals(original.height, result.height)
        assertEquals(originalPixel, result.getPixel(50, 40))

        // Mutating the enhanced bitmap must not affect the original raw bitmap
        result.setPixel(50, 40, Color.RED)
        assertEquals(originalPixel, original.getPixel(50, 40))
    }

    /**
     * Requirement: Mode 2 - Grayscale.
     * Computes luminance conversion where R, G, B channels are equalized without losing details.
     */
    @Test
    fun testGrayscaleConversion() {
        val original = createSampleDocument()
        val gray = DocumentEnhancementEngine.applyGrayscale(original)

        assertNotNull(gray)
        assertEquals(original.width, gray.width)
        assertEquals(original.height, gray.height)

        // Blue signature pixel was previously colored (R != B)
        // In grayscale, R, G, and B components must be approximately equal
        val pixel = gray.getPixel(50, 70)
        val r = Color.red(pixel)
        val g = Color.green(pixel)
        val b = Color.blue(pixel)

        assertTrue("R and G must be equal in grayscale", kotlin.math.abs(r - g) <= 2)
        assertTrue("G and B must be equal in grayscale", kotlin.math.abs(g - b) <= 2)
    }

    /**
     * Requirement: Mode 3 - Black & White.
     * "Black-and-white threshold এমন হবে, যাতে হালকা লেখা অকারণে অদৃশ্য না হয়।"
     * Faint pencil and light ink strokes must NOT disappear into white.
     */
    @Test
    fun testBlackAndWhitePreservesFaintWriting() {
        val width = 80
        val height = 80
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(width * height) { Color.rgb(240, 240, 240) } // White paper

        // Faint handwriting (e.g. light pencil at gray level 185) at y=40, thickness 4
        for (y in 38..42) {
            for (x in 20 until 60) {
                pixels[y * width + x] = Color.rgb(185, 185, 185)
            }
        }
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)

        val bw = DocumentEnhancementEngine.applyBlackAndWhite(bitmap, blockSize = 25, offset = 6.0)

        // Verify paper background is pure white
        val paperPixel = bw.getPixel(10, 10)
        assertEquals(Color.WHITE, paperPixel)

        // Verify faint pencil stroke is preserved as black and did NOT disappear into white
        val strokePixel = bw.getPixel(40, 40)
        assertEquals("Faint writing must be preserved as black text and not bleached away", Color.BLACK, strokePixel)
    }

    /**
     * Requirement: Mode 4 - Document Contrast.
     * Whitens the paper background while maintaining dark readable text.
     */
    @Test
    fun testDocumentContrastEnhancement() {
        val original = createSampleDocument()
        val contrasted = DocumentEnhancementEngine.applyDocumentContrast(original)

        assertNotNull(contrasted)
        assertEquals(original.width, contrasted.width)
        assertEquals(original.height, contrasted.height)

        // Paper background should be brightened towards pure white (> 220)
        val paperPixel = contrasted.getPixel(10, 10)
        assertTrue(Color.red(paperPixel) >= 220)
        assertTrue(Color.green(paperPixel) >= 220)
        assertTrue(Color.blue(paperPixel) >= 220)

        // Text stroke should remain dark
        val textPixel = contrasted.getPixel(50, 40)
        assertTrue(Color.red(textPixel) <= 50)
    }

    /**
     * Requirement: Mode 5 - Noise Reduction.
     * Smooths flat regions while preserving sharp character edges.
     */
    @Test
    fun testNoiseReductionPreservesTextEdges() {
        val width = 80
        val height = 80
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(width * height) { Color.WHITE }

        // Sharp black line at y=40
        for (y in 38..42) {
            for (x in 20 until 60) {
                pixels[y * width + x] = Color.BLACK
            }
        }
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)

        val denoised = DocumentEnhancementEngine.applyNoiseReduction(bitmap)
        assertNotNull(denoised)

        // Text stroke center should remain dark black
        val textPixel = denoised.getPixel(40, 40)
        val lumText = (Color.red(textPixel) + Color.green(textPixel) + Color.blue(textPixel)) / 3
        assertTrue("Text must remain dark after noise reduction", lumText < 40)

        // Surrounding paper must remain white
        val paperPixel = denoised.getPixel(40, 10)
        val lumPaper = (Color.red(paperPixel) + Color.green(paperPixel) + Color.blue(paperPixel)) / 3
        assertTrue("Paper background must remain white", lumPaper > 220)
    }

    /**
     * Requirement: Mode 6 - Controlled Sharpening.
     * "অতিরিক্ত sharpening দিয়ে লেখার অক্ষর নষ্ট করবে না।"
     * Unsharp masking with soft clamping prevents overflowing black halos or character distortion.
     */
    @Test
    fun testControlledSharpeningPreservesCharactersWithoutSevereHalos() {
        val original = createSampleDocument()
        val sharpened = DocumentEnhancementEngine.applyControlledSharpening(original)

        assertNotNull(sharpened)
        assertEquals(original.width, sharpened.width)
        assertEquals(original.height, sharpened.height)

        // Compare contrast along the ink edge (y=40 is text, y=30 is background paper)
        val textLuminance = Color.red(sharpened.getPixel(50, 40))
        val paperLuminance = Color.red(sharpened.getPixel(50, 20))

        assertTrue("Text stroke must remain substantially darker than paper", paperLuminance - textLuminance > 120)
    }

    /**
     * Requirement: Shadow Reduction.
     * "Shadow reduction ব্যবহার করলে background ও text আলাদা করার মান পরীক্ষা করবে।"
     */
    @Test
    fun testShadowReductionSeparatesBackgroundAndText() {
        val width = 80
        val height = 80
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(width * height)

        // Draw shadow gradient across the paper (darker on left: 140, lighter on right: 230)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val shade = 140 + (x * 90 / width)
                pixels[y * width + x] = Color.rgb(shade, shade, shade)
            }
        }

        // Draw dark text in the shadowed region (x in 15..25, y in 20..60)
        for (y in 20..60) {
            for (x in 18..22) {
                pixels[y * width + x] = Color.rgb(20, 20, 20)
            }
        }
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)

        val shadowLifted = DocumentEnhancementEngine.applyShadowReduction(bitmap)
        assertNotNull(shadowLifted)

        // In the shadowed region, background paper must be lifted/brightened
        val paperInShadow = shadowLifted.getPixel(10, 40)
        val paperLum = Color.red(paperInShadow)
        assertTrue("Shadow reduction must lift paper luminance in shadowed zone", paperLum >= 160)

        // Text must remain distinctly separated from background
        val textInShadow = shadowLifted.getPixel(20, 40)
        val textLum = Color.red(textInShadow)
        assertTrue("Text must remain significantly darker than paper background", paperLum - textLum >= 60)
    }

    /**
     * Requirement: Orientation - EXIF orientation tests.
     */
    @Test
    fun testExifOrientationRotation() {
        val bitmap = Bitmap.createBitmap(100, 60, Bitmap.Config.ARGB_8888)

        // Rotate 90 degrees
        val rotated90 = ImageOrientationCorrector.rotateBitmap(bitmap, 90f)
        assertEquals(60, rotated90.width)
        assertEquals(100, rotated90.height)

        // Rotate 180 degrees
        val rotated180 = ImageOrientationCorrector.rotateBitmap(bitmap, 180f)
        assertEquals(100, rotated180.width)
        assertEquals(60, rotated180.height)

        // Rotate 270 degrees
        val rotated270 = ImageOrientationCorrector.rotateBitmap(bitmap, 270f)
        assertEquals(60, rotated270.width)
        assertEquals(100, rotated270.height)
    }

    /**
     * Requirement: Orientation -
     * "শুধু ছবির প্রস্থ ও উচ্চতা দেখে ভুলভাবে সব document ঘুরিয়ে দেবে না।"
     * Landscape documents with width > height must NOT be arbitrarily rotated 90 degrees
     * when there is no text justification!
     */
    @Test
    fun testLandscapeDocumentsNotArbitrarilyRotated() = runBlocking {
        // Landscape document without text (e.g. wide check or landscape diagram)
        val landscapeBitmap = Bitmap.createBitmap(800, 500, Bitmap.Config.ARGB_8888)
        val (corrected, rotationAngle) = ImageOrientationCorrector.autoCorrectOrientation(landscapeBitmap)

        assertEquals("Landscape document with no sideways text must keep 0 degree rotation", 0f, rotationAngle, 0.01f)
        assertEquals(800, corrected.width)
        assertEquals(500, corrected.height)
    }

    /**
     * Requirement: EnhancementMode enum integration with DocumentEnhancementEngine.enhance
     */
    @Test
    fun testAllEnhancementModesExecution() {
        val original = createSampleDocument()

        val modes = listOf(
            EnhancementMode.ORIGINAL,
            EnhancementMode.GRAYSCALE,
            EnhancementMode.BLACK_WHITE,
            EnhancementMode.DOCUMENT_CONTRAST,
            EnhancementMode.NOISE_REDUCTION,
            EnhancementMode.SHARPENING,
            EnhancementMode.CAMSCANNER_MAGIC,
            EnhancementMode.SHADOW_REDUCTION
        )

        for (mode in modes) {
            val result = DocumentEnhancementEngine.enhance(original, mode)
            assertNotNull("Enhancement result for mode $mode must not be null", result)
            assertEquals("Width must match for mode $mode", original.width, result.width)
            assertEquals("Height must match for mode $mode", original.height, result.height)
        }
    }
}
