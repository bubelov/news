package api.miniflux

import retrofit2.http.GET
import retrofit2.http.Path

interface HackernewsApi {
    @GET("item/{id}.json")
    suspend fun getItem(
        @Path("id") entryId: Long,
    ): HnEntryJson
}