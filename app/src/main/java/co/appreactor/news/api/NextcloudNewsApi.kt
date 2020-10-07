package co.appreactor.news.api

import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.http.*

interface NextcloudNewsApi {

    @POST("feeds")
    fun postFeed(@Body args: PostFeedArgs): Call<FeedsPayload>

    @GET("feeds")
    fun getFeeds(): Call<FeedsPayload>

    @GET("feeds")
    fun getFeedsRaw(): Call<ResponseBody>

    @PUT("feeds/{id}/rename")
    fun putFeedRename(
        @Path("id") id: Long,
        @Body args: PutFeedRenameArgs
    ): Call<List<Long>>

    @DELETE("feeds/{id}")
    fun deleteFeed(@Path("id") id: Long): Call<List<Long>>

    @GET("items?type=3&getRead=false&batchSize=-1")
    fun getUnreadItems(): Call<ItemsPayload>

    @GET("items?type=2&getRead=true&batchSize=-1")
    fun getStarredItems(): Call<ItemsPayload>

    @GET("items/updated?type=3")
    fun getNewAndUpdatedItems(
        @Query("lastModified") lastModified: Long
    ): Call<ItemsPayload>

    @PUT("items/read/multiple")
    fun putRead(@Body args: PutReadArgs): Call<ResponseBody>

    @PUT("items/unread/multiple")
    fun putUnread(@Body args: PutReadArgs): Call<ResponseBody>

    @PUT("items/star/multiple")
    fun putStarred(@Body args: PutStarredArgs): Call<ResponseBody>

    @PUT("items/unstar/multiple")
    fun putUnstarred(@Body args: PutStarredArgs): Call<ResponseBody>
}