package api

import android.content.Context
import api.miniflux.MinifluxApiAdapter
import api.miniflux.MinifluxApiBuilder
import api.nextcloud.DirectNextcloudNewsApiBuilder
import api.nextcloud.NextcloudNewsApi
import api.nextcloud.NextcloudNewsApiAdapter
import api.standalone.StandaloneNewsApi
import db.EntryQueries
import db.FeedQueries
import com.google.gson.GsonBuilder
import com.nextcloud.android.sso.api.NextcloudAPI
import com.nextcloud.android.sso.helper.SingleAccountHelper
import common.ConfRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import retrofit2.NextcloudRetrofitApiBuilder
import timber.log.Timber

class NewsApiSwitcher(
    private val wrapper: NewsApiWrapper,
    private val feedQueries: FeedQueries,
    private val entryQueries: EntryQueries,
    private val confRepo: ConfRepository,
    private val context: Context,
) {

    fun switch(authType: String) {
        when (authType) {
            ConfRepository.AUTH_TYPE_NEXTCLOUD_APP -> switchToAppBasedNextcloudApi()
            ConfRepository.AUTH_TYPE_NEXTCLOUD_DIRECT -> switchToDirectNextcloudApi()
            ConfRepository.AUTH_TYPE_MINIFLUX -> switchToMinifluxApi()
            ConfRepository.AUTH_TYPE_STANDALONE -> switchToStandaloneApi()
            else -> throw Exception("Unknown auth type: $authType")
        }
    }

    private fun switchToAppBasedNextcloudApi() {
        val account = SingleAccountHelper.getCurrentSingleSignOnAccount(context)

        val callback: NextcloudAPI.ApiConnectedListener =
            object : NextcloudAPI.ApiConnectedListener {
                override fun onConnected() {
                    Timber.d("Connected to Nextcloud app")
                }

                override fun onError(e: Exception) {
                    Timber.e(e, "Failed to connect to Nextcloud app")
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
        val conf = confRepo.select().first()

        wrapper.api = NextcloudNewsApiAdapter(
            DirectNextcloudNewsApiBuilder().build(
                url = conf.nextcloudServerUrl,
                username = conf.nextcloudServerUsername,
                password = conf.nextcloudServerPassword,
                trustSelfSignedCerts = conf.nextcloudServerTrustSelfSignedCerts,
            )
        )
    }

    private fun switchToMinifluxApi(): Unit = runBlocking {
        val conf = confRepo.select().first()

        wrapper.api = MinifluxApiAdapter(
            MinifluxApiBuilder().build(
                url = conf.minifluxServerUrl,
                username = conf.minifluxServerUsername,
                password = conf.minifluxServerPassword,
                trustSelfSignedCerts = conf.minifluxServerTrustSelfSignedCerts,
            )
        )
    }

    private fun switchToStandaloneApi() {
        wrapper.api = StandaloneNewsApi(feedQueries, entryQueries)
    }
}