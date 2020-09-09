package co.appreactor.nextcloud.news.api

import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PUT
import retrofit2.http.Query

interface NewsApi {

    @GET("feeds")
    fun getFeeds(): Call<FeedsPayload>

    @GET("items?type=3&getRead=false&batchSize=-1")
    fun getUnreadItems(): Call<FeedItemsPayload>

    @GET("items?type=2&getRead=true&batchSize=-1")
    fun getStarredItems(): Call<FeedItemsPayload>

    @GET("items/updated?type=3")
    fun getNewAndUpdatedItems(
        @Query("lastModified") lastModified: Long
    ): Call<FeedItemsPayload>

    @PUT("items/read/multiple")
    fun putRead(@Body args: PutReadArgs): Call<ResponseBody>

    @PUT("items/unread/multiple")
    fun putUnread(@Body args: PutReadArgs): Call<ResponseBody>

    @PUT("items/star/multiple")
    fun putStarred(@Body args: PutStarredArgs): Call<ResponseBody>

    @PUT("items/unstar/multiple")
    fun putUnstarred(@Body args: PutStarredArgs): Call<ResponseBody>
}