package co.appreactor.nextcloud.news

import retrofit2.http.GET

interface NewsApi {

    @GET("feeds")
    fun getFeeds(): FeedsPayload

    @GET("items?type=3&getRead=false&batchSize=-1")
    fun getUnreadItems(): ItemsPayload

    @GET("items?type=2&getRead=true&batchSize=-1")
    fun getStarredItems(): ItemsPayload
}