package co.appreactor.nextcloud.news.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import co.appreactor.nextcloud.news.feeds.FeedsRepository
import co.appreactor.nextcloud.news.common.Preferences
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

    suspend fun getShowReadNews() = prefs.getBoolean(
        key = Preferences.SHOW_READ_NEWS,
        default = true
    )

    suspend fun setShowReadNews(show: Boolean) {
        prefs.putBoolean(
            key = Preferences.SHOW_READ_NEWS,
            value = show
        )
    }

    suspend fun getShowFeedImages() = prefs.getBoolean(
        key = Preferences.SHOW_FEED_IMAGES,
        default = false
    )

    suspend fun setShowFeedImages(crop: Boolean) {
        prefs.putBoolean(
            key = Preferences.SHOW_FEED_IMAGES,
            value = crop
        )
    }

    suspend fun getCropFeedImages() = prefs.getBoolean(
        key = Preferences.CROP_FEED_IMAGES,
        default = true
    )

    suspend fun setCropFeedImages(crop: Boolean) {
        prefs.putBoolean(
            key = Preferences.CROP_FEED_IMAGES,
            value = crop
        )
    }

    suspend fun getExceptionsCount() = loggedExceptionsRepository.count()

    suspend fun getAccountName(context: Context): String {
        val serverUrl = prefs.getString(Preferences.SERVER_URL).first()

        return if (serverUrl.isNotBlank()) {
            val username = prefs.getString(Preferences.SERVER_USERNAME).first()
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