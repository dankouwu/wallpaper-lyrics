package com.dnk.wallpaperlyrics

/**
 * Sizes the album art in the metadata view. YouTube publishes 16:9 thumbnails, so
 * those keep their own aspect ratio and everything else stays square.
 */
object MetadataArtLayout {
    const val WIDTH_FRACTION = 0.70f
    const val MAX_HEIGHT_FRACTION = 0.55f

    fun allowsNativeAspect(packageName: String?): Boolean {
        if (packageName.isNullOrBlank()) return false
        return packageName.lowercase().contains("youtube")
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
