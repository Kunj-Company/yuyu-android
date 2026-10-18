package com.twilighty.yuyu.core.network.di

import android.content.Context
import coil3.ImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import com.twilighty.yuyu.core.network.BuildConfig
import com.twilighty.yuyu.core.network.NetworkJson
import com.twilighty.yuyu.core.network.api.YuyuMobileApi
import com.twilighty.yuyu.core.network.api.YuyuWebApi
import com.twilighty.yuyu.core.network.auth.TokenAuthenticator
import com.twilighty.yuyu.core.network.interceptor.AuthInterceptor
import com.twilighty.yuyu.core.network.interceptor.BaseUrlInterceptor
import com.twilighty.yuyu.core.network.interceptor.RedactingLogger
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import okhttp3.Cache
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun providesJson(): Json = NetworkJson

    /** HTTP cache: honours the server's `Cache-Control` (API responses are `no-store`; images cache). */
    @Provides
    @Singleton
    fun providesCache(@ApplicationContext context: Context): Cache =
        Cache(File(context.cacheDir, "http"), 20L * 1024 * 1024)

    @Provides
    @Singleton
    @BareClient
    fun providesBareClient(baseUrlInterceptor: BaseUrlInterceptor): OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(baseUrlInterceptor)
            .apply { if (BuildConfig.DEBUG) addInterceptor(RedactingLogger.interceptor()) }
            .build()

    @Provides
    @Singleton
    @AuthedClient
    fun providesAuthedClient(
        @BareClient bare: OkHttpClient,
        cache: Cache,
        authInterceptor: AuthInterceptor,
        authenticator: TokenAuthenticator,
    ): OkHttpClient {
        // Rebuild interceptor order: base URL → auth → (debug) logging.
        val builder = bare.newBuilder().cache(cache).authenticator(authenticator)
        builder.interceptors().clear()
        bare.interceptors.filterIsInstance<BaseUrlInterceptor>().forEach { builder.addInterceptor(it) }
        builder.addInterceptor(authInterceptor)
        if (BuildConfig.DEBUG) builder.addInterceptor(RedactingLogger.interceptor())
        return builder.build()
    }

    @Provides
    @Singleton
    @StreamingClient
    fun providesStreamingClient(@AuthedClient client: OkHttpClient): OkHttpClient =
        client.newBuilder().readTimeout(0, TimeUnit.MILLISECONDS).cache(null).build()

    @Provides
    @Singleton
    fun providesRetrofit(@AuthedClient client: OkHttpClient, json: Json): Retrofit =
        Retrofit.Builder()
            .baseUrl(BaseUrlInterceptor.PLACEHOLDER_BASE_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

    @Provides
    @Singleton
    fun providesMobileApi(retrofit: Retrofit): YuyuMobileApi = retrofit.create(YuyuMobileApi::class.java)

    @Provides
    @Singleton
    fun providesWebApi(retrofit: Retrofit): YuyuWebApi = retrofit.create(YuyuWebApi::class.java)

    /** Coil loads images through the authed client so private `/api/uploads/...` work. */
    @Provides
    @Singleton
    fun providesImageLoader(@ApplicationContext context: Context, @AuthedClient client: OkHttpClient): ImageLoader =
        ImageLoader.Builder(context)
            .components { add(OkHttpNetworkFetcherFactory(callFactory = { client })) }
            .crossfade(true)
            .build()
}
