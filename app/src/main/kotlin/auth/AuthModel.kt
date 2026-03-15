package auth

import androidx.lifecycle.ViewModel
import conf.ConfRepo
import org.koin.android.annotation.KoinViewModel
import sync.BackgroundSyncScheduler
import java.util.concurrent.TimeUnit

@KoinViewModel
class AuthModel(
    private val confRepo: ConfRepo,
    private val syncScheduler: BackgroundSyncScheduler,
) : ViewModel() {

    fun setStandaloneBackend() {
        confRepo.update {
            it.copy(
                backend = ConfRepo.BACKEND_STANDALONE,
                syncOnStartup = false,
                backgroundSyncIntervalMillis = TimeUnit.HOURS.toMillis(12),
            )
        }

        syncScheduler.schedule()
    }
}