package co.appreactor.news.entriesenclosures

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import co.appreactor.news.db.Entry
import java.io.File

fun String.isAudioMime() = startsWith("audio")

fun Context.getCachedEnclosure(
    entryId: String,
    entryEnclosureLink: String,
    entryEnclosureLinkType: String,
): File {
    val enclosures = File(externalCacheDir, "enclosures")
    enclosures.mkdir()

    val mimeDir = File(enclosures, entryEnclosureLinkType.split("/").first())
    mimeDir.mkdir()

    val fileName = "$entryId-${entryEnclosureLink.split("/").last()}"
    return File(mimeDir, fileName)
}

fun Context.openCachedEnclosure(entry: Entry) {
    val fileUri = FileProvider.getUriForFile(
        this,
        "${packageName}.fileprovider",
        getCachedEnclosure(entry.id, entry.enclosureLink, entry.enclosureLinkType)
    )

    val intent = Intent().apply {
        action = Intent.ACTION_VIEW
        data = fileUri
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    startActivity(intent)
}