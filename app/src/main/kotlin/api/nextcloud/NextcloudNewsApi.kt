package api.nextcloud

import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

interface NextcloudNewsApi {

    @POST("feeds")
    suspend fun postFeed(
        @Body args: PostFeedArgs,
    ): FeedsPayload

    @GET("feeds")
    suspend fun getFeeds(): FeedsPayload

    @PUT("feeds/{id}/rename")
    suspend fun putFeedRename(
        @Path("id") id: Long,
        @Body args: PutFeedRenameArgs,
    ): List<Long>

    @DELETE("feeds/{id}")
    suspend fun deleteFeed(
        @Path("id") id: Long,
    ): List<Long>

    @GET("items?type=3")
    suspend fun getAllItems(
        @Query("getRead") getRead: Boolean,
        @Query("batchSize") batchSize: Long,
        @Query("offset") offset: Long,
    ): ItemsPayload

    @GET("items/updated?type=3")
    suspend fun getNewAndUpdatedItems(
        @Query("lastModified") lastModified: Long,
    ): ItemsPayload

    @PUT("items/read/multiple")
    suspend fun putRead(
        @Body args: PutReadArgs,
    )

    @PUT("items/unread/multiple")
    suspend fun putUnread(
        @Body args: PutReadArgs,
    )

    @PUT("items/star/multiple")
    suspend fun putStarred(
        @Body args: PutStarredArgs,
    )

    @PUT("items/unstar/multiple")
    fun putUnstarred(
        @Body args: PutStarredArgs,
    )
}