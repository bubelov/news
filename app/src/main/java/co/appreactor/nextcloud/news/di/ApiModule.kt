package co.appreactor.nextcloud.news.di

import co.appreactor.nextcloud.news.api.DirectApiBuilder
import co.appreactor.nextcloud.news.api.NewsApi
import co.appreactor.nextcloud.news.common.Preferences
import com.google.gson.GsonBuilder
import com.nextcloud.android.sso.api.NextcloudAPI
import com.nextcloud.android.sso.helper.SingleAccountHelper
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.koin.dsl.module
import retrofit2.NextcloudRetrofitApiBuilder
import timber.log.Timber

val apiModule = module {

    single<NewsApi> {
        val prefs = get<Preferences>()
        val serverUrl = runBlocking { prefs.getString(Preferences.SERVER_URL).first() }

        if (serverUrl.isNotBlank()) {
            val username = runBlocking { prefs.getString(Preferences.SERVER_USERNAME).first() }
            val password = runBlocking { prefs.getString(Preferences.SERVER_PASSWORD).first() }

            DirectApiBuilder().build(
                serverUrl,
                username,
                password
            )
        } else {
            val nextcloudApi = NextcloudAPI(
                get(),
                get(),
                GsonBuilder().create(),
                get()
            )

            NextcloudRetrofitApiBuilder(
                nextcloudApi,
                "/index.php/apps/news/api/v1-2/"
            ).create(NewsApi::class.java)
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