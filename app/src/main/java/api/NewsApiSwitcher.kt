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
import db.LogEntry
import kotlinx.coroutines.runBlocking
import logentries.LogEntriesRepository
import org.joda.time.DateTime
import retrofit2.NextcloudRetrofitApiBuilder
import timber.log.Timber
import java.util.*

class NewsApiSwitcher(
    private val wrapper: NewsApiWrapper,
    private val feedQueries: FeedQueries,
    private val entryQueries: EntryQueries,
    private val prefs: PreferencesRepository,
    private val log: LogEntriesRepository,
    private val context: Context,
) {

    fun switch(authType: String) {
        when (authType) {
            PreferencesRepository.AUTH_TYPE_NEXTCLOUD_APP -> switchToAppBasedNextcloudApi()
            PreferencesRepository.AUTH_TYPE_NEXTCLOUD_DIRECT -> switchToDirectNextcloudApi()
            PreferencesRepository.AUTH_TYPE_STANDALONE -> switchToStandaloneApi()
            else -> throw Exception("Unknown auth type: $authType")
        }
    }

    private fun switchToAppBasedNextcloudApi() {
        val account = SingleAccountHelper.getCurrentSingleSignOnAccount(context)

        val callback: NextcloudAPI.ApiConnectedListener =
            object : NextcloudAPI.ApiConnectedListener {
                override fun onConnected() {
                    log("Connected to Nextcloud app")
                }

                override fun onError(e: Exception) {
                    log("Failed to connect to Nextcloud app")
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

    private fun switchToDirectNextcloudApi(): Unit = runBlocking {
        prefs.get().apply {
            wrapper.api = NextcloudNewsApiAdapter(
                DirectNextcloudNewsApiBuilder().build(
                    url = nextcloudServerUrl,
                    username = nextcloudServerUsername,
                    password = nextcloudServerPassword,
                    trustSelfSignedCerts = prefs.get().nextcloudServerTrustSelfSignedCerts,
                )
            )
        }
    }

    private fun switchToStandaloneApi() {
        wrapper.api = StandaloneNewsApi(feedQueries, entryQueries, log)
    }

    private fun log(message: String): Unit = runBlocking {
        log.insert(
            LogEntry(
                id = UUID.randomUUID().toString(),
                date = DateTime.now().toString(),
                tag = NewsApiSwitcher::class.java.simpleName,
                message = message,
            )
        )
    }
}