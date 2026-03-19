package org.vestifeed.api.nextcloud

data class PutStarredArgsItem(
    val feedId: Long,
    val guidHash: String,
)