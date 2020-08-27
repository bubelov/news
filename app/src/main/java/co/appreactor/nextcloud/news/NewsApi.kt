package co.appreactor.nextcloud.news

import okhttp3.ResponseBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PUT
import retrofit2.http.Query

interface NewsApi {

    @GET("feeds")
    fun getFeeds(): FeedsPayload

    @GET("items?type=3&getRead=false&batchSize=-1")
    fun getUnreadItems(): ItemsPayload

    @GET("items?type=2&getRead=true&batchSize=-1")
    fun getStarredItems(): ItemsPayload

    @GET("items/updated?type=3")
    fun getNewAndUpdatedItems(
        @Query("lastModified") lastModified: Long
    ): ItemsPayload

    @PUT("items/read/multiple")
    fun markAsRead(@Body args: PutReadArgs): ResponseBody

    @PUT("items/unread/multiple")
    fun markAsUnread(@Body args: PutReadArgs): ResponseBody

    @PUT("items/star/multiple")
    fun markAsStarred(@Body args: PutStarredArgs): ResponseBody

    @PUT("items/unstar/multiple")
    fun markAsUnstarred(@Body args: PutStarredArgs): ResponseBody
}