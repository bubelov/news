package co.appreactor.nextcloud.news.api

data class PutStarredArgsItem (
    val feedId: Long,
    val guidHash: String
)