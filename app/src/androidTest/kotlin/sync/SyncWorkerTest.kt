package sync

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.ListenableWorker
import androidx.work.testing.TestWorkerBuilder
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SyncWorkerTest {

    private lateinit var targetContext: Context

    @Before
    fun setup() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        targetContext = instrumentation.targetContext.applicationContext
    }

    @Test
    fun retryIfInitialSyncIncomplete() {
        val workerBuilder = TestWorkerBuilder.from(targetContext, SyncWorker::class.java)
        val worker = workerBuilder.build()
        val result = worker.doWork()
        Assert.assertTrue(result is ListenableWorker.Result.Retry)
    }
}