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
    fun getUnreadItems(): Call<ItemsPayload>

    @GET("items?type=2&getRead=true&batchSize=-1")
    fun getStarredItems(): Call<ItemsPayload>

    @GET("items/updated?type=3")
    fun getNewAndUpdatedItems(
        @Query("lastModified") lastModified: Long
    ): Call<ItemsPayload>

    @PUT("items/read/multiple")
    fun markAsRead(@Body args: PutReadArgs): Call<ResponseBody>

    @PUT("items/unread/multiple")
    fun markAsUnread(@Body args: PutReadArgs): Call<ResponseBody>

    @PUT("items/star/multiple")
    fun markAsStarred(@Body args: PutStarredArgs): Call<ResponseBody>

    @PUT("items/unstar/multiple")
    fun markAsUnstarred(@Body args: PutStarredArgs): Call<ResponseBody>
}