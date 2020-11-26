package co.appreactor.news.api.nextcloud

data class PutStarredArgsItem (
    val feedId: Long,
    val guidHash: String
)