package co.appreactor.nextcloud.news.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import co.appreactor.nextcloud.news.feeds.NewsFeedsRepository
import co.appreactor.nextcloud.news.common.Preferences
import co.appreactor.nextcloud.news.news.NewsItemsRepository
import com.nextcloud.android.sso.AccountImporter
import com.nextcloud.android.sso.exceptions.SSOException
import com.nextcloud.android.sso.helper.SingleAccountHelper
import kotlinx.coroutines.flow.first

class SettingsFragmentModel(
    private val newsItemsRepository: NewsItemsRepository,
    private val newsFeedsRepository: NewsFeedsRepository,
    private val prefs: Preferences,
    private val context: Context
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

    suspend fun getAccountName(): String {
        val serverUrl = prefs.getString(Preferences.SERVER_URL).first()

        return if (serverUrl.isNotBlank()) {
            val username = prefs.getString(Preferences.SERVER_USERNAME).first()
            "$username@${serverUrl.replace("https://", "")}"
        } else {
            try {
                val account = SingleAccountHelper.getCurrentSingleSignOnAccount(context)
                account.name
            } catch (e: SSOException) {
                "unknown"
            }
        }
    }

    suspend fun clearData() {
        AccountImporter.clearAllAuthTokens(context)

        newsItemsRepository.clear()
        newsFeedsRepository.clear()

        prefs.clear()
    }
}