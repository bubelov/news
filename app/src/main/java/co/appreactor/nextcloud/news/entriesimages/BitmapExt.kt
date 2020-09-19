package co.appreactor.nextcloud.news.entriesimages

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.random.Random

fun Bitmap.hasTransparentAngles(): Boolean {
    if (width == 0 || height == 0) {
        return false
    }

    if (getPixel(0, 0) == Color.TRANSPARENT) {
        return true
    }

    if (getPixel(width - 1, 0) == Color.TRANSPARENT) {
        return true
    }

    if (getPixel(0, height - 1) == Color.TRANSPARENT) {
        return true
    }

    if (getPixel(width - 1, height - 1) == Color.TRANSPARENT) {
        return true
    }

    return false
}

fun Bitmap.looksLikeSingleColor(): Boolean {
    if (width == 0 || height == 0) {
        return false
    }

    val randomPixels = (1..100).map {
        getPixel(Random.nextInt(0, width), Random.nextInt(0, height))
    }

    return randomPixels.all { it == randomPixels.first() }
}