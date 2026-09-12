package com.dnk.wallpaperlyrics

/**
 * Sizes the album art in the metadata view. YouTube publishes 16:9 thumbnails, so
 * those keep their own aspect ratio and everything else stays square.
 */
object MetadataArtLayout {
    const val WIDTH_FRACTION = 0.70f
    const val MAX_HEIGHT_FRACTION = 0.55f

    /** Anything at or below this luma counts as letterbox rather than artwork. */
    const val LETTERBOX_LUMA_CEILING = 18

    /**
     * Refuse to trim more than this off either edge. A genuinely dark cover would
     * otherwise get eaten from both ends.
     */
    const val LETTERBOX_MAX_TRIM_FRACTION = 0.35f

    fun allowsNativeAspect(packageName: String?): Boolean {
        if (packageName.isNullOrBlank()) return false
        return packageName.lowercase().contains("youtube")
    }

    fun isLetterboxDark(pixel: Int): Boolean {
        val r = (pixel shr 16) and 0xFF
        val g = (pixel shr 8) and 0xFF
        val b = pixel and 0xFF
        return (r * 299 + g * 587 + b * 114) / 1000 <= LETTERBOX_LUMA_CEILING
    }

    /**
     * YouTube pads thumbnails into a fixed frame, so art that is not the frame's shape
     * arrives with black bars baked into the top and bottom. Given one dark flag per
     * row, returns the rows holding the picture.
     *
     * Only bars running from an edge count. A dark band in the middle of a cover is
     * part of the cover.
     */
    fun contentRows(rowIsDark: BooleanArray): IntRange {
        val height = rowIsDark.size
        if (height == 0) return IntRange.EMPTY

        var top = 0
        while (top < height && rowIsDark[top]) top++
        if (top == height) return 0 until height

        var bottom = height - 1
        while (rowIsDark[bottom]) bottom--

        val maxTrim = (height * LETTERBOX_MAX_TRIM_FRACTION).toInt()
        if (top > maxTrim || height - 1 - bottom > maxTrim) return 0 until height
        return top..bottom
    }

    fun aspectFor(allowsNativeAspect: Boolean, bitmapWidth: Int, bitmapHeight: Int): Float {
        if (!allowsNativeAspect || bitmapWidth <= 0 || bitmapHeight <= 0) {
            return 1.0f
        }
        return bitmapWidth.toFloat() / bitmapHeight.toFloat()
    }

    fun fittedWidth(screenWidth: Float, screenHeight: Float, aspect: Float): Float {
        val initialW = screenWidth * WIDTH_FRACTION
        val initialH = initialW / aspect
        val maxH = screenHeight * MAX_HEIGHT_FRACTION
        return if (initialH > maxH) {
            maxH * aspect
        } else {
            initialW
        }
    }

    fun fittedHeight(screenWidth: Float, screenHeight: Float, aspect: Float): Float {
        val initialW = screenWidth * WIDTH_FRACTION
        val initialH = initialW / aspect
        val maxH = screenHeight * MAX_HEIGHT_FRACTION
        return if (initialH > maxH) {
            maxH
        } else {
            initialH
        }
    }
}
