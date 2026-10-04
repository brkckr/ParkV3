package com.brkckr.parkv3.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import okhttp3.Interceptor
import okhttp3.logging.HttpLoggingInterceptor

/** Body logging for debug builds only; the logging library is not on the release classpath. */
@Module
@InstallIn(SingletonComponent::class)
object DebugNetworkModule {
    @Provides
    @IntoSet
    fun provideLoggingInterceptor(): Interceptor =
        HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BODY }
}
