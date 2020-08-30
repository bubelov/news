package co.appreactor.nextcloud.news

import androidx.appcompat.app.AlertDialog
import com.google.gson.GsonBuilder
import com.nextcloud.android.sso.api.NextcloudAPI
import com.nextcloud.android.sso.helper.SingleAccountHelper
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.koin.dsl.module
import retrofit2.NextcloudRetrofitApiBuilder

val apiModule = module {

    single<NewsApi> {
        val prefs = get<PreferencesRepository>()
        val serverUrl = runBlocking { prefs.get(PreferencesRepository.SERVER_URL).first() }

        if (serverUrl.isNotBlank()) {
            val username = runBlocking { prefs.get(PreferencesRepository.SERVER_USERNAME).first() }
            val password = runBlocking { prefs.get(PreferencesRepository.SERVER_PASSWORD).first() }

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
                // TODO
                AlertDialog.Builder(get())
                    .setMessage("callback.onError: ${e.message}")
                    .show()
            }
        }

        callback
    }
}