package api.nextcloud

import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

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

    @GET("items?type=3")
    fun getAllItems(
        @Query("getRead") getRead: Boolean,
        @Query("batchSize") batchSize: Long,
        @Query("offset") offset: Long,
    ): Call<ItemsPayload>

    @GET("items/updated?type=3")
    fun getNewAndUpdatedItems(
        @Query("lastModified") lastModified: Long
    ): Call<ItemsPayload>

    @PUT("items/read/multiple")
    fun putRead(@Body args: PutReadArgs): Call<Void>

    @PUT("items/unread/multiple")
    fun putUnread(@Body args: PutReadArgs): Call<Void>

    @PUT("items/star/multiple")
    fun putStarred(@Body args: PutStarredArgs): Call<Void>

    @PUT("items/unstar/multiple")
    fun putUnstarred(@Body args: PutStarredArgs): Call<Void>
}