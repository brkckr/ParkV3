package com.brkckr.parkv3.di

import javax.inject.Qualifier

/** Application-lifetime scope for work that must outlive a single screen (shared refreshes). */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IsparkBaseUrl
