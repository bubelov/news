package db

import co.appreactor.feedk.AtomLinkRel
import okhttp3.HttpUrl

data class Link(
    val feedId: String?,
    val entryId: String?,
    val href: HttpUrl,
    val rel: AtomLinkRel?,
    val type: String?,
    val hreflang: String?,
    val title: String?,
    val length: Long?,
    val extEnclosureDownloadProgress: Double?,
    val extCacheUri: String?,
)