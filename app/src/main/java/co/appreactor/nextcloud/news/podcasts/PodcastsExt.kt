package co.appreactor.nextcloud.news.podcasts

import android.content.Context
import co.appreactor.nextcloud.news.db.NewsItem
import java.io.File

fun NewsItem.isPodcast(): Boolean {
    return enclosureMime?.startsWith("audio") ?: false
}

fun NewsItem.getPodcastFile(context: Context): File {
    val podcasts = File(context.externalCacheDir, "podcasts")
    podcasts.mkdir()

    val extension = enclosureMime!!.split("/")[1]
    return File(podcasts, "${id}-$title.$extension")
}