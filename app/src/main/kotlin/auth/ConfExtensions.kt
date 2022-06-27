package auth

import android.content.res.Resources
import co.appreactor.news.R
import conf.ConfRepository
import db.Conf

fun Conf.accountTitle(resources: Resources): String {
    return when (backend) {
        ConfRepository.BACKEND_STANDALONE -> resources.getString(R.string.standalone_mode)
        ConfRepository.BACKEND_MINIFLUX -> resources.getString(R.string.miniflux)
        ConfRepository.BACKEND_NEXTCLOUD -> resources.getString(R.string.nextcloud)
        else -> ""
    }
}

fun Conf.accountSubtitle(): String {
    return when (backend) {
        ConfRepository.BACKEND_STANDALONE -> ""
        ConfRepository.BACKEND_MINIFLUX -> {
            val username = minifluxServerUsername
            "$username@${minifluxServerUrl.extractDomain()}"
        }
        ConfRepository.BACKEND_NEXTCLOUD -> {
            val username = nextcloudServerUsername
            "$username@${nextcloudServerUrl.extractDomain()}"
        }
        else -> ""
    }
}

private fun String.extractDomain(): String {
    return replace("https://", "").replace("http://", "")
}