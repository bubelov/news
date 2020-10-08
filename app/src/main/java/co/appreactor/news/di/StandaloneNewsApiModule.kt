package co.appreactor.news.di

import co.appreactor.news.api.*
import org.koin.dsl.module

val standaloneNewsApiModule = module {
    single<NewsApi> {
        StandaloneNewsApi()
    }
}