package co.appreactor.nextcloud.news

import android.content.Context
import androidx.lifecycle.ViewModel
import com.nextcloud.android.sso.AccountImporter
import com.nextcloud.android.sso.exceptions.SSOException
import com.nextcloud.android.sso.helper.SingleAccountHelper
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class SettingsFragmentModel(
    private val newsItemsRepository: NewsItemsRepository,
    private val newsFeedsRepository: NewsFeedsRepository,
    private val prefs: PreferencesRepository,
    private val context: Context
) : ViewModel() {

    suspend fun getShowReadNews() =
        prefs.get(PreferencesRepository.SHOW_READ_NEWS).map {
            it.isEmpty() || it == "true"
        }

    suspend fun setShowReadNews(show: Boolean) {
        prefs.put(
            PreferencesRepository.SHOW_READ_NEWS,
            if (show) "true" else "false"
        )
    }

    suspend fun getAccountName(): String {
        val serverUrl = prefs.get(PreferencesRepository.SERVER_URL).first()

        return if (serverUrl.isNotBlank()) {
            val username = prefs.get(PreferencesRepository.SERVER_USERNAME).first()
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