package api

import android.content.Context
import api.nextcloud.DirectNextcloudNewsApiBuilder
import api.nextcloud.NextcloudNewsApi
import api.nextcloud.NextcloudNewsApiAdapter
import api.standalone.StandaloneNewsApi
import common.*
import db.EntryQueries
import db.FeedQueries
import com.google.gson.GsonBuilder
import com.nextcloud.android.sso.api.NextcloudAPI
import com.nextcloud.android.sso.helper.SingleAccountHelper
import common.Preferences.Companion.NEXTCLOUD_SERVER_PASSWORD
import common.Preferences.Companion.NEXTCLOUD_SERVER_URL
import common.Preferences.Companion.NEXTCLOUD_SERVER_USERNAME
import retrofit2.NextcloudRetrofitApiBuilder
import timber.log.Timber

class NewsApiSwitcher(
    private val wrapper: NewsApiWrapper,
    private val feedQueries: FeedQueries,
    private val entryQueries: EntryQueries,
    private val prefs: Preferences,
    private val context: Context,
) {

    fun switch(authType: String) {
        when (authType) {
            Preferences.AUTH_TYPE_NEXTCLOUD_APP -> switchToAppBasedNextcloudApi()
            Preferences.AUTH_TYPE_NEXTCLOUD_DIRECT -> switchToDirectNextcloudApi()
            Preferences.AUTH_TYPE_STANDALONE -> switchToStandaloneApi()
            else -> throw Exception("Unknown auth type: $authType")
        }
    }

    private fun switchToAppBasedNextcloudApi() {
        val account = SingleAccountHelper.getCurrentSingleSignOnAccount(context)

        val callback: NextcloudAPI.ApiConnectedListener = object :
            NextcloudAPI.ApiConnectedListener {
            override fun onConnected() {}

            override fun onError(e: Exception) {
                Timber.e(e)
            }
        }

        val nextcloudApi = NextcloudAPI(
            context,
            account,
            GsonBuilder().create(),
            callback
        )

        val nextcloudNewsApi = NextcloudRetrofitApiBuilder(
            nextcloudApi,
            "/index.php/apps/news/api/v1-2/"
        ).create(NextcloudNewsApi::class.java)

        wrapper.api = NextcloudNewsApiAdapter(nextcloudNewsApi)
    }

    private fun switchToDirectNextcloudApi() {
        val serverUrl = prefs.getStringBlocking(NEXTCLOUD_SERVER_URL)
        val username = prefs.getStringBlocking(NEXTCLOUD_SERVER_USERNAME)
        val password = prefs.getStringBlocking(NEXTCLOUD_SERVER_PASSWORD)

        val api = DirectNextcloudNewsApiBuilder().build(
            serverUrl,
            username,
            password,
        )

        wrapper.api = NextcloudNewsApiAdapter(api)
    }

    private fun switchToStandaloneApi() {
        wrapper.api = StandaloneNewsApi(feedQueries, entryQueries)
    }
}