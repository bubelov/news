package api

import api.miniflux.MinifluxApiAdapter
import api.miniflux.MinifluxApiBuilder
import api.nextcloud.NextcloudNewsApiAdapter
import api.nextcloud.NextcloudNewsApiBuilder
import api.standalone.StandaloneNewsApi
import common.ConfRepository
import db.Database
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.koin.core.annotation.Single

@Single
class NewsApiSwitcher(
    private val db: Database,
    private val wrapper: NewsApiWrapper,
    private val confRepo: ConfRepository,
) {

    fun switch(backend: String) {
        when (backend) {
            ConfRepository.BACKEND_STANDALONE -> switchToStandaloneApi()
            ConfRepository.BACKEND_MINIFLUX -> switchToMinifluxApi()
            ConfRepository.BACKEND_NEXTCLOUD -> switchToNextcloudApi()
            else -> throw Exception("Unknown backend: $backend")
        }
    }

    private fun switchToNextcloudApi(): Unit = runBlocking {
        val conf = confRepo.load().first()

        wrapper.api = NextcloudNewsApiAdapter(
            NextcloudNewsApiBuilder().build(
                url = conf.nextcloudServerUrl,
                username = conf.nextcloudServerUsername,
                password = conf.nextcloudServerPassword,
                trustSelfSignedCerts = conf.nextcloudServerTrustSelfSignedCerts,
            )
        )
    }

    private fun switchToMinifluxApi(): Unit = runBlocking {
        val conf = confRepo.load().first()

        wrapper.api = MinifluxApiAdapter(
            MinifluxApiBuilder().build(
                url = conf.minifluxServerUrl,
                username = conf.minifluxServerUsername,
                password = conf.minifluxServerPassword,
                trustSelfSignedCerts = conf.minifluxServerTrustSelfSignedCerts,
            )
        )
    }

    private fun switchToStandaloneApi() {
        wrapper.api = StandaloneNewsApi(db)
    }
}