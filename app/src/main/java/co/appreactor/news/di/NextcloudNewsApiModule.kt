package co.appreactor.news.di

import co.appreactor.news.api.DirectNextcloudNewsApiBuilder
import co.appreactor.news.api.NewsApi
import co.appreactor.news.api.NextcloudNewsApi
import co.appreactor.news.api.NextcloudNewsApiAdapter
import co.appreactor.news.common.*
import com.google.gson.GsonBuilder
import com.nextcloud.android.sso.api.NextcloudAPI
import com.nextcloud.android.sso.helper.SingleAccountHelper
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.koin.dsl.module
import retrofit2.NextcloudRetrofitApiBuilder
import timber.log.Timber

val nextcloudNewsApiModule = module {

    single<NewsApi> {
        NextcloudNewsApiAdapter(get())
    }

    single<NextcloudNewsApi> {
        val prefs = get<Preferences>()

        val authType = runBlocking {
            prefs.getAuthType().first()
        }

        return@single when (authType) {
            Preferences.AUTH_TYPE_NEXTCLOUD_APP -> {
                val nextcloudApi = NextcloudAPI(
                    get(),
                    get(),
                    GsonBuilder().create(),
                    get()
                )

                NextcloudRetrofitApiBuilder(
                    nextcloudApi,
                    "/index.php/apps/news/api/v1-2/"
                ).create(NextcloudNewsApi::class.java)
            }

            Preferences.AUTH_TYPE_NEXTCLOUD_DIRECT -> {
                val serverUrl = runBlocking { prefs.getNextcloudServerUrl().first() }
                val username = runBlocking { prefs.getNextcloudServerUsername().first() }
                val password = runBlocking { prefs.getNextcloudServerPassword().first() }

                DirectNextcloudNewsApiBuilder().build(
                    serverUrl,
                    username,
                    password
                )
            }

            else -> throw IllegalStateException()
        }
    }

    single {
        SingleAccountHelper.getCurrentSingleSignOnAccount(get())
    }

    single {
        val callback: NextcloudAPI.ApiConnectedListener = object :
            NextcloudAPI.ApiConnectedListener {
            override fun onConnected() {}

            override fun onError(e: Exception) {
                Timber.e(e)
            }
        }

        callback
    }
}