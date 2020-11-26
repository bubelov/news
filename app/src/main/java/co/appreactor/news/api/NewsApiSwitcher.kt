package co.appreactor.news.api

import android.content.Context
import co.appreactor.news.api.nextcloud.DirectNextcloudNewsApiBuilder
import co.appreactor.news.api.nextcloud.NextcloudNewsApi
import co.appreactor.news.api.nextcloud.NextcloudNewsApiAdapter
import co.appreactor.news.api.standalone.StandaloneNewsApi
import co.appreactor.news.common.*
import co.appreactor.news.db.EntryQueries
import co.appreactor.news.db.FeedQueries
import com.google.gson.GsonBuilder
import com.nextcloud.android.sso.api.NextcloudAPI
import com.nextcloud.android.sso.helper.SingleAccountHelper
import kotlinx.coroutines.flow.first
import retrofit2.NextcloudRetrofitApiBuilder
import timber.log.Timber

class NewsApiSwitcher(
    private val wrapper: NewsApiWrapper,
    private val feedQueries: FeedQueries,
    private val entryQueries: EntryQueries,
    private val prefs: Preferences,
    private val context: Context,
) {

    suspend fun switch(authType: String) {
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

    private suspend fun switchToDirectNextcloudApi() {
        val serverUrl = prefs.getNextcloudServerUrl().first()
        val username = prefs.getNextcloudServerUsername().first()
        val password = prefs.getNextcloudServerPassword().first()

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