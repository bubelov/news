package org.vestifeed.auth

import androidx.lifecycle.ViewModel
import org.vestifeed.conf.ConfRepo
import org.vestifeed.sync.BackgroundSyncScheduler
import java.util.concurrent.TimeUnit

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