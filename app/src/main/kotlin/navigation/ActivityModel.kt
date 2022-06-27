package navigation

import android.app.Application
import androidx.lifecycle.ViewModel
import auth.accountSubtitle
import auth.accountTitle
import conf.ConfRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.koin.android.annotation.KoinViewModel

@KoinViewModel
class ActivityModel(
    private val app: Application,
    private val confRepo: ConfRepository,
) : ViewModel() {

    fun accountTitle(): Flow<String> {
        return confRepo.load().map { it.accountTitle(app.resources) }
    }

    fun accountSubtitle(): Flow<String> {
        return confRepo.load().map { it.accountSubtitle() }
    }
}