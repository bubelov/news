package co.appreactor.news.di

import co.appreactor.news.api.*
import co.appreactor.news.api.standalone.StandaloneNewsApi
import org.koin.dsl.module

val standaloneNewsApiModule = module {
    single<NewsApi> {
        StandaloneNewsApi(get(), get())
    }
}