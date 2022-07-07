package navigation

import android.app.Application
import androidx.lifecycle.ViewModel
import conf.ConfRepo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.koin.android.annotation.KoinViewModel

@KoinViewModel
class ActivityModel(
    private val app: Application,
    private val confRepo: ConfRepo,
) : ViewModel() {
    fun accountTitle(): Flow<String> {
        return confRepo.conf.map { it.accountTitle(app.resources) }
    }

    fun accountSubtitle(): Flow<String> {
        return confRepo.conf.map { it.accountSubtitle() }
    }
}