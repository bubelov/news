package api.miniflux

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

interface MinifluxApi {

    @GET("categories")
    suspend fun getCategories(): List<CategoryJson>

    @POST("feeds")
    suspend fun postFeed(@Body args: PostFeedArgs): PostFeedResponse

    @GET("feeds")
    suspend fun getFeeds(): List<FeedJson>

    @GET("feeds/{id}")
    suspend fun getFeed(@Path("id") id: Long): FeedJson

    @PUT("feeds/{id}")
    suspend fun putFeed(
        @Path("id") id: Long,
        @Body args: PutFeedArgs,
    ): Response<Unit>

    @DELETE("feeds/{id}")
    suspend fun deleteFeed(@Path("id") id: Long): Response<Unit>

    @GET("entries?order=id")
    suspend fun getEntriesAfterEntry(
        @Query("after_entry_id") afterEntryId: Long = 0,
        @Query("limit") limit: Long = 0,
    ): EntriesPayload

    @GET("entries?order=id&direction=desc")
    suspend fun getEntriesBeforeEntry(
        @Query("status") status: String = "",
        @Query("before_entry_id") entryId: Long = 0,
        @Query("limit") limit: Long = 0,
    ): EntriesPayload

    @PUT("entries")
    suspend fun putEntryStatus(@Body args: PutStatusArgs): Response<Unit>

    @PUT("entries/{id}/bookmark")
    suspend fun putEntryBookmark(@Path("id") id: Long): Response<Unit>
}