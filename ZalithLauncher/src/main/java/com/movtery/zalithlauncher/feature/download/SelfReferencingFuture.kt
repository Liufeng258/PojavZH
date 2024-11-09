package com.movtery.zalithlauncher.feature.download

import com.movtery.zalithlauncher.feature.log.Logging.i
import okhttp3.internal.notify
import okhttp3.internal.wait
import java.util.concurrent.ExecutorService
import java.util.concurrent.Future

class SelfReferencingFuture(private val mFutureInterface: FutureInterface) {
    private val mFutureLock = Any()
    private var mMyFuture: Future<*>? = null

    @JvmName(" Any.notify")
    fun startOnExecutor(executorService: ExecutorService): Future<*> {
        val future = executorService.submit { this.run() }
        synchronized(mFutureLock) {
            mMyFuture = future
            mFutureLock.notify()
        }
        return future
    }

     @JvmName(" Any.wait")
    private fun run() {
        try {
            synchronized(mFutureLock) {
                if (mMyFuture == null) mFutureLock.wait()
            }
            mFutureInterface.run(mMyFuture!!)
        } catch (e: InterruptedException) {
            i("SelfReferencingFuture", "Interrupted while acquiring own Future")
        }
    }

    interface FutureInterface {
        fun run(myFuture: Future<*>)
    }
}
