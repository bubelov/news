package co.appreactor.nextcloud.news

import android.content.Context
import androidx.lifecycle.ViewModel
import com.nextcloud.android.sso.AccountImporter
import kotlinx.coroutines.flow.map

class SettingsFragmentModel(
    private val newsItemsRepository: NewsItemsRepository,
    private val newsFeedsRepository: NewsFeedsRepository,
    private val preferencesRepository: PreferencesRepository,
    private val context: Context
) : ViewModel() {

    suspend fun getShowReadNews() =
        preferencesRepository.get(PreferencesRepository.SHOW_READ_NEWS).map {
            it.isEmpty() || it == "true"
        }

    suspend fun setShowReadNews(show: Boolean) {
        preferencesRepository.put(
            PreferencesRepository.SHOW_READ_NEWS,
            if (show) "true" else "false"
        )
    }

    suspend fun clearData() {
        AccountImporter.clearAllAuthTokens(context)

        newsItemsRepository.clear()
        newsFeedsRepository.clear()

        preferencesRepository.clear()
    }
}