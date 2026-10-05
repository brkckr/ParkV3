package com.brkckr.parkv3

import android.app.Application
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.brkckr.parkv3.data.sync.ListRefreshTriggers
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class ParkApplication : Application() {

    @Inject
    lateinit var listRefreshTriggers: ListRefreshTriggers

    override fun onCreate() {
        super.onCreate()
        // Automatic refreshes only while the app is visible (docs/adr/0005). A refresh that is
        // already running when the app goes to the background still completes and is saved.
        val process = ProcessLifecycleOwner.get()
        process.lifecycleScope.launch {
            process.repeatOnLifecycle(Lifecycle.State.STARTED) { listRefreshTriggers.runWhileForeground() }
        }
    }
}
