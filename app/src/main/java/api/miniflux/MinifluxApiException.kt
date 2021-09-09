package api.miniflux

import com.google.gson.Gson
import com.google.gson.JsonObject
import retrofit2.HttpException

class MinifluxApiException(message: String) : Exception(message) {

    companion object {
        fun from(e: HttpException): MinifluxApiException {
            val json = Gson().fromJson(e.response()!!.errorBody()!!.string(), JsonObject::class.java)
            return MinifluxApiException(json["error_message"].asString)
        }
    }
}