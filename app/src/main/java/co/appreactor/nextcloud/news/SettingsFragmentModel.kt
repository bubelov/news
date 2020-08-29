package co.appreactor.nextcloud.news

import android.content.Context
import androidx.lifecycle.ViewModel
import com.nextcloud.android.sso.AccountImporter

class SettingsFragmentModel(
    private val newsItemsRepository: NewsItemsRepository,
    private val newsFeedsRepository: NewsFeedsRepository,
    private val context: Context
) : ViewModel() {

    suspend fun clearData() {
        AccountImporter.clearAllAuthTokens(context)

        newsItemsRepository.clear()
        newsFeedsRepository.clear()
    }
}