package co.appreactor.nextcloud.news.di

import co.appreactor.nextcloud.news.Database
import co.appreactor.nextcloud.news.auth.AuthFragmentModel
import co.appreactor.nextcloud.news.auth.DirectAuthFragmentModel
import co.appreactor.nextcloud.news.common.Preferences
import co.appreactor.nextcloud.news.common.Sync
import co.appreactor.nextcloud.news.feeds.NewsFeedsRepository
import co.appreactor.nextcloud.news.logging.ExceptionsFragmentModel
import co.appreactor.nextcloud.news.logging.ExceptionsRepository
import co.appreactor.nextcloud.news.news.NewsFragmentModel
import co.appreactor.nextcloud.news.news.NewsItemsRepository
import co.appreactor.nextcloud.news.newsitem.NewsItemFragmentModel
import co.appreactor.nextcloud.news.opengraph.OpenGraphImagesSync
import co.appreactor.nextcloud.news.podcasts.PodcastsSync
import co.appreactor.nextcloud.news.settings.SettingsFragmentModel
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
    single<ExceptionsRepository>()
    single<Preferences>()

    single { Database(get()) }
    single { get<Database>().newsItemQueries }
    single { get<Database>().newsFeedQueries }
    single { get<Database>().preferenceQueries }
    single { get<Database>().loggedExceptionQueries }

    viewModel<AuthFragmentModel>()
    viewModel<NewsFragmentModel>()
    viewModel<NewsItemFragmentModel>()
    viewModel<StarredNewsFragmentModel>()
    viewModel<SettingsFragmentModel>()
    viewModel<DirectAuthFragmentModel>()
    viewModel<ExceptionsFragmentModel>()
}