package com.brkckr.parkv3

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.brkckr.parkv3.domain.ParkRepository
import com.brkckr.parkv3.domain.model.FreshnessPolicy
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class ParkApplication : Application() {

    @Inject
    lateinit var repository: ParkRepository

    override fun onCreate() {
        super.onCreate()
        // Staleness check whenever the app comes to the foreground (docs/adr/0005).
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                owner.lifecycleScope.launch {
                    repository.refreshParksIfOlderThan(FreshnessPolicy.LIST_AUTO_REFRESH_AFTER_MS)
                }
            }
        })
    }
}
