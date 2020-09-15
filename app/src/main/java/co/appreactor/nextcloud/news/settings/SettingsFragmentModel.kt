package co.appreactor.nextcloud.news.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import co.appreactor.nextcloud.news.common.*
import co.appreactor.nextcloud.news.feeds.FeedsRepository
import co.appreactor.nextcloud.news.logging.LoggedExceptionsRepository
import co.appreactor.nextcloud.news.feeditems.FeedItemsRepository
import com.nextcloud.android.sso.AccountImporter
import com.nextcloud.android.sso.exceptions.SSOException
import com.nextcloud.android.sso.helper.SingleAccountHelper
import kotlinx.coroutines.flow.first
import timber.log.Timber

class SettingsFragmentModel(
    private val feedsRepository: FeedsRepository,
    private val feedItemsRepository: FeedItemsRepository,
    private val loggedExceptionsRepository: LoggedExceptionsRepository,
    private val prefs: Preferences,
) : ViewModel() {

    suspend fun getShowReadNews() = prefs.showReadNews()

    suspend fun setShowReadNews(show: Boolean) = prefs.setShowReadNews(show)

    suspend fun getShowFeedImages() = prefs.showFeedImages()

    suspend fun setShowFeedImages(show: Boolean) = prefs.setShowFeedImages(show)

    suspend fun getCropFeedImages() = prefs.cropFeedImages()

    suspend fun setCropFeedImages(crop: Boolean) = prefs.setCropFeedImages(crop)

    suspend fun getExceptionsCount() = loggedExceptionsRepository.count()

    suspend fun getAccountName(context: Context): String {
        val serverUrl = prefs.getServerUrl().first()

        return if (serverUrl.isNotBlank()) {
            val username = prefs.getServerUsername().first()
            "$username@${serverUrl.replace("https://", "")}"
        } else {
            try {
                val account = SingleAccountHelper.getCurrentSingleSignOnAccount(context)
                account.name
            } catch (e: SSOException) {
                Timber.e(e)
                "unknown"
            }
        }
    }

    suspend fun clearData(context: Context) {
        AccountImporter.clearAllAuthTokens(context)

        feedsRepository.clear()
        feedItemsRepository.clear()

        prefs.clear()
    }
}