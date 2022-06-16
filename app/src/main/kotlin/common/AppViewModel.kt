package common

import android.content.Context
import androidx.lifecycle.ViewModel
import auth.accountSubtitle
import auth.accountTitle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.koin.android.annotation.KoinViewModel

@KoinViewModel
class AppViewModel(
    private val confRepo: ConfRepository,
    context: Context,
) : ViewModel() {

    private val resources = context.resources

    fun accountTitle(): Flow<String> {
        return confRepo.load().map { it.accountTitle(resources) }
    }

    fun accountSubtitle(): Flow<String> {
        return confRepo.load().map { it.accountSubtitle() }
    }
}