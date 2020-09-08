package co.appreactor.nextcloud.news.podcasts

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import co.appreactor.nextcloud.news.db.NewsItem
import java.io.File

fun NewsItem.isPodcast(): Boolean {
    return enclosureMime?.startsWith("audio") ?: false
}

fun NewsItem.getPodcastFile(context: Context): File {
    val podcasts = File(context.externalCacheDir, "podcasts")
    podcasts.mkdir()

    val fileName = "$id-${enclosureLink!!.split("/").last()}"
    return File(podcasts, fileName)
}

fun Fragment.playPodcast(podcast: NewsItem) {
    val fileUri = FileProvider.getUriForFile(
        requireContext(),
        "${requireContext().packageName}.fileprovider",
        podcast.getPodcastFile(requireContext())
    )

    val intent = Intent().apply {
        action = Intent.ACTION_VIEW
        data = fileUri
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    startActivity(intent)
}