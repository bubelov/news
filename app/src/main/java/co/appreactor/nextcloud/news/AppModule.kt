package co.appreactor.nextcloud.news

import androidx.appcompat.app.AlertDialog
import com.google.gson.GsonBuilder
import com.nextcloud.android.sso.api.NextcloudAPI
import com.nextcloud.android.sso.helper.SingleAccountHelper
import com.squareup.sqldelight.android.AndroidSqliteDriver
import com.squareup.sqldelight.db.SqlDriver
import org.koin.android.experimental.dsl.viewModel
import org.koin.dsl.module
import org.koin.experimental.builder.single
import retrofit2.NextcloudRetrofitApiBuilder

val appModule = module {

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

    single {
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

    single<SqlDriver> {
        AndroidSqliteDriver(
            schema = Database.Schema,
            context = get(),
            name = "nextcloud-news-android.db"
        )
    }

    single<Sync>()

    single<NewsItemsRepository>()
    single<NewsFeedsRepository>()

    single { Database(get()) }
    single { get<Database>().newsItemQueries }
    single { get<Database>().newsFeedQueries }

    viewModel<NewsFragmentModel>()
    viewModel<NewsItemFragmentModel>()
    viewModel<StarredNewsFragmentModel>()
}