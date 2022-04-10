package sync

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.ListenableWorker
import androidx.work.testing.TestWorkerBuilder
import common.App
import common.ConfRepository
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.android.ext.android.get
import kotlin.test.assertIs

@RunWith(AndroidJUnit4::class)
class SyncWorkerTest {

    private lateinit var app: App

    @Before
    fun setup() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        app = instrumentation.targetContext.applicationContext as App
    }

    @Test
    fun retryIfInitialSyncIncomplete() {
        val confRepo = app.get<ConfRepository>()
        runBlocking { confRepo.save(confRepo.get().copy(initialSyncCompleted = false)) }
        val workerBuilder = TestWorkerBuilder.from(app, SyncWorker::class.java)
        val worker = workerBuilder.build()
        val result = worker.doWork()
        assertIs<ListenableWorker.Result.Retry>(result)
    }
}