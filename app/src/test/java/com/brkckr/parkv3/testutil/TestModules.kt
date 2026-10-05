package com.brkckr.parkv3.testutil

import android.content.Context
import androidx.room.Room
import com.brkckr.parkv3.data.local.ParkDao
import com.brkckr.parkv3.data.local.ParkDatabase
import com.brkckr.parkv3.di.BaseUrlModule
import com.brkckr.parkv3.di.DatabaseModule
import com.brkckr.parkv3.di.IsparkBaseUrl
import com.brkckr.parkv3.di.PlatformModule
import com.brkckr.parkv3.location.LocationProvider
import com.brkckr.parkv3.ui.map.MapAvailability
import com.brkckr.parkv3.ui.map.MapStatus
import dagger.Module
import dagger.Provides
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import javax.inject.Singleton

@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [BaseUrlModule::class])
object TestBaseUrlModule {
    @Provides
    @IsparkBaseUrl
    fun provideBaseUrl(): String = TestServer.baseUrl
}

/** A fresh in-memory database per test (each Hilt test gets its own component). */
@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [DatabaseModule::class])
object TestDatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): ParkDatabase =
        Room.inMemoryDatabaseBuilder(context, ParkDatabase::class.java).build()

    @Provides
    fun provideParkDao(database: ParkDatabase): ParkDao = database.parkDao()
}

/** No location permission and no map key: the flows must work without either. */
@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [PlatformModule::class])
object TestPlatformModule {
    @Provides
    @Singleton
    fun provideLocationProvider(): LocationProvider = FakeLocationProvider()

    @Provides
    fun provideMapAvailability(): MapAvailability = MapAvailability { MapStatus.NO_API_KEY }
}
