package co.appreactor.nextcloud.news

import okhttp3.ResponseBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PUT

interface NewsApi {

    @GET("feeds")
    fun getFeeds(): FeedsPayload

    @GET("items?type=3&getRead=false&batchSize=-1")
    fun getUnreadItems(): ItemsPayload

    @GET("items?type=2&getRead=true&batchSize=-1")
    fun getStarredItems(): ItemsPayload

    @PUT("items/read/multiple")
    fun markAsRead(@Body args: NewsItemsIdsArgs): ResponseBody

    @PUT("items/unread/multiple")
    fun markAsUnread(@Body args: NewsItemsIdsArgs): ResponseBody
}