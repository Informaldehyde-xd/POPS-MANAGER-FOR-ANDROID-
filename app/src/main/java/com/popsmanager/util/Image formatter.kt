package com.popsmanager.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream

/**
 * Normalizes any input image (jpg, png, whatever size/depth) into the exact
 * format POPStarter's icon/cover slot expects: a 200x200 PNG, 8 bits per
 * color channel (the standard, universal PNG bit depth — Android's own PNG
 * encoder always writes 8-bit-per-channel truecolor PNGs, so no separate
 * step is needed for that part once we're going through Bitmap.compress).
 */
object ImageFormatter {

    private const val TARGET_SIZE = 200

    /** Returns PNG bytes, exactly 200x200, 8-bit-per-channel — or null if the input couldn't be decoded. */
    fun normalizeToPng200x200(sourceBytes: ByteArray): ByteArray? {
        val original = BitmapFactory.decodeByteArray(sourceBytes, 0, sourceBytes.size) ?: return null

        // Center-crop to a square first so a resize to 200x200 doesn't
        // stretch/distort non-square source images (most cover art is
        // portrait, e.g. 3:4 — center-cropping keeps the important center
        // content instead of squashing the whole image).
        val cropSize = minOf(original.width, original.height)
        val cropX = (original.width - cropSize) / 2
        val cropY = (original.height - cropSize) / 2
        val squared = Bitmap.createBitmap(original, cropX, cropY, cropSize, cropSize)

        val resized = Bitmap.createScaledBitmap(squared, TARGET_SIZE, TARGET_SIZE, true)

        // ARGB_8888 = 8 bits per channel, which is what gets written out to
        // the PNG regardless — Android's PNG encoder doesn't support any
        // other per-channel bit depth, so this is already the correct format.
        val normalized = if (resized.config == Bitmap.Config.ARGB_8888) {
            resized
        } else {
            resized.copy(Bitmap.Config.ARGB_8888, false)
        }

        val out = ByteArrayOutputStream()
        normalized.compress(Bitmap.CompressFormat.PNG, 100, out)

        if (squared !== original) original.recycle()
        if (resized !== squared) squared.recycle()
        if (normalized !== resized) resized.recycle()
        normalized.recycle()

        return out.toByteArray()
    }
}
