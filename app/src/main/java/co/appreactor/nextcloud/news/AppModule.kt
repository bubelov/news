package co.appreactor.nextcloud.news

import co.appreactor.nextcloud.news.news.NewsFragmentModel
import co.appreactor.nextcloud.news.news.NewsItemsRepository
import co.appreactor.nextcloud.news.opengraph.OpenGraphImagesSync
import co.appreactor.nextcloud.news.podcasts.PodcastsSync
import co.appreactor.nextcloud.news.starred.StarredNewsFragmentModel
import com.squareup.sqldelight.android.AndroidSqliteDriver
import com.squareup.sqldelight.db.SqlDriver
import org.koin.android.experimental.dsl.viewModel
import org.koin.dsl.module
import org.koin.experimental.builder.single

val appModule = module {

    single<SqlDriver> {
        AndroidSqliteDriver(
            schema = Database.Schema,
            context = get(),
            name = "nextcloud-news-android.db"
        )
    }

    single<Sync>()
    single<OpenGraphImagesSync>()
    single<PodcastsSync>()

    single<NewsItemsRepository>()
    single<NewsFeedsRepository>()
    single<Preferences>()

    single { Database(get()) }
    single { get<Database>().newsItemQueries }
    single { get<Database>().newsFeedQueries }
    single { get<Database>().preferenceQueries }

    viewModel<AuthFragmentModel>()
    viewModel<NewsFragmentModel>()
    viewModel<NewsItemFragmentModel>()
    viewModel<StarredNewsFragmentModel>()
    viewModel<SettingsFragmentModel>()
    viewModel<DirectAuthFragmentModel>()
}