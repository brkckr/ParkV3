package com.brkckr.parkv3.di

import android.content.Context
import com.brkckr.parkv3.BuildConfig
import com.brkckr.parkv3.data.OfflineFirstParkRepository
import com.brkckr.parkv3.data.local.ParkDao
import com.brkckr.parkv3.data.local.ParkDatabase
import com.brkckr.parkv3.data.remote.IsparkApi
import com.brkckr.parkv3.data.remote.IsparkApiFactory
import com.brkckr.parkv3.domain.ParkRepository
import com.brkckr.parkv3.domain.model.Clock
import com.brkckr.parkv3.location.FusedLocationProvider
import com.brkckr.parkv3.location.LocationProvider
import com.brkckr.parkv3.ui.map.MapAvailability
import com.brkckr.parkv3.ui.map.PlayServicesMapAvailability
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.Multibinds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideClock(): Clock = Clock { System.currentTimeMillis() }

    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
}

/** Separate so tests can point the client at a local server. */
@Module
@InstallIn(SingletonComponent::class)
object BaseUrlModule {
    @Provides
    @IsparkBaseUrl
    fun provideBaseUrl(): String = BuildConfig.ISPARK_BASE_URL
}

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(interceptors: Set<@JvmSuppressWildcards Interceptor>): OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .callTimeout(30, TimeUnit.SECONDS)
            .apply { interceptors.forEach(::addInterceptor) }
            .build()

    @Provides
    @Singleton
    fun provideIsparkApi(client: OkHttpClient, @IsparkBaseUrl baseUrl: String): IsparkApi =
        IsparkApiFactory.create(baseUrl, client)
}

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): ParkDatabase = ParkDatabase.create(context)

    @Provides
    fun provideParkDao(database: ParkDatabase): ParkDao = database.parkDao()
}

@Module
@InstallIn(SingletonComponent::class)
interface BindingsModule {

    @Binds
    fun bindParkRepository(impl: OfflineFirstParkRepository): ParkRepository

    @Binds
    fun bindLocationProvider(impl: FusedLocationProvider): LocationProvider

    @Binds
    fun bindMapAvailability(impl: PlayServicesMapAvailability): MapAvailability

    /** Debug builds contribute a logger (src/debug); release builds contribute nothing. */
    @Multibinds
    fun networkInterceptors(): Set<Interceptor>
}
