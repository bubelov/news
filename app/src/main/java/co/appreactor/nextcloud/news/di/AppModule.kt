package co.appreactor.nextcloud.news.di

import co.appreactor.nextcloud.news.Database
import co.appreactor.nextcloud.news.auth.AuthFragmentModel
import co.appreactor.nextcloud.news.auth.DirectAuthFragmentModel
import co.appreactor.nextcloud.news.common.Preferences
import co.appreactor.nextcloud.news.common.NewsApiSync
import co.appreactor.nextcloud.news.feeds.FeedsRepository
import co.appreactor.nextcloud.news.logging.LoggedExceptionsFragmentModel
import co.appreactor.nextcloud.news.logging.LoggedExceptionsRepository
import co.appreactor.nextcloud.news.feeditems.FeedItemsFragmentModel
import co.appreactor.nextcloud.news.feeditems.FeedItemsRepository
import co.appreactor.nextcloud.news.feeditem.FeedItemFragmentModel
import co.appreactor.nextcloud.news.opengraph.OpenGraphImagesRepository
import co.appreactor.nextcloud.news.podcasts.PodcastDownloadsRepository
import co.appreactor.nextcloud.news.settings.SettingsFragmentModel
import co.appreactor.nextcloud.news.bookmarks.BookmarksFragmentModel
import co.appreactor.nextcloud.news.feeds.FeedsFragmentModel
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

    single<NewsApiSync>()

    single<FeedItemsRepository>()
    single<FeedsRepository>()
    single<OpenGraphImagesRepository>()
    single<PodcastDownloadsRepository>()
    single<Preferences>()
    single<LoggedExceptionsRepository>()

    single { Database(get()) }
    single { get<Database>().feedQueries }
    single { get<Database>().feedItemQueries }
    single { get<Database>().openGraphImageQueries }
    single { get<Database>().podcastDownloadQueries }
    single { get<Database>().preferenceQueries }
    single { get<Database>().loggedExceptionQueries }

    viewModel<AuthFragmentModel>()
    viewModel<FeedItemsFragmentModel>()
    viewModel<FeedItemFragmentModel>()
    viewModel<BookmarksFragmentModel>()
    viewModel<SettingsFragmentModel>()
    viewModel<DirectAuthFragmentModel>()
    viewModel<LoggedExceptionsFragmentModel>()
    viewModel<FeedsFragmentModel>()
}