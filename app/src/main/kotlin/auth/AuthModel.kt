package auth

import androidx.lifecycle.ViewModel
import conf.ConfRepository
import kotlinx.coroutines.flow.first
import org.koin.android.annotation.KoinViewModel
import sync.BackgroundSyncScheduler
import java.util.concurrent.TimeUnit

@KoinViewModel
class AuthModel(
    private val confRepo: ConfRepository,
    private val syncScheduler: BackgroundSyncScheduler,
) : ViewModel() {

    suspend fun hasBackend() = confRepo.load().first().backend.isNotBlank()

    suspend fun setStandaloneBackend() {
        confRepo.save {
            it.copy(
                backend = ConfRepository.BACKEND_STANDALONE,
                syncOnStartup = false,
                backgroundSyncIntervalMillis = TimeUnit.HOURS.toMillis(12),
            )
        }

        syncScheduler.schedule(override = true)
    }
}