package co.appreactor.nextcloud.news

import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Headers

interface NewsApi {

    @GET("feeds")
    @Headers("Accept: application/json")
    suspend fun getFeeds(
        @Header("Authorization") authorization: String
    ): FeedsPayload

    @GET("items?type=3&getRead=false&batchSize=-1")
    @Headers("Accept: application/json")
    suspend fun getUnreadItems(
        @Header("Authorization") authorization: String
    ): ItemsPayload
}