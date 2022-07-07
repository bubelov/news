package navigation

import android.content.res.Resources
import co.appreactor.news.R
import conf.ConfRepo
import db.Conf

fun Conf.accountTitle(resources: Resources): String {
    return when (backend) {
        ConfRepo.BACKEND_STANDALONE -> resources.getString(R.string.standalone_mode)
        ConfRepo.BACKEND_MINIFLUX -> resources.getString(R.string.miniflux)
        ConfRepo.BACKEND_NEXTCLOUD -> resources.getString(R.string.nextcloud)
        else -> ""
    }
}

fun Conf.accountSubtitle(): String {
    return when (backend) {
        ConfRepo.BACKEND_STANDALONE -> ""
        ConfRepo.BACKEND_MINIFLUX -> {
            val username = minifluxServerUsername
            "$username@${minifluxServerUrl.extractDomain()}"
        }
        ConfRepo.BACKEND_NEXTCLOUD -> {
            val username = nextcloudServerUsername
            "$username@${nextcloudServerUrl.extractDomain()}"
        }
        else -> ""
    }
}

private fun String.extractDomain(): String {
    return replace("https://", "").replace("http://", "")
}