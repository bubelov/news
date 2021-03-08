package di

import android.content.Context
import android.net.ConnectivityManager
import api.NewsApi
import api.NewsApiSwitcher
import api.NewsApiWrapper
import auth.AuthFragmentModel
import auth.DirectAuthFragmentModel
import common.PreferencesRepository
import common.NewsApiSync
import feeds.FeedsRepository
import exceptions.AppExceptionsFragmentModel
import exceptions.AppExceptionsRepository
import entries.EntriesViewModel
import entries.EntriesRepository
import entry.EntryFragmentModel
import entriesimages.EntriesImagesRepository
import podcasts.PodcastsRepository
import settings.SettingsFragmentModel
import common.ConnectivityProbe
import entries.EntriesSupportingTextRepository
import exception.AppExceptionFragmentModel
import feeds.FeedsViewModel
import org.koin.android.experimental.dsl.viewModel
import org.koin.dsl.module
import org.koin.experimental.builder.single
import search.SearchFragmentModel

val appModule = module {

    single {
        val context = get<Context>()
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        ConnectivityProbe(connectivityManager)
    }

    single<NewsApi> { NewsApiWrapper() }
    single { get<NewsApi>() as NewsApiWrapper }
    single<NewsApiSwitcher>()
    single<NewsApiSync>()

    single<FeedsRepository>()
    single<EntriesRepository>()
    single<EntriesSupportingTextRepository>()
    single<EntriesImagesRepository>()
    single<PodcastsRepository>()
    single<PreferencesRepository>()
    single<AppExceptionsRepository>()

    viewModel<AuthFragmentModel>()
    viewModel<EntriesViewModel>()
    viewModel<EntryFragmentModel>()
    viewModel<SettingsFragmentModel>()
    viewModel<DirectAuthFragmentModel>()
    viewModel<AppExceptionsFragmentModel>()
    viewModel<AppExceptionFragmentModel>()
    viewModel<FeedsViewModel>()
    viewModel<SearchFragmentModel>()
}