package com.twilighty.yuyu.core.network.di

import javax.inject.Qualifier

/** Server-rewriting client without auth: refresh calls and station clients derive from it. */
@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class BareClient

/** Main client: base URL rewrite + bearer + refresh authenticator. Used by Retrofit and Coil. */
@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class AuthedClient

/** [AuthedClient] without a read timeout, for Server-Sent Events. */
@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class StreamingClient
